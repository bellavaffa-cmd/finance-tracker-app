package com.financetracker.app.data

import android.content.Context
import androidx.room.withTransaction
import com.financetracker.app.domain.Backup
import com.financetracker.app.domain.Defaults
import com.financetracker.app.domain.LedgerCsv
import com.financetracker.app.domain.Money
import com.financetracker.app.domain.Snapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Currency
import java.util.Locale

class FinanceRepository(context: Context) {

    private val db = AppDatabase.get(context)
    private val dao = db.dao()
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val accounts = dao.accounts()
    val categories = dao.categories()
    val transactions = dao.transactions()
    val budgets = dao.budgets()

    private val _currency = MutableStateFlow(prefs.getString(KEY_CURRENCY, null) ?: localCurrency())
    val currency: StateFlow<String> = _currency.asStateFlow()

    fun setCurrency(code: String) {
        val c = code.uppercase()
        require(Money.isValidCurrency(c)) { "Unknown currency code \"$code\"." }
        prefs.edit().putString(KEY_CURRENCY, c).apply()
        _currency.value = c
    }

    suspend fun save(txn: Txn) {
        require(txn.amount > 0) { "Amount must be more than zero." }
        if (txn.id == 0L) dao.insert(txn) else dao.update(txn)
    }

    suspend fun deleteTxn(id: Long) = dao.deleteTxn(id)

    suspend fun save(account: Account) {
        if (account.id == 0L) dao.insert(account) else dao.update(account)
    }

    /** An account with history can only be archived, so past entries never lose their account. */
    suspend fun deleteAccount(id: Long): Boolean {
        if (dao.countForAccount(id) > 0) return false
        dao.deleteAccount(id)
        return true
    }

    suspend fun save(category: Category) {
        if (category.id == 0L) dao.insert(category) else dao.update(category)
    }

    /** Its transactions become uncategorised rather than disappearing. */
    suspend fun deleteCategory(id: Long) = db.withTransaction {
        dao.uncategorise(id)
        dao.deleteBudget(id)
        dao.deleteCategory(id)
    }

    suspend fun setBudget(categoryId: Long, limit: Long?) {
        if (limit == null || limit <= 0) dao.deleteBudget(categoryId) else dao.upsert(Budget(categoryId, limit))
    }

    suspend fun exportCsv(): String =
        LedgerCsv.export(dao.allTransactions(), dao.allAccounts(), dao.allCategories(), currency.value)

    data class ImportResult(val imported: Int, val duplicates: Int, val errors: List<String>, val warnings: List<String>)

    suspend fun importCsv(text: String): ImportResult = db.withTransaction {
        val parsed = LedgerCsv.parse(text, currency.value)
        val accounts = dao.allAccounts()
        val categories = dao.allCategories()
        val fallback = accounts.firstOrNull { !it.archived }?.name ?: "Imported"
        val plan = LedgerCsv.plan(parsed.rows, accounts, categories, dao.allTransactions(), fallback)

        plan.newAccounts.forEach { dao.insert(Account(name = it, type = AccountType.BANK)) }
        plan.newCategories.forEachIndexed { i, (name, kind) ->
            dao.insert(Category(name = name, kind = kind, color = Defaults.colorFor(categories.size + i)))
        }
        val accId = dao.allAccounts().associate { it.name.lowercase() to it.id }
        val catId = dao.allCategories().associate { (it.name.lowercase() to it.kind) to it.id }
        dao.insertTxns(plan.toInsert.map { r ->
            val kind = if (r.type == TxnType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
            Txn(
                type = r.type,
                amount = r.amount,
                accountId = accId.getValue(r.account.lowercase()),
                toAccountId = r.toAccount.takeIf { it.isNotBlank() }?.let { accId.getValue(it.lowercase()) },
                categoryId = r.category.takeIf { it.isNotBlank() }?.let { catId[it.lowercase() to kind] },
                date = r.date.toEpochDay(),
                payee = r.payee,
                note = r.note,
            )
        })
        ImportResult(plan.toInsert.size, plan.duplicates, parsed.errors, parsed.warnings)
    }

    suspend fun backup(): String = Backup.encode(
        Snapshot(currency.value, dao.allAccounts(), dao.allCategories(), dao.allTransactions(), dao.allBudgets())
    )

    /** Replaces the whole ledger in one transaction, so a failure part-way leaves it untouched. */
    suspend fun restore(text: String) {
        val s = Backup.decode(text)
        db.withTransaction {
            dao.clearTransactions(); dao.clearBudgets(); dao.clearCategories(); dao.clearAccounts()
            dao.insertAccounts(s.accounts)
            dao.insertCategories(s.categories)
            dao.insertTxns(s.txns)
            dao.insertBudgets(s.budgets)
        }
        setCurrency(s.currency)
    }

    private fun localCurrency(): String =
        runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull() ?: "EUR"

    private companion object {
        const val KEY_CURRENCY = "currency"
    }
}
