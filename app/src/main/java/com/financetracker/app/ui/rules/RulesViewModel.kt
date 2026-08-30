package com.financetracker.app.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.FinanceApplication
import com.financetracker.app.data.account.AccountRepository
import com.financetracker.app.data.account.AccountWithBalance
import com.financetracker.app.data.category.Category
import com.financetracker.app.data.category.CategoryRepository
import com.financetracker.app.data.rules.BackfillResult
import com.financetracker.app.data.rules.MatchType
import com.financetracker.app.data.rules.PayeeRule
import com.financetracker.app.data.rules.PayeeRuleRepository
import com.financetracker.app.data.rules.RuleSuggestion
import com.financetracker.app.ui.common.FinanceViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RulesUiState(
    val rules: List<PayeeRule> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accounts: List<AccountWithBalance> = emptyList(),
    val suggestions: List<RuleSuggestion> = emptyList(),
    val backfill: BackfillResult? = null,
    val busy: Boolean = false,
    val loading: Boolean = true
) {
    val activeCount: Int get() = rules.count { it.isActive }

    fun categoryName(id: Long?): String? = id?.let { categories.firstOrNull { c -> c.id == it }?.name }

    fun accountName(id: Long?): String? = id?.let { accounts.firstOrNull { a -> a.id == it }?.name }
}

class RulesViewModel(
    private val rules: PayeeRuleRepository,
    private val categories: CategoryRepository,
    private val accounts: AccountRepository
) : ViewModel() {

    private val suggestions = MutableStateFlow<List<RuleSuggestion>>(emptyList())
    private val backfill = MutableStateFlow<BackfillResult?>(null)
    private val busy = MutableStateFlow(false)

    val uiState: StateFlow<RulesUiState> = combine(
        rules.allRules,
        categories.allCategories,
        accounts.activeAccounts,
        suggestions,
        combine(backfill, busy) { result, working -> result to working }
    ) { ruleList, categoryList, accountList, suggestionList, (backfillResult, working) ->
        RulesUiState(
            rules = ruleList,
            categories = categoryList.filter { !it.isArchived },
            accounts = accountList,
            suggestions = suggestionList,
            backfill = backfillResult,
            busy = working,
            loading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RulesUiState())

    init {
        refreshSuggestions()
    }

    fun refreshSuggestions() = viewModelScope.launch {
        suggestions.value = rules.suggestions()
    }

    fun save(
        existing: PayeeRule?,
        pattern: String,
        matchType: MatchType,
        categoryId: Long?,
        accountId: Long?,
        renameTo: String?
    ) = viewModelScope.launch {
        if (pattern.isBlank()) return@launch
        val rule = (existing ?: PayeeRule(
            pattern = "",
            createdAtMillis = System.currentTimeMillis()
        )).copy(
            pattern = pattern.trim(),
            matchType = matchType,
            categoryId = categoryId,
            accountId = accountId,
            renameTo = renameTo?.trim()?.takeIf { it.isNotEmpty() }
        )
        // A rule that sets nothing would match and then do nothing, quietly shadowing the rules
        // below it. Better to refuse it than to let it silently break the ones underneath.
        if (!rule.isUseful) return@launch
        rules.save(rule)
        refreshSuggestions()
    }

    /** Turns a suggestion into a real rule, matching the payee exactly as it was seen. */
    fun acceptSuggestion(suggestion: RuleSuggestion) = viewModelScope.launch {
        rules.save(
            PayeeRule(
                pattern = suggestion.payee,
                matchType = MatchType.EQUALS,
                categoryId = suggestion.categoryId,
                createdAtMillis = System.currentTimeMillis()
            )
        )
        refreshSuggestions()
    }

    fun dismissSuggestion(suggestion: RuleSuggestion) {
        suggestions.value = suggestions.value.filterNot { it.payee == suggestion.payee }
    }

    fun setActive(rule: PayeeRule, active: Boolean) = viewModelScope.launch {
        rules.setActive(rule, active)
    }

    fun move(rule: PayeeRule, up: Boolean) = viewModelScope.launch { rules.move(rule, up) }

    fun delete(rule: PayeeRule) = viewModelScope.launch {
        rules.delete(rule)
        refreshSuggestions()
    }

    /** Applies the rules to entries already in the ledger that have no category yet. */
    fun runBackfill() = viewModelScope.launch {
        busy.value = true
        backfill.value = rules.backfill()
        busy.value = false
    }

    fun clearBackfill() { backfill.value = null }

    companion object {
        fun factory(app: FinanceApplication) = FinanceViewModelFactory(app) {
            RulesViewModel(
                rules = it.payeeRuleRepository,
                categories = it.categoryRepository,
                accounts = it.accountRepository
            )
        }
    }
}
