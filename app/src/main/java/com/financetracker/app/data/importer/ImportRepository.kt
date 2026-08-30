package com.financetracker.app.data.importer

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.financetracker.app.data.AppDatabase
import com.financetracker.app.data.account.Account
import com.financetracker.app.data.account.AccountType
import com.financetracker.app.data.category.Category
import com.financetracker.app.data.category.CategoryKind
import com.financetracker.app.data.rules.PayeeRuleEngine
import com.financetracker.app.data.settings.SettingsRepository
import com.financetracker.app.data.txn.Transaction
import com.financetracker.app.data.txn.TxnType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a completed import actually did. */
data class ImportResult(
    val imported: Int,
    val duplicatesSkipped: Int,
    val rowsSkipped: Int,
    val accountsCreated: Int,
    val categoriesCreated: Int,
    val autoCategorised: Int = 0
)

/** The file as read, before any mapping decisions are applied. */
data class LoadedCsv(
    val header: List<String>,
    val rows: List<List<String>>,
    val delimiter: Char
)

class ImportRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val settings: SettingsRepository
) {

    suspend fun load(source: Uri): LoadedCsv = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(source)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: throw IllegalArgumentException("Could not read that file.")

        val delimiter = CsvParser.detectDelimiter(text)
        val all = CsvParser.parse(text, delimiter)
        if (all.isEmpty()) throw IllegalArgumentException("That file has no rows in it.")

        LoadedCsv(header = all.first(), rows = all.drop(1), delimiter = delimiter)
    }

    /** Names already in use, lower-cased, so the preview can say what would be created. */
    suspend fun knownNames(): Pair<Set<String>, Set<String>> = withContext(Dispatchers.IO) {
        val accounts = database.accountDao().all().map { it.name.lowercase() }.toSet()
        val categories = database.categoryDao().all().map { it.name.lowercase() }.toSet()
        accounts to categories
    }

    /**
     * Writes the parsed rows.
     *
     * Everything happens in one database transaction, so a file that fails half way leaves nothing
     * behind. Accounts and categories named in the file are matched case-insensitively and created
     * only when [createMissing] is set - otherwise those rows fall back to the default account and
     * come in uncategorised, which is recoverable, whereas inventing accounts nobody asked for is
     * not.
     */
    suspend fun commit(
        rows: List<ParsedRow.Usable>,
        defaultAccountId: Long,
        createMissing: Boolean,
        skipDuplicates: Boolean,
        applyRules: Boolean = true
    ): ImportResult = withContext(Dispatchers.IO) {
        val baseCurrency = settings.currentBaseCurrency()
        // Bank exports arrive as raw merchant strings, which is exactly what the rules exist for.
        val rules = if (applyRules) database.payeeRuleDao().active() else emptyList()

        database.withTransaction {
            val accountDao = database.accountDao()
            val categoryDao = database.categoryDao()
            val txnDao = database.transactionDao()

            val accountsByName = accountDao.all().associateBy { it.name.lowercase() }.toMutableMap()
            val categoriesByName = categoryDao.all().associateBy { it.name.lowercase() }.toMutableMap()
            val defaultAccount = accountDao.byId(defaultAccountId)
                ?: throw IllegalArgumentException("Pick an account to import into.")

            var createdAccounts = 0
            var createdCategories = 0
            var duplicates = 0
            var imported = 0
            var autoCategorised = 0

            // Existing entries are fingerprinted once rather than queried per row, which keeps a
            // few thousand imported rows from becoming a few thousand round-trips.
            val existing = if (skipDuplicates) {
                txnDao.all()
                    .filter { it.deletedAtMillis == null }
                    .mapTo(mutableSetOf()) { fingerprint(it.dateMillis, it.amountMinor, it.accountId, it.payee) }
            } else {
                mutableSetOf()
            }

            for (raw in rows) {
                val outcome = if (rules.isEmpty()) null else PayeeRuleEngine.apply(rules, raw.payee)
                // A category named in the file is the user's own statement about that row, so it
                // wins; a rule only fills the gap when the file said nothing.
                val row = if (outcome == null) raw else raw.copy(
                    payee = outcome.payee ?: raw.payee,
                    categoryName = raw.categoryName
                )
                val accountName = row.accountName?.lowercase()
                val account = when {
                    accountName == null -> defaultAccount
                    accountsByName.containsKey(accountName) -> accountsByName.getValue(accountName)
                    createMissing -> {
                        val created = Account(
                            name = row.accountName.trim(),
                            type = AccountType.BANK,
                            // An imported account inherits the row's currency when it declares one,
                            // otherwise the base currency - never the default account's, which
                            // could silently mis-denominate a whole file.
                            currencyCode = row.currencyCode ?: baseCurrency,
                            openingBalanceMinor = 0,
                            colorArgb = IMPORT_COLOR,
                            sortOrder = accountsByName.size
                        )
                        val id = accountDao.insert(created)
                        createdAccounts++
                        created.copy(id = id).also { accountsByName[accountName] = it }
                    }
                    else -> defaultAccount
                }

                val categoryName = row.categoryName?.lowercase()
                val categoryId = when {
                    // A category named in the file is the user's own statement about that row.
                    categoryName != null && categoriesByName.containsKey(categoryName) ->
                        categoriesByName.getValue(categoryName).id

                    categoryName != null && createMissing -> {
                        val created = Category(
                            name = row.categoryName!!.trim(),
                            kind = if (row.type == TxnType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE,
                            parentId = null,
                            colorArgb = IMPORT_COLOR,
                            sortOrder = Int.MAX_VALUE / 2
                        )
                        val id = categoryDao.insert(created)
                        createdCategories++
                        categoriesByName[categoryName] = created.copy(id = id)
                        id
                    }

                    // The file said nothing useful, so the rules get their turn. This is the whole
                    // point of them: a bank export has no category column at all, and without this
                    // branch the rules would never fire on the files they exist to categorise.
                    outcome?.categoryId != null -> outcome.categoryId.also { autoCategorised++ }

                    else -> null
                }

                val print = fingerprint(row.dateMillis, row.amountMinor, account.id, row.payee)
                if (skipDuplicates && !existing.add(print)) {
                    duplicates++
                    continue
                }

                txnDao.insert(
                    Transaction(
                        type = row.type,
                        dateMillis = row.dateMillis,
                        accountId = account.id,
                        toAccountId = null,
                        categoryId = categoryId,
                        amountMinor = row.amountMinor,
                        toAmountMinor = null,
                        // Imported rows carry no historical rate, so they are recorded at parity
                        // with the base currency unless the account itself is in another currency.
                        fxRateToBase = 1.0,
                        payee = row.payee,
                        note = row.note,
                        createdAtMillis = System.currentTimeMillis()
                    )
                )
                imported++
            }

            ImportResult(
                imported = imported,
                duplicatesSkipped = duplicates,
                rowsSkipped = 0,
                accountsCreated = createdAccounts,
                categoriesCreated = createdCategories,
                autoCategorised = autoCategorised
            )
        }
    }

    companion object {
        private val IMPORT_COLOR = 0xFF8A94A6.toInt()

        /**
         * Identity for duplicate detection: same day, same amount, same account, same payee.
         *
         * Deliberately day-level rather than to the millisecond, because a re-exported file rarely
         * carries the original time of day. Two genuinely separate identical purchases on one day
         * from the same shop will collapse into one - the trade is a rare lost row against
         * duplicating an entire file on every re-import, and only the second is hard to notice.
         */
        fun fingerprint(dateMillis: Long, amountMinor: Long, accountId: Long, payee: String): String {
            val day = dateMillis / 86_400_000L
            return "$day|$amountMinor|$accountId|${payee.trim().lowercase()}"
        }
    }
}
