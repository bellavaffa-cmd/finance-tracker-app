package com.financetracker.app.ui.entry

import android.net.Uri

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.FinanceApplication
import com.financetracker.app.data.Money
import com.financetracker.app.data.account.AccountRepository
import com.financetracker.app.data.account.AccountWithBalance
import com.financetracker.app.data.attachment.AttachmentStore
import com.financetracker.app.data.receipt.ReceiptReading
import com.financetracker.app.data.receipt.ReceiptScanner
import com.financetracker.app.data.category.Category
import com.financetracker.app.data.category.CategoryGroup
import com.financetracker.app.data.category.CategoryKind
import com.financetracker.app.data.category.CategoryRepository
import com.financetracker.app.data.rules.PayeeRuleEngine
import com.financetracker.app.data.rules.PayeeRuleRepository
import com.financetracker.app.data.settings.SettingsRepository
import com.financetracker.app.data.tag.Tag
import com.financetracker.app.data.tag.TagRepository
import com.financetracker.app.data.txn.SplitDraft
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
    /** Two or more legs means this is a split; fewer means the single category above applies. */
    val splits: List<SplitDraft> = emptyList(),
    val allTags: List<Tag> = emptyList(),
    val selectedTagIds: Set<Long> = emptySet(),
    /** File name of the attached receipt, if there is one. */
    val attachmentName: String? = null,
    val attaching: Boolean = false,
    val scanning: Boolean = false,
    /** What was read off the receipt, offered but never applied on its own. */
    val reading: ReceiptReading? = null,
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

    val isSplit: Boolean get() = splits.isNotEmpty()

    val splitTotalMinor: Long get() = splits.sumOf { it.amountMinor }

    /** Positive means the legs do not yet account for the whole payment. */
    val splitRemainderMinor: Long get() = amountMinor - splitTotalMinor

    val splitBalances: Boolean get() = splitRemainderMinor == 0L
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
    private val transactions: TransactionRepository,
    private val tags: TagRepository,
    private val rules: PayeeRuleRepository,
    private val attachments: AttachmentStore,
    private val scanner: ReceiptScanner
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
            val tagList = tags.allTags.first()

            _state.update { current ->
                val defaultAccount = current.accountId ?: accountList.firstOrNull()?.id
                current.copy(
                    accounts = accountList,
                    expenseGroups = expense,
                    incomeGroups = income,
                    categoriesById = byId,
                    allTags = tagList,
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
                attachmentName = existing.attachmentName,
                saved = false,
                error = null
            )
        }
        val splits = transactions.splitsFor(transactionId)
            .map { SplitDraft(categoryId = it.categoryId, amountMinor = it.amountMinor, note = it.note) }
        val tagIds = transactions.tagIdsFor(transactionId).toSet()
        _state.update { it.copy(splits = splits, selectedTagIds = tagIds) }
    }

    // --- Splits ------------------------------------------------------------------------------

    /**
     * Starts a split with two legs: the first pre-filled with the whole amount, the second empty.
     * Splitting is only meaningful for expenses and income - a transfer has no category to divide.
     */
    fun startSplit() = _state.update { current ->
        if (current.type == TxnType.TRANSFER || current.isSplit) return@update current
        current.copy(
            splits = listOf(
                SplitDraft(categoryId = current.categoryId, amountMinor = current.amountMinor),
                SplitDraft()
            ),
            categoryId = null,
            error = null
        )
    }

    /** Collapsing a split keeps the first leg's category, which is the one you chose first. */
    fun cancelSplit() = _state.update { current ->
        current.copy(
            splits = emptyList(),
            categoryId = current.splits.firstOrNull()?.categoryId ?: current.categoryId,
            error = null
        )
    }

    fun addSplitLeg() = _state.update { current ->
        if (!current.isSplit) return@update current
        // The new leg starts with whatever is still unaccounted for, which is almost always what
        // you want and saves typing the remainder by hand.
        val remainder = current.splitRemainderMinor.coerceAtLeast(0)
        current.copy(splits = current.splits + SplitDraft(amountMinor = remainder), error = null)
    }

    fun removeSplitLeg(index: Int) = _state.update { current ->
        if (index !in current.splits.indices) return@update current
        val remaining = current.splits.toMutableList().apply { removeAt(index) }
        // Dropping to one leg is just a plain categorised transaction again.
        if (remaining.size < 2) {
            current.copy(splits = emptyList(), categoryId = remaining.firstOrNull()?.categoryId, error = null)
        } else {
            current.copy(splits = remaining, error = null)
        }
    }

    fun setSplitCategory(index: Int, categoryId: Long) = updateLeg(index) { it.copy(categoryId = categoryId) }

    fun setSplitAmount(index: Int, amountMinor: Long) =
        updateLeg(index) { it.copy(amountMinor = amountMinor.coerceAtLeast(0)) }

    fun setSplitNote(index: Int, note: String) = updateLeg(index) { it.copy(note = note) }

    /** Pushes whatever is unaccounted for into one leg, so the split balances in a single tap. */
    fun absorbRemainder(index: Int) = _state.update { current ->
        if (index !in current.splits.indices) return@update current
        val leg = current.splits[index]
        val corrected = leg.amountMinor + current.splitRemainderMinor
        if (corrected < 0) return@update current
        current.copy(
            splits = current.splits.toMutableList().apply { set(index, leg.copy(amountMinor = corrected)) },
            error = null
        )
    }

    private fun updateLeg(index: Int, transform: (SplitDraft) -> SplitDraft) = _state.update { current ->
        if (index !in current.splits.indices) return@update current
        current.copy(
            splits = current.splits.toMutableList().apply { set(index, transform(get(index))) },
            error = null
        )
    }

    // --- Receipt -----------------------------------------------------------------------------

    fun attachFrom(uri: Uri) = viewModelScope.launch {
        _state.update { it.copy(attaching = true) }
        val stored = attachments.importFrom(uri)
        applyAttachment(stored)
    }

    fun attachCapture(file: java.io.File) = viewModelScope.launch {
        _state.update { it.copy(attaching = true) }
        val stored = attachments.importFrom(file)
        applyAttachment(stored)
    }

    /**
     * Replacing a receipt deletes the previous image straight away. The transaction is the only
     * thing that ever points at it, so leaving it behind would just accumulate dead files.
     */
    private fun applyAttachment(stored: String?) {
        val previous = _state.value.attachmentName
        _state.update {
            it.copy(
                attaching = false,
                attachmentName = stored ?: it.attachmentName,
                reading = null,
                error = if (stored == null) "That image could not be read." else it.error
            )
        }
        if (stored != null && previous != null && previous != stored) {
            viewModelScope.launch { attachments.delete(previous) }
        }
        if (stored != null) scanReceipt(stored)
    }

    /**
     * Reads the receipt in the background. Nothing is filled in automatically - a total read off a
     * blurry photograph is a guess, and a wrong amount quietly written into a ledger is worse than
     * no amount at all. The result is offered as a chip the user can accept.
     */
    private fun scanReceipt(name: String) = viewModelScope.launch {
        val uri = attachments.uriFor(name) ?: return@launch
        _state.update { it.copy(scanning = true) }
        val reading = scanner.scan(uri)
        _state.update { current ->
            current.copy(
                scanning = false,
                // Only worth offering when it found something the entry does not already have.
                reading = reading.takeIf { it.totalMinor != null && it.totalMinor > 0 }
            )
        }
    }

    /** Accepts the scanned total, and the merchant name when the payee is still blank. */
    fun applyReading() {
        val reading = _state.value.reading ?: return
        val total = reading.totalMinor ?: return
        _state.update { current ->
            current.copy(
                amountMinor = total,
                payee = current.payee.ifBlank { reading.merchant?.take(40).orEmpty() },
                reading = null,
                error = null
            )
        }
        recomputeTransferAmount()
    }

    fun dismissReading() = _state.update { it.copy(reading = null) }

    fun removeAttachment() {
        val current = _state.value.attachmentName ?: return
        _state.update { it.copy(attachmentName = null) }
        // Only drop the file once the row is saved without it; until then the edit is undoable by
        // simply backing out, and deleting now would destroy a receipt the user still has.
        pendingDeletion = current
    }

    private var pendingDeletion: String? = null

    fun cameraTarget(): Pair<java.io.File, Uri> = attachments.newCameraTarget()

    fun attachmentUri(name: String): Uri? = attachments.uriFor(name)

    // --- Tags --------------------------------------------------------------------------------

    fun toggleTag(tagId: Long) = _state.update { current ->
        val next = current.selectedTagIds.toMutableSet()
        if (!next.add(tagId)) next.remove(tagId)
        current.copy(selectedTagIds = next, error = null)
    }

    /** Creates a tag if it does not exist yet and applies it, so tagging never needs a detour. */
    fun createAndApplyTag(name: String) = viewModelScope.launch {
        val colour = TAG_PALETTE[(_state.value.allTags.size) % TAG_PALETTE.size]
        val tag = tags.findOrCreate(name, colour) ?: return@launch
        val refreshed = tags.all()
        _state.update {
            it.copy(allTags = refreshed, selectedTagIds = it.selectedTagIds + tag.id, error = null)
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
        viewModelScope.launch { autoFillFrom(payee) }
    }

    /**
     * Fills in what the payee implies. An explicit rule wins over "what you did last time" -
     * a rule is a standing decision, whereas history is only a guess about one.
     *
     * Neither ever overwrites a category already chosen by hand on this entry.
     */
    private suspend fun autoFillFrom(payee: String) {
        if (payee.isBlank()) return
        val outcome = PayeeRuleEngine.apply(rules.active(), payee)
        val suggested = outcome?.categoryId ?: transactions.lastCategoryForPayee(payee) ?: return

        _state.update { current ->
            val kind = current.categoriesById[suggested]?.kind
            val matchesDirection = when (current.type) {
                TxnType.EXPENSE -> kind == CategoryKind.EXPENSE
                TxnType.INCOME -> kind == CategoryKind.INCOME
                TxnType.TRANSFER -> false
            }
            current.copy(
                categoryId = if (current.categoryId == null && matchesDirection) suggested
                else current.categoryId,
                // A rule that tidies "CARD PURCHASE LIDL 4432" into "Lidl" should do so here too,
                // or the ledger keeps the messy name the rule exists to replace.
                payee = outcome?.payee ?: current.payee,
                accountId = outcome?.accountId ?: current.accountId
            )
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
        // Rules must also fire for a payee that was typed out rather than picked from a
        // suggestion - otherwise the feature only works for people who happen to tap the chip.
        if (_state.value.categoryId == null && !_state.value.isSplit &&
            _state.value.type != TxnType.TRANSFER
        ) {
            autoFillFrom(_state.value.payee)
        }

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
            // A split transaction carries no category of its own; its legs hold them. Leaving both
            // set would double-count the payment in every report.
            categoryId = if (current.type == TxnType.TRANSFER || current.isSplit) null
            else current.categoryId,
            amountMinor = current.amountMinor,
            toAmountMinor = toAmount,
            fxRateToBase = if (current.type == TxnType.TRANSFER) {
                accounts.rateToBase(current.currencyCode, current.baseCurrency)
            } else {
                current.fxRateToBase
            },
            payee = current.payee.trim(),
            note = current.note.trim(),
            attachmentName = current.attachmentName,
            createdAtMillis = System.currentTimeMillis()
        )

        val toSave = if (current.isEditing) {
            val original = transactions.byId(current.editingId!!)
            transaction.copy(
                createdAtMillis = original?.createdAtMillis ?: transaction.createdAtMillis,
                recurringRuleId = original?.recurringRuleId
            )
        } else {
            transaction
        }

        transactions.save(
            transaction = toSave,
            splits = if (current.type == TxnType.TRANSFER) emptyList() else current.splits,
            tagIds = current.selectedTagIds.toList()
        )
        // The removal is only made permanent once the row without it is actually written.
        pendingDeletion?.let { orphan ->
            if (orphan != current.attachmentName) attachments.delete(orphan)
            pendingDeletion = null
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
        state.type != TxnType.TRANSFER && !state.isSplit && state.categoryId == null -> "Pick a category"
        state.isSplit && state.splits.any { it.categoryId == null } -> "Give every split a category"
        state.isSplit && state.splits.any { it.amountMinor <= 0 } -> "Every split needs an amount"
        // The legs must account for the payment exactly, or the ledger and the reports disagree
        // about how much was actually spent.
        state.isSplit && !state.splitBalances -> splitMismatchMessage(state)
        state.needsFxRate && state.fxRateToBase <= 0.0 -> "Exchange rate must be above zero"
        else -> null
    }

    private fun splitMismatchMessage(state: EntryUiState): String {
        val remainder = state.splitRemainderMinor
        val amount = Money.format(kotlin.math.abs(remainder), state.currencyCode)
        return if (remainder > 0) "$amount still unassigned" else "Splits exceed the total by $amount"
    }

    companion object {
        private const val MAX_AMOUNT_MINOR = 1_000_000_000_000L

        /** Colours cycled through when a new tag is created, so tags stay visually distinct. */
        private val TAG_PALETTE = listOf(
            0xFF4C8DFF, 0xFF3FBF8F, 0xFFE8A33D, 0xFFE85E7A,
            0xFF9B7BE8, 0xFF5EC8D8, 0xFFE0C24E, 0xFFD98BC8
        ).map { it.toInt() }

        fun factory(app: FinanceApplication) = FinanceViewModelFactory(app) {
            EntryViewModel(
                settings = it.settingsRepository,
                accounts = it.accountRepository,
                categories = it.categoryRepository,
                transactions = it.transactionRepository,
                tags = it.tagRepository,
                rules = it.payeeRuleRepository,
                attachments = it.attachmentStore,
                scanner = it.receiptScanner
            )
        }
    }
}
