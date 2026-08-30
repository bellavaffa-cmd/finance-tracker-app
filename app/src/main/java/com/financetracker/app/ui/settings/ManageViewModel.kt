package com.financetracker.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.FinanceApplication
import com.financetracker.app.data.Money
import com.financetracker.app.data.account.Account
import com.financetracker.app.data.account.AccountRepository
import com.financetracker.app.data.account.AccountType
import com.financetracker.app.data.account.AccountWithBalance
import com.financetracker.app.data.category.Category
import com.financetracker.app.data.category.CategoryGroup
import com.financetracker.app.data.category.CategoryKind
import com.financetracker.app.data.category.CategoryRepository
import com.financetracker.app.data.recurring.Frequency
import com.financetracker.app.data.recurring.RecurringRepository
import com.financetracker.app.data.recurring.RecurringRule
import com.financetracker.app.data.recurring.RecurringRuleDetail
import com.financetracker.app.data.settings.CurrencyRate
import com.financetracker.app.data.settings.SettingsRepository
import com.financetracker.app.data.txn.TxnType
import com.financetracker.app.ui.common.FinanceViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ManageUiState(
    val baseCurrency: String = SettingsRepository.DEFAULT_BASE_CURRENCY,
    val monthStartDay: Int = 1,
    val accounts: List<AccountWithBalance> = emptyList(),
    val rates: List<CurrencyRate> = emptyList(),
    val expenseGroups: List<CategoryGroup> = emptyList(),
    val incomeGroups: List<CategoryGroup> = emptyList(),
    val rules: List<RecurringRuleDetail> = emptyList(),
    val loading: Boolean = true
) {
    /** Currencies actually in use, which is what the rates screen needs to list. */
    val currenciesInUse: List<String>
        get() = accounts.map { it.currencyCode }.distinct().sorted()

    val monthlyCommitmentMinor: Long
        get() = rules.filter { it.isActive && it.type == TxnType.EXPENSE }
            .sumOf { RecurringRepository.monthlyEquivalentMinor(it.amountMinor, it.frequency, it.interval) }
}

/** Backs the accounts, categories, recurring-rules and settings screens - they share repositories. */
class ManageViewModel(
    private val settings: SettingsRepository,
    private val accountRepo: AccountRepository,
    private val categoryRepo: CategoryRepository,
    private val recurringRepo: RecurringRepository
) : ViewModel() {

    private val categoryState = combine(
        categoryRepo.expenseGroups,
        categoryRepo.incomeGroups
    ) { expense, income -> expense to income }

    private val prefs = combine(
        settings.baseCurrency,
        settings.monthStartDay
    ) { base, day -> base to day }

    val uiState: StateFlow<ManageUiState> = combine(
        prefs,
        accountRepo.accountsWithBalances,
        accountRepo.rates,
        categoryState,
        recurringRepo.allRules
    ) { (baseCurrency, startDay), accounts, rates, categories, rules ->
        ManageUiState(
            baseCurrency = baseCurrency,
            monthStartDay = startDay,
            accounts = accounts,
            rates = rates,
            expenseGroups = categories.first,
            incomeGroups = categories.second,
            rules = rules,
            loading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManageUiState())

    /** Rate rows are created lazily for whatever currencies the accounts actually use. */
    val ratesNeeded: StateFlow<List<String>> = uiState
        .map { it.currenciesInUse.filter { code -> code != it.baseCurrency } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- Accounts -----------------------------------------------------------------------------

    fun saveAccount(
        existing: Account?,
        name: String,
        type: AccountType,
        currencyCode: String,
        openingBalanceInput: String,
        colorArgb: Int,
        includeInNetWorth: Boolean
    ) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        val opening = Money.parseToMinor(openingBalanceInput.ifBlank { "0" }, currencyCode) ?: 0L
        if (existing == null) {
            accountRepo.insert(
                Account(
                    name = name.trim(),
                    type = type,
                    currencyCode = currencyCode,
                    openingBalanceMinor = opening,
                    colorArgb = colorArgb,
                    includeInNetWorth = includeInNetWorth,
                    sortOrder = uiState.value.accounts.size
                )
            )
        } else {
            accountRepo.update(
                existing.copy(
                    name = name.trim(),
                    type = type,
                    currencyCode = currencyCode,
                    openingBalanceMinor = opening,
                    colorArgb = colorArgb,
                    includeInNetWorth = includeInNetWorth
                )
            )
        }
        accountRepo.ensureRatesFor(listOf(currencyCode), settings.currentBaseCurrency())
    }

    fun setAccountArchived(id: Long, archived: Boolean) = viewModelScope.launch {
        val account = accountRepo.byId(id) ?: return@launch
        accountRepo.update(account.copy(isArchived = archived))
    }

    /**
     * Only an account with no transactions can be deleted outright; anything else is archived, so
     * a year of history cannot vanish because a card was closed.
     */
    fun deleteAccount(id: Long, onBlocked: (Int) -> Unit) = viewModelScope.launch {
        val used = accountRepo.transactionCount(id)
        if (used > 0) {
            onBlocked(used)
            return@launch
        }
        accountRepo.byId(id)?.let { accountRepo.delete(it) }
    }

    suspend fun accountById(id: Long): Account? = accountRepo.byId(id)

    // --- Categories ---------------------------------------------------------------------------

    fun saveCategory(
        existing: Category?,
        name: String,
        kind: CategoryKind,
        parentId: Long?,
        colorArgb: Int
    ) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        if (existing == null) {
            categoryRepo.insert(
                Category(
                    name = name.trim(),
                    kind = kind,
                    parentId = parentId,
                    colorArgb = colorArgb,
                    sortOrder = Int.MAX_VALUE / 2
                )
            )
        } else {
            categoryRepo.update(
                existing.copy(name = name.trim(), parentId = parentId, colorArgb = colorArgb)
            )
        }
    }

    fun setCategoryArchived(category: Category, archived: Boolean) = viewModelScope.launch {
        categoryRepo.update(category.copy(isArchived = archived))
    }

    fun deleteCategory(category: Category) = viewModelScope.launch { categoryRepo.delete(category) }

    // --- Recurring ----------------------------------------------------------------------------

    fun saveRule(
        existingId: Long?,
        name: String,
        type: TxnType,
        accountId: Long,
        toAccountId: Long?,
        categoryId: Long?,
        amountInput: String,
        currencyCode: String,
        payee: String,
        note: String,
        frequency: Frequency,
        interval: Int,
        firstDueMillis: Long,
        endDateMillis: Long?,
        autoPost: Boolean
    ) = viewModelScope.launch {
        val amount = Money.parseToMinor(amountInput, currencyCode) ?: return@launch
        if (amount <= 0 || name.isBlank()) return@launch

        val anchorDay = if (frequency == Frequency.MONTHLY) {
            java.time.Instant.ofEpochMilli(firstDueMillis)
                .atZone(java.time.ZoneId.systemDefault()).dayOfMonth
        } else null

        val rule = RecurringRule(
            id = existingId ?: 0,
            name = name.trim(),
            type = type,
            accountId = accountId,
            toAccountId = if (type == TxnType.TRANSFER) toAccountId else null,
            categoryId = if (type == TxnType.TRANSFER) null else categoryId,
            amountMinor = amount,
            payee = payee.trim(),
            note = note.trim(),
            frequency = frequency,
            interval = interval.coerceAtLeast(1),
            anchorDayOfMonth = anchorDay,
            nextDueMillis = firstDueMillis,
            endDateMillis = endDateMillis,
            autoPost = autoPost,
            isActive = true
        )
        if (existingId == null) recurringRepo.insert(rule) else recurringRepo.update(rule)
    }

    fun setRuleActive(detail: RecurringRuleDetail, active: Boolean) = viewModelScope.launch {
        val rule = recurringRepo.byId(detail.id) ?: return@launch
        recurringRepo.update(rule.copy(isActive = active))
    }

    fun deleteRule(id: Long) = viewModelScope.launch { recurringRepo.deleteById(id) }

    fun confirmRule(id: Long) = viewModelScope.launch { recurringRepo.confirm(id) }

    fun skipRule(id: Long) = viewModelScope.launch { recurringRepo.skip(id) }

    // --- Settings -----------------------------------------------------------------------------

    /**
     * Changing the base currency does not rewrite the rates already frozen on past transactions -
     * those stay as recorded. It only changes what new entries convert into and how net worth is
     * displayed, which is the honest behaviour: history happened at the rates it happened at.
     */
    fun setBaseCurrency(code: String) = viewModelScope.launch {
        settings.setBaseCurrency(code)
        accountRepo.ensureRatesFor(uiState.value.currenciesInUse, code)
        accountRepo.deleteRate(code)
    }

    fun setMonthStartDay(day: Int) = viewModelScope.launch { settings.setMonthStartDay(day) }

    fun setRate(code: String, input: String) = viewModelScope.launch {
        val rate = input.trim().replace(',', '.').toDoubleOrNull() ?: return@launch
        if (rate <= 0.0) return@launch
        accountRepo.setRate(code, rate)
    }

    companion object {
        fun factory(app: FinanceApplication) = FinanceViewModelFactory(app) {
            ManageViewModel(
                settings = it.settingsRepository,
                accountRepo = it.accountRepository,
                categoryRepo = it.categoryRepository,
                recurringRepo = it.recurringRepository
            )
        }
    }
}
