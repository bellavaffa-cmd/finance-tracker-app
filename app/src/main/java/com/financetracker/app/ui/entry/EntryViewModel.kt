package com.financetracker.app.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.FinanceApplication
import com.financetracker.app.data.Money
import com.financetracker.app.data.account.AccountRepository
import com.financetracker.app.data.account.AccountWithBalance
import com.financetracker.app.data.category.Category
import com.financetracker.app.data.category.CategoryGroup
import com.financetracker.app.data.category.CategoryKind
import com.financetracker.app.data.category.CategoryRepository
import com.financetracker.app.data.settings.SettingsRepository
import com.financetracker.app.data.txn.Transaction
import com.financetracker.app.data.txn.TransactionRepository
import com.financetracker.app.data.txn.TxnType
import com.financetracker.app.ui.common.FinanceViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EntryUiState(
    val editingId: Long? = null,
    val type: TxnType = TxnType.EXPENSE,
    /** Amount as whole minor units, built up digit by digit on the keypad. */
    val amountMinor: Long = 0,
    /** What lands in the destination account of a cross-currency transfer. */
    val toAmountMinor: Long = 0,
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val dateMillis: Long = System.currentTimeMillis(),
    val payee: String = "",
    val note: String = "",
    val fxRateToBase: Double = 1.0,
    val accounts: List<AccountWithBalance> = emptyList(),
    val expenseGroups: List<CategoryGroup> = emptyList(),
    val incomeGroups: List<CategoryGroup> = emptyList(),
    val categoriesById: Map<Long, Category> = emptyMap(),
    val baseCurrency: String = SettingsRepository.DEFAULT_BASE_CURRENCY,
    val payeeSuggestions: List<String> = emptyList(),
    val error: String? = null,
    val saved: Boolean = false,
    val loading: Boolean = true
) {
    val account: AccountWithBalance? get() = accounts.firstOrNull { it.id == accountId }
    val toAccount: AccountWithBalance? get() = accounts.firstOrNull { it.id == toAccountId }
    val currencyCode: String get() = account?.currencyCode ?: baseCurrency
    val toCurrencyCode: String get() = toAccount?.currencyCode ?: currencyCode

    val groups: List<CategoryGroup>
        get() = if (type == TxnType.INCOME) incomeGroups else expenseGroups

    val category: Category? get() = categoryId?.let { categoriesById[it] }

    /** The FX row only earns its space when the account is not already in the base currency. */
    val needsFxRate: Boolean get() = type != TxnType.TRANSFER && currencyCode != baseCurrency

    /** A same-currency transfer moves an identical amount, so the second field is noise. */
    val needsToAmount: Boolean get() = type == TxnType.TRANSFER && currencyCode != toCurrencyCode

    val isEditing: Boolean get() = editingId != null
}

/**
 * Backs both the add and the edit screen. Entry is the screen this app lives or dies on, so the
 * defaults do as much of the work as possible: last-used account, category recalled from the
 * payee, today's date, and the current FX rate all arrive pre-filled.
 */
class EntryViewModel(
    private val settings: SettingsRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val transactions: TransactionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(EntryUiState())
    val state: StateFlow<EntryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val baseCurrency = settings.currentBaseCurrency()
            val accountList = accounts.activeAccounts.first()
            val expense = categories.expenseGroups.first()
            val income = categories.incomeGroups.first()
            val byId = categories.allCategories.first().associateBy { it.id }

            _state.update { current ->
                val defaultAccount = current.accountId ?: accountList.firstOrNull()?.id
                current.copy(
                    accounts = accountList,
                    expenseGroups = expense,
                    incomeGroups = income,
                    categoriesById = byId,
                    baseCurrency = baseCurrency,
                    accountId = defaultAccount,
                    loading = false
                )
            }
            refreshFxRate()
        }
    }

    /** Loads an existing transaction for editing. Safe to call after [init] has populated lists. */
    fun startEditing(transactionId: Long) = viewModelScope.launch {
        val existing = transactions.byId(transactionId) ?: return@launch
        _state.update {
            it.copy(
                editingId = existing.id,
                type = existing.type,
                amountMinor = existing.amountMinor,
                toAmountMinor = existing.toAmountMinor ?: 0,
                accountId = existing.accountId,
                toAccountId = existing.toAccountId,
                categoryId = existing.categoryId,
                dateMillis = existing.dateMillis,
                payee = existing.payee,
                note = existing.note,
                fxRateToBase = existing.fxRateToBase,
                saved = false,
                error = null
            )
        }
    }

    fun setType(type: TxnType) {
        _state.update { current ->
            // Categories do not carry across a direction change: an "expense: Groceries" that
            // silently became "income: Groceries" would quietly corrupt the reports.
            val keepCategory = when {
                type == TxnType.TRANSFER -> null
                current.type == type -> current.categoryId
                else -> null
            }
            current.copy(type = type, categoryId = keepCategory, error = null)
        }
        recomputeTransferAmount()
    }

    fun appendDigit(digit: Int) {
        _state.update {
            val next = it.amountMinor * 10 + digit
            // Stop at a trillion minor units rather than letting a stuck key overflow the Long.
            if (next > MAX_AMOUNT_MINOR) it else it.copy(amountMinor = next, error = null)
        }
        recomputeTransferAmount()
    }

    fun backspace() {
        _state.update { it.copy(amountMinor = it.amountMinor / 10) }
        recomputeTransferAmount()
    }

    fun clearAmount() {
        _state.update { it.copy(amountMinor = 0) }
        recomputeTransferAmount()
    }

    fun setToAmountMinor(value: Long) = _state.update { it.copy(toAmountMinor = value.coerceAtLeast(0)) }

    fun setAccount(id: Long) {
        _state.update { current ->
            // Picking the same account on both legs of a transfer would net to nothing.
            val to = if (current.toAccountId == id) null else current.toAccountId
            current.copy(accountId = id, toAccountId = to, error = null)
        }
        viewModelScope.launch { refreshFxRate() }
        recomputeTransferAmount()
    }

    fun setToAccount(id: Long) {
        _state.update { it.copy(toAccountId = if (it.accountId == id) null else id, error = null) }
        recomputeTransferAmount()
    }

    fun setCategory(id: Long?) = _state.update { it.copy(categoryId = id, error = null) }

    fun setDate(millis: Long) = _state.update { it.copy(dateMillis = millis) }

    fun setNote(note: String) = _state.update { it.copy(note = note) }

    fun setFxRate(rate: Double) = _state.update { it.copy(fxRateToBase = rate) }

    /**
     * Typing a payee recalls the category it was last filed under, so the second Lidl shop is one
     * tap shorter than the first. It never overwrites a category you picked yourself.
     */
    fun setPayee(payee: String) {
        _state.update { it.copy(payee = payee) }
        viewModelScope.launch {
            val suggestions = transactions.payeeSuggestions(payee)
            _state.update { it.copy(payeeSuggestions = suggestions) }
        }
    }

    fun applyPayee(payee: String) {
        _state.update { it.copy(payee = payee, payeeSuggestions = emptyList()) }
        viewModelScope.launch {
            val remembered = transactions.lastCategoryForPayee(payee) ?: return@launch
            _state.update { current ->
                val kind = current.categoriesById[remembered]?.kind
                val matchesDirection = when (current.type) {
                    TxnType.EXPENSE -> kind == CategoryKind.EXPENSE
                    TxnType.INCOME -> kind == CategoryKind.INCOME
                    TxnType.TRANSFER -> false
                }
                if (current.categoryId == null && matchesDirection) current.copy(categoryId = remembered)
                else current
            }
        }
    }

    private suspend fun refreshFxRate() {
        val current = _state.value
        val code = current.currencyCode
        val rate = accounts.rateToBase(code, current.baseCurrency)
        _state.update { it.copy(fxRateToBase = rate) }
    }

    /** Keeps the destination amount of a cross-currency transfer in step with the source amount. */
    private fun recomputeTransferAmount() = viewModelScope.launch {
        val current = _state.value
        if (current.type != TxnType.TRANSFER || !current.needsToAmount) return@launch
        val fromRate = accounts.rateToBase(current.currencyCode, current.baseCurrency)
        val toRate = accounts.rateToBase(current.toCurrencyCode, current.baseCurrency)
        val converted = Money.convert(
            current.amountMinor, current.currencyCode, fromRate, current.toCurrencyCode, toRate
        )
        _state.update { it.copy(toAmountMinor = converted) }
    }

    fun save() = viewModelScope.launch {
        val current = _state.value
        val error = validate(current)
        if (error != null) {
            _state.update { it.copy(error = error) }
            return@launch
        }

        val toAmount = when {
            current.type != TxnType.TRANSFER -> null
            current.needsToAmount -> current.toAmountMinor
            // Same-currency transfer: what leaves is exactly what lands.
            else -> current.amountMinor
        }

        val transaction = Transaction(
            id = current.editingId ?: 0,
            type = current.type,
            dateMillis = current.dateMillis,
            accountId = current.accountId!!,
            toAccountId = if (current.type == TxnType.TRANSFER) current.toAccountId else null,
            categoryId = if (current.type == TxnType.TRANSFER) null else current.categoryId,
            amountMinor = current.amountMinor,
            toAmountMinor = toAmount,
            fxRateToBase = if (current.type == TxnType.TRANSFER) {
                accounts.rateToBase(current.currencyCode, current.baseCurrency)
            } else {
                current.fxRateToBase
            },
            payee = current.payee.trim(),
            note = current.note.trim(),
            createdAtMillis = System.currentTimeMillis()
        )

        if (current.isEditing) {
            val original = transactions.byId(current.editingId!!)
            transactions.update(
                transaction.copy(
                    createdAtMillis = original?.createdAtMillis ?: transaction.createdAtMillis,
                    recurringRuleId = original?.recurringRuleId
                )
            )
        } else {
            transactions.insert(transaction)
        }
        _state.update { it.copy(saved = true, error = null) }
    }

    fun delete() = viewModelScope.launch {
        val id = _state.value.editingId ?: return@launch
        transactions.softDelete(id)
        _state.update { it.copy(saved = true) }
    }

    private fun validate(state: EntryUiState): String? = when {
        state.amountMinor <= 0 -> "Enter an amount"
        state.accountId == null -> "Pick an account"
        state.type == TxnType.TRANSFER && state.toAccountId == null -> "Pick the account to transfer to"
        state.type == TxnType.TRANSFER && state.toAccountId == state.accountId ->
            "Transfer needs two different accounts"
        state.type == TxnType.TRANSFER && state.needsToAmount && state.toAmountMinor <= 0 ->
            "Enter the amount that arrives"
        state.type != TxnType.TRANSFER && state.categoryId == null -> "Pick a category"
        state.needsFxRate && state.fxRateToBase <= 0.0 -> "Exchange rate must be above zero"
        else -> null
    }

    companion object {
        private const val MAX_AMOUNT_MINOR = 1_000_000_000_000L

        fun factory(app: FinanceApplication) = FinanceViewModelFactory(app) {
            EntryViewModel(
                settings = it.settingsRepository,
                accounts = it.accountRepository,
                categories = it.categoryRepository,
                transactions = it.transactionRepository
            )
        }
    }
}
