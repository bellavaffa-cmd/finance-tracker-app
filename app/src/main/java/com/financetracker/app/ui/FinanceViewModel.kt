package com.financetracker.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.data.Account
import com.financetracker.app.data.Budget
import com.financetracker.app.data.Category
import com.financetracker.app.data.FinanceRepository
import com.financetracker.app.data.Txn
import com.financetracker.app.domain.Ledger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything on screen derives from this one snapshot of the ledger. */
data class LedgerState(
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val txns: List<Txn> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val currency: String = "EUR",
    val loaded: Boolean = false,
) {
    val accountById: Map<Long, Account> by lazy { accounts.associateBy { it.id } }
    val categoryById: Map<Long, Category> by lazy { categories.associateBy { it.id } }
    val balances: Map<Long, Long> by lazy { Ledger.balances(accounts, txns) }
    val activeAccounts: List<Account> by lazy { accounts.filter { !it.archived } }
}

class FinanceViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = FinanceRepository(app)

    val state: StateFlow<LedgerState> = combine(
        repo.accounts, repo.categories, repo.transactions, repo.budgets, repo.currency,
    ) { a, c, t, b, cur -> LedgerState(a, c, t, b, cur, loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LedgerState())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    /** Shown as a dialog after an import, because the errors need reading, not a snackbar. */
    val importResult = MutableStateFlow<FinanceRepository.ImportResult?>(null)

    private fun launchOp(success: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                success?.let { _messages.tryEmit(it) }
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: "Something went wrong.")
            }
        }
    }

    fun save(txn: Txn) = launchOp { repo.save(txn) }
    fun deleteTxn(id: Long) = launchOp("Transaction deleted") { repo.deleteTxn(id) }
    fun save(account: Account) = launchOp { repo.save(account) }
    fun deleteAccount(id: Long) = launchOp {
        if (!repo.deleteAccount(id)) _messages.tryEmit("This account has transactions, so it can only be archived.")
    }
    fun save(category: Category) = launchOp { repo.save(category) }
    fun deleteCategory(id: Long) = launchOp("Category deleted") { repo.deleteCategory(id) }
    fun setBudget(categoryId: Long, limit: Long?) = launchOp { repo.setBudget(categoryId, limit) }
    fun setCurrency(code: String) = launchOp("Currency set to ${code.uppercase()}") { repo.setCurrency(code) }

    private val resolver get() = getApplication<Application>().contentResolver

    private suspend fun write(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
            ?: error("Couldn't open that file for writing.")
    }

    private suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("Couldn't open that file.")
    }

    fun exportCsv(uri: Uri) = launchOp("Exported to CSV") { write(uri, repo.exportCsv()) }
    fun importCsv(uri: Uri) = launchOp { importResult.value = repo.importCsv(read(uri)) }
    fun backup(uri: Uri) = launchOp("Backup saved") { write(uri, repo.backup()) }
    fun restore(uri: Uri) = launchOp("Backup restored") { repo.restore(read(uri)) }
}
