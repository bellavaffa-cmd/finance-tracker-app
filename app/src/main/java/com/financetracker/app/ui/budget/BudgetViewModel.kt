package com.financetracker.app.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.FinanceApplication
import com.financetracker.app.data.Money
import com.financetracker.app.data.MonthPeriod
import com.financetracker.app.data.budget.Budget
import com.financetracker.app.data.budget.BudgetProgress
import com.financetracker.app.data.budget.BudgetRepository
import com.financetracker.app.data.category.Category
import com.financetracker.app.data.category.CategoryKind
import com.financetracker.app.data.category.CategoryRepository
import com.financetracker.app.data.settings.SettingsRepository
import com.financetracker.app.ui.common.FinanceViewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BudgetUiState(
    val period: MonthPeriod = MonthPeriod.current(1),
    val baseCurrency: String = SettingsRepository.DEFAULT_BASE_CURRENCY,
    val progress: List<BudgetProgress> = emptyList(),
    /** Expense categories with no budget yet, offered in the "add budget" picker. */
    val budgetableCategories: List<Category> = emptyList(),
    val hasOverallBudget: Boolean = false,
    val loading: Boolean = true
) {
    val totalBudgetedMinor: Long get() = progress.filter { it.budget.categoryId != null }
        .sumOf { it.allowanceMinorBase }
    val totalSpentMinor: Long get() = progress.filter { it.budget.categoryId != null }
        .sumOf { it.spentMinorBase }
    val overBudgetCount: Int get() = progress.count { it.isOverBudget }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModel(
    private val settings: SettingsRepository,
    private val categories: CategoryRepository,
    private val budgets: BudgetRepository
) : ViewModel() {

    private val monthOffset = MutableStateFlow(0L)

    val uiState: StateFlow<BudgetUiState> = combine(
        settings.baseCurrency,
        settings.monthStartDay,
        monthOffset
    ) { base, startDay, offset -> Triple(base, startDay, offset) }
        .flatMapLatest { (baseCurrency, startDay, offset) ->
            val period = MonthPeriod.current(startDay).plusMonths(offset)
            combine(
                budgets.allBudgets,
                categories.allCategories
            ) { budgetList, categoryList ->
                val byId = categoryList.associateBy { it.id }
                val budgeted = budgetList.mapNotNull { it.categoryId }.toSet()
                BudgetUiState(
                    period = period,
                    baseCurrency = baseCurrency,
                    progress = budgets.progressFor(budgetList, period, byId, baseCurrency),
                    budgetableCategories = categoryList.filter {
                        it.kind == CategoryKind.EXPENSE && !it.isArchived && it.id !in budgeted
                    },
                    hasOverallBudget = budgetList.any { it.categoryId == null && it.isActive },
                    loading = false
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetUiState())

    fun previousMonth() { monthOffset.value -= 1 }
    fun nextMonth() { monthOffset.value += 1 }

    /**
     * Creates or replaces a budget. It starts from the month currently on screen, so setting a
     * budget while looking at March does not retroactively judge January.
     */
    fun saveBudget(categoryId: Long?, amountInput: String, rollover: Boolean) = viewModelScope.launch {
        val state = uiState.value
        val amount = Money.parseToMinor(amountInput, state.baseCurrency) ?: return@launch
        if (amount <= 0) return@launch

        val existing = budgets.forCategory(categoryId)
        if (existing == null) {
            budgets.upsert(
                Budget(
                    categoryId = categoryId,
                    amountMinorBase = amount,
                    rollover = rollover,
                    startMonthMillis = state.period.startMillis,
                    isActive = true
                )
            )
        } else {
            budgets.update(
                existing.copy(amountMinorBase = amount, rollover = rollover, isActive = true)
            )
        }
    }

    fun deleteBudget(budget: Budget) = viewModelScope.launch { budgets.delete(budget) }

    companion object {
        fun factory(app: FinanceApplication) = FinanceViewModelFactory(app) {
            BudgetViewModel(
                settings = it.settingsRepository,
                categories = it.categoryRepository,
                budgets = it.budgetRepository
            )
        }
    }
}
