package com.financetracker.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.FinanceApplication
import com.financetracker.app.data.Money
import com.financetracker.app.data.MonthPeriod
import com.financetracker.app.data.account.AccountRepository
import com.financetracker.app.data.account.AccountWithBalance
import com.financetracker.app.data.budget.BudgetProgress
import com.financetracker.app.data.budget.BudgetRepository
import com.financetracker.app.data.category.CategoryRepository
import com.financetracker.app.data.forecast.Forecast
import com.financetracker.app.data.forecast.ForecastEngine
import com.financetracker.app.data.recurring.RecurringRepository
import com.financetracker.app.data.recurring.RecurringRuleDetail
import com.financetracker.app.data.settings.SettingsRepository
import com.financetracker.app.data.txn.TransactionDetail
import com.financetracker.app.data.txn.TransactionRepository
import com.financetracker.app.data.txn.TxnType
import com.financetracker.app.ui.common.FinanceViewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val baseCurrency: String = SettingsRepository.DEFAULT_BASE_CURRENCY,
    val hideBalances: Boolean = false,
    val period: MonthPeriod = MonthPeriod.current(1),
    val netWorthMinor: Long = 0,
    val accounts: List<AccountWithBalance> = emptyList(),
    val incomeMinor: Long = 0,
    val expenseMinor: Long = 0,
    val budgets: List<BudgetProgress> = emptyList(),
    val recent: List<TransactionDetail> = emptyList(),
    val dueRules: List<RecurringRuleDetail> = emptyList(),
    val monthlyCommitmentMinor: Long = 0,
    val forecast: Forecast? = null
) {
    val netMinor: Long get() = incomeMinor - expenseMinor
}

private data class Prefs(val baseCurrency: String, val monthStartDay: Int, val hideBalances: Boolean)

/** Everything the dashboard shows, assembled from the repositories in one reactive pipeline. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val settings: SettingsRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val transactions: TransactionRepository,
    private val budgets: BudgetRepository,
    private val recurring: RecurringRepository
) : ViewModel() {

    /** Bumped after posting a recurring rule so the due list re-evaluates against a fresh clock. */
    private val refreshTick = MutableStateFlow(System.currentTimeMillis())

    private val prefs = combine(
        settings.baseCurrency,
        settings.monthStartDay,
        settings.hideBalances
    ) { base, day, hide -> Prefs(base, day, hide) }

    val uiState: StateFlow<HomeUiState> = combine(prefs, refreshTick) { p, tick -> p to tick }
        .flatMapLatest { (prefs, tick) ->
            val period = MonthPeriod.current(prefs.monthStartDay)

            val ledger = combine(
                accounts.accountsWithBalances,
                accounts.rates,
                transactions.amountRows(TxnType.EXPENSE, period.startMillis, period.endMillisExclusive),
                transactions.amountRows(TxnType.INCOME, period.startMillis, period.endMillisExclusive),
                transactions.recent(RECENT_LIMIT)
            ) { accountList, rateList, expenseRows, incomeRows, recent ->
                val rateMap = rateList.associate { it.code to it.rateToBase }
                LedgerSnapshot(
                    accounts = accountList,
                    netWorthMinor = AccountRepository.netWorthMinorBase(accountList, rateMap, prefs.baseCurrency),
                    spendableMinor = AccountRepository.spendableMinorBase(accountList, rateMap, prefs.baseCurrency),
                    expenseMinor = TransactionRepository.sumToBase(expenseRows, prefs.baseCurrency),
                    incomeMinor = TransactionRepository.sumToBase(incomeRows, prefs.baseCurrency),
                    recent = recent
                )
            }

            // The burn-rate window rides alongside the ledger so the final combine keeps its five
            // slots for the things the dashboard already needed.
            val ledgerWithBurn = combine(
                ledger,
                transactions.nonRecurringSpend(
                    tick - ForecastEngine.BURN_WINDOW_DAYS * 86_400_000L,
                    tick
                )
            ) { snapshot, spendRows -> snapshot to spendRows }

            combine(
                ledgerWithBurn,
                budgets.activeBudgets,
                categories.allCategories,
                recurring.dueForConfirmation(tick),
                recurring.activeRules
            ) { (snapshot, spendRows), budgetList, categoryList, due, activeRules ->
                val categoriesById = categoryList.associateBy { it.id }
                val commitments = activeRules
                    .filter { it.type == TxnType.EXPENSE }
                    .sumOf { rule ->
                        val monthly = RecurringRepository.monthlyEquivalentMinor(
                            rule.amountMinor, rule.frequency, rule.interval
                        )
                        Money.toBaseMinor(
                            monthly,
                            rule.accountCurrency,
                            accounts.rateToBase(rule.accountCurrency, prefs.baseCurrency),
                            prefs.baseCurrency
                        )
                    }

                // The ledger can be younger than the burn window, so the observed span is capped
                // at its actual age - otherwise a fortnight of spending is divided across sixty
                // days and the projection looks reassuringly, wrongly, low.
                val earliest = transactions.earliestDateMillis()
                val observedDays = if (earliest == null) 0 else minOf(
                    ForecastEngine.BURN_WINDOW_DAYS,
                    ForecastEngine.daysBetween(earliest, tick) + 1
                )

                // Rates are resolved here rather than inside the conversion lambda: looking them
                // up is a suspending database call, and the engine is deliberately pure.
                val ruleCurrencies = activeRules.associate { it.id to it.accountCurrency }
                val ruleRates = ruleCurrencies.values.distinct()
                    .associateWith { accounts.rateToBase(it, prefs.baseCurrency) }

                val forecast = ForecastEngine.build(
                    period = period,
                    nowMillis = tick,
                    spendableMinorBase = snapshot.spendableMinor,
                    rules = activeRules.map { it.toRule() },
                    ruleNames = activeRules.associate { it.id to it.name },
                    ruleToBase = { amountMinor, ruleId ->
                        val code = ruleCurrencies[ruleId] ?: prefs.baseCurrency
                        Money.toBaseMinor(
                            amountMinor,
                            code,
                            ruleRates[code] ?: 1.0,
                            prefs.baseCurrency
                        )
                    },
                    nonRecurringSpendMinorBase = TransactionRepository.sumToBase(
                        spendRows, prefs.baseCurrency
                    ),
                    observedDays = observedDays
                )

                HomeUiState(
                    loading = false,
                    baseCurrency = prefs.baseCurrency,
                    hideBalances = prefs.hideBalances,
                    period = period,
                    netWorthMinor = snapshot.netWorthMinor,
                    accounts = snapshot.accounts.filter { !it.isArchived },
                    incomeMinor = snapshot.incomeMinor,
                    expenseMinor = snapshot.expenseMinor,
                    budgets = budgets.progressFor(budgetList, period, categoriesById, prefs.baseCurrency),
                    recent = snapshot.recent,
                    dueRules = due,
                    monthlyCommitmentMinor = commitments,
                    forecast = forecast
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun confirmRule(ruleId: Long) = viewModelScope.launch {
        recurring.confirm(ruleId)
        refreshTick.value = System.currentTimeMillis()
    }

    fun skipRule(ruleId: Long) = viewModelScope.launch {
        recurring.skip(ruleId)
        refreshTick.value = System.currentTimeMillis()
    }

    fun setHideBalances(hide: Boolean) = viewModelScope.launch { settings.setHideBalances(hide) }

    private data class LedgerSnapshot(
        val accounts: List<AccountWithBalance>,
        val netWorthMinor: Long,
        val spendableMinor: Long,
        val expenseMinor: Long,
        val incomeMinor: Long,
        val recent: List<TransactionDetail>
    )

    companion object {
        private const val RECENT_LIMIT = 8

        fun factory(app: FinanceApplication) = FinanceViewModelFactory(app) {
            HomeViewModel(
                settings = it.settingsRepository,
                accounts = it.accountRepository,
                categories = it.categoryRepository,
                transactions = it.transactionRepository,
                budgets = it.budgetRepository,
                recurring = it.recurringRepository
            )
        }
    }
}
