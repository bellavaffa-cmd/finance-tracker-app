package com.financetracker.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.FinanceApplication
import com.financetracker.app.data.Money
import com.financetracker.app.data.MonthPeriod
import com.financetracker.app.data.account.AccountRepository
import com.financetracker.app.data.account.AccountWithBalance
import com.financetracker.app.data.category.Category
import com.financetracker.app.data.category.CategoryRepository
import com.financetracker.app.data.settings.SettingsRepository
import com.financetracker.app.data.txn.TransactionDetail
import com.financetracker.app.data.txn.TransactionRepository
import com.financetracker.app.data.txn.TxnFilter
import com.financetracker.app.data.txn.TxnType
import com.financetracker.app.ui.common.FinanceViewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A day's worth of transactions, with the day's net movement already totalled. */
data class DayGroup(
    val dateMillis: Long,
    val items: List<TransactionDetail>,
    val netMinorBase: Long
)

data class TransactionsUiState(
    val period: MonthPeriod = MonthPeriod.current(1),
    val baseCurrency: String = SettingsRepository.DEFAULT_BASE_CURRENCY,
    val days: List<DayGroup> = emptyList(),
    val accounts: List<AccountWithBalance> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val type: TxnType? = null,
    val query: String = "",
    val minInput: String = "",
    val maxInput: String = "",
    val incomeMinor: Long = 0,
    val expenseMinor: Long = 0,
    val loading: Boolean = true
) {
    val isFiltered: Boolean
        get() = accountId != null || categoryId != null || type != null ||
            query.isNotBlank() || minInput.isNotBlank() || maxInput.isNotBlank()

    val count: Int get() = days.sumOf { it.items.size }
}

private data class Query(
    val monthOffset: Long = 0,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val type: TxnType? = null,
    val text: String = "",
    val minInput: String = "",
    val maxInput: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModel(
    private val settings: SettingsRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val transactions: TransactionRepository
) : ViewModel() {

    private val query = MutableStateFlow(Query())

    val uiState: StateFlow<TransactionsUiState> = combine(
        settings.baseCurrency,
        settings.monthStartDay,
        query
    ) { base, startDay, q -> Triple(base, startDay, q) }
        .flatMapLatest { (baseCurrency, startDay, q) ->
            val period = MonthPeriod.current(startDay).plusMonths(q.monthOffset)
            // Amount bounds are typed in the base currency and compared against stored minor units;
            // an unparseable value means "no bound" rather than silently matching nothing.
            val filter = TxnFilter(
                fromMillis = period.startMillis,
                toMillis = period.endMillisExclusive,
                accountId = q.accountId,
                categoryId = q.categoryId,
                type = q.type,
                query = q.text,
                minMinor = Money.parseToMinor(q.minInput, baseCurrency),
                maxMinor = Money.parseToMinor(q.maxInput, baseCurrency)
            )

            combine(
                transactions.filtered(filter),
                accounts.activeAccounts,
                categories.allCategories
            ) { items, accountList, categoryList ->
                TransactionsUiState(
                    period = period,
                    baseCurrency = baseCurrency,
                    days = groupByDay(items, baseCurrency),
                    accounts = accountList,
                    categories = categoryList,
                    accountId = q.accountId,
                    categoryId = q.categoryId,
                    type = q.type,
                    query = q.text,
                    minInput = q.minInput,
                    maxInput = q.maxInput,
                    incomeMinor = totalFor(items, TxnType.INCOME, baseCurrency),
                    expenseMinor = totalFor(items, TxnType.EXPENSE, baseCurrency),
                    loading = false
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    fun previousMonth() = query.update { it.copy(monthOffset = it.monthOffset - 1) }
    fun nextMonth() = query.update { it.copy(monthOffset = it.monthOffset + 1) }

    fun setAccount(id: Long?) = query.update { it.copy(accountId = id) }
    fun setCategory(id: Long?) = query.update { it.copy(categoryId = id) }
    fun setType(type: TxnType?) = query.update { it.copy(type = type) }
    fun setQuery(text: String) = query.update { it.copy(text = text) }
    fun setMin(input: String) = query.update { it.copy(minInput = input) }
    fun setMax(input: String) = query.update { it.copy(maxInput = input) }

    fun clearFilters() = query.update { Query(monthOffset = it.monthOffset) }

    fun delete(id: Long) = viewModelScope.launch { transactions.softDelete(id) }

    fun restore(id: Long) = viewModelScope.launch { transactions.restore(id) }

    companion object {
        /** Transfers are excluded from the income/expense strips - they are not either one. */
        private fun totalFor(items: List<TransactionDetail>, type: TxnType, baseCurrency: String): Long =
            items.filter { it.type == type }.sumOf {
                Money.toBaseMinor(it.amountMinor, it.accountCurrency, it.fxRateToBase, baseCurrency)
            }

        private fun groupByDay(items: List<TransactionDetail>, baseCurrency: String): List<DayGroup> =
            items.groupBy { detail ->
                java.time.Instant.ofEpochMilli(detail.dateMillis)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
            }.map { (date, dayItems) ->
                val net = dayItems.sumOf { detail ->
                    val base = Money.toBaseMinor(
                        detail.amountMinor, detail.accountCurrency, detail.fxRateToBase, baseCurrency
                    )
                    when (detail.type) {
                        TxnType.INCOME -> base
                        TxnType.EXPENSE -> -base
                        TxnType.TRANSFER -> 0L
                    }
                }
                DayGroup(
                    dateMillis = date.atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant().toEpochMilli(),
                    items = dayItems,
                    netMinorBase = net
                )
            }.sortedByDescending { it.dateMillis }

        fun factory(app: FinanceApplication) = FinanceViewModelFactory(app) {
            TransactionsViewModel(
                settings = it.settingsRepository,
                accounts = it.accountRepository,
                categories = it.categoryRepository,
                transactions = it.transactionRepository
            )
        }
    }
}
