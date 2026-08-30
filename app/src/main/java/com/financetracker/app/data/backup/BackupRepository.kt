package com.financetracker.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.financetracker.app.data.AppDatabase
import com.financetracker.app.data.attachment.AttachmentStore
import com.financetracker.app.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** What a restore actually put back, for the confirmation message. */
data class RestoreSummary(
    val accounts: Int,
    val categories: Int,
    val transactions: Int,
    val budgets: Int,
    val rules: Int,
    val tags: Int
)

/**
 * Export and restore. All file access goes through the Storage Access Framework, so the user picks
 * the destination themselves and the app needs no storage permission at all - and the file lands
 * somewhere they chose, rather than in app-private storage that vanishes on uninstall.
 */
class BackupRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val settings: SettingsRepository,
    private val attachments: AttachmentStore,
    private val appVersion: String
) {

    suspend fun exportBackup(target: Uri): Int = withContext(Dispatchers.IO) {
        val payload = collect()
        val json = BackupCodec.encode(payload, appVersion, System.currentTimeMillis())
        write(target, json)
        payload.transactionCount
    }

    suspend fun exportCsv(target: Uri): Int = withContext(Dispatchers.IO) {
        val details = database.transactionDao().allDetails()
        val ids = details.map { it.id }
        // Two bulk queries rather than two per row: a few years of entries would otherwise be
        // thousands of round-trips and a visibly frozen screen.
        val splits = database.splitDao().forTransactions(ids).groupBy { it.txnId }
        val tags = database.tagDao().tagsForTransactions(ids)
            .groupBy { it.txnId }
            .mapValues { entry -> entry.value.map { "#" + it.name } }

        val rows = details.map { detail ->
            ExportRow(
                detail = detail,
                splits = splits[detail.id].orEmpty(),
                tags = tags[detail.id].orEmpty()
            )
        }
        write(target, CsvExporter.export(rows, settings.currentBaseCurrency()))
        details.size
    }

    /**
     * Replaces everything with the contents of [source].
     *
     * This is a replace, not a merge. Merging two ledgers cannot be done safely without a stable
     * identity for each transaction across devices, and a merge that guesses would silently
     * duplicate or drop entries - the two failure modes a finance app can least afford. The whole
     * thing runs in one Room transaction, so a failure part-way leaves the existing data intact.
     */
    suspend fun restore(source: Uri): RestoreSummary = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(source)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: throw BackupFormatException("Could not read that file.")

        val payload = BackupCodec.decode(text.removePrefix("\uFEFF"))

        database.withTransaction {
            // Children first, so nothing is left pointing at a row that has already gone.
            database.tagDao().clearLinks()
            database.splitDao().clear()
            database.tagDao().clearTags()
            database.transactionDao().clear()
            database.budgetDao().clear()
            database.recurringDao().clear()
            database.categoryDao().clear()
            database.accountDao().clear()
            database.currencyRateDao().clear()
            database.goalDao().clear()
            database.debtDao().clear()
            database.payeeRuleDao().clear()

            database.accountDao().insertAll(payload.accounts)
            database.categoryDao().insertAll(payload.categories)
            database.recurringDao().insertAll(payload.rules)
            database.budgetDao().insertAll(payload.budgets)
            database.transactionDao().insertAll(payload.transactions)
            database.currencyRateDao().insertAll(payload.rates)
            database.tagDao().insertAll(payload.tags)
            // Links and splits last: both point at rows that must already exist.
            database.tagDao().linkAll(payload.tagLinks)
            database.splitDao().insertAll(payload.splits)
            database.goalDao().insertAll(payload.goals)
            database.debtDao().insertAll(payload.debts)
            database.payeeRuleDao().insertAll(payload.payeeRules)
        }
        settings.restore(payload.settings)

        // A restore swaps out the whole ledger, which is the one moment receipt images can be left
        // with nothing pointing at them. Anything the new ledger does not reference is swept up.
        attachments.pruneOrphans(database.transactionDao().referencedAttachments().toSet())

        RestoreSummary(
            accounts = payload.accounts.size,
            categories = payload.categories.size,
            transactions = payload.transactionCount,
            budgets = payload.budgets.size,
            rules = payload.rules.size,
            tags = payload.tags.size
        )
    }

    private fun write(target: Uri, content: String) {
        // "wt" truncates: without it, overwriting a longer existing file leaves its tail behind and
        // produces a file that is silently corrupt from the end.
        context.contentResolver.openOutputStream(target, "wt")?.use {
            it.write(content.toByteArray(Charsets.UTF_8))
            it.flush()
        } ?: throw BackupFormatException("Could not write to that location.")
    }

    private suspend fun collect(): BackupPayload = BackupPayload(
        accounts = database.accountDao().all(),
        categories = database.categoryDao().all(),
        transactions = database.transactionDao().all(),
        budgets = database.budgetDao().all(),
        rules = database.recurringDao().all(),
        rates = database.currencyRateDao().all(),
        tags = database.tagDao().all(),
        tagLinks = database.tagDao().allLinks(),
        splits = database.splitDao().all(),
        goals = database.goalDao().all(),
        debts = database.debtDao().all(),
        payeeRules = database.payeeRuleDao().all(),
        settings = settings.snapshot()
    )

    companion object {
        const val BACKUP_MIME = "application/json"
        const val CSV_MIME = "text/csv"

        /** Dated filenames so successive exports sit next to each other instead of overwriting. */
        fun backupFileName(today: LocalDate = LocalDate.now()) = "finance-tracker-backup-$today.json"

        fun csvFileName(today: LocalDate = LocalDate.now()) = "finance-tracker-$today.csv"
    }
}
