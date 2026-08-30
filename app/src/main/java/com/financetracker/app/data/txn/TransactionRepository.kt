package com.financetracker.app.data.txn

import androidx.room.withTransaction
import com.financetracker.app.data.AppDatabase
import com.financetracker.app.data.Money
import com.financetracker.app.data.tag.TagDao
import com.financetracker.app.data.tag.TaggedRow
import com.financetracker.app.data.tag.TxnTag
import kotlinx.coroutines.flow.Flow

/** The filter state of the transactions screen, passed straight through to one SQL query. */
data class TxnFilter(
    val fromMillis: Long,
    val toMillis: Long,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val tagId: Long? = null,
    val type: TxnType? = null,
    val query: String = "",
    val minMinor: Long? = null,
    val maxMinor: Long? = null
) {
    val isNarrowed: Boolean
        get() = accountId != null || categoryId != null || tagId != null || type != null ||
            query.isNotBlank() || minMinor != null || maxMinor != null
}

class TransactionRepository(
    private val database: AppDatabase,
    private val dao: TransactionDao,
    private val splitDao: SplitDao,
    private val tagDao: TagDao
) {

    fun recent(limit: Int): Flow<List<TransactionDetail>> = dao.observeRecent(limit)

    fun inRange(fromMillis: Long, toMillis: Long): Flow<List<TransactionDetail>> =
        dao.observeInRange(fromMillis, toMillis)

    fun filtered(filter: TxnFilter): Flow<List<TransactionDetail>> = dao.observeFiltered(
        fromMillis = filter.fromMillis,
        toMillis = filter.toMillis,
        accountId = filter.accountId,
        categoryId = filter.categoryId,
        tagId = filter.tagId,
        type = filter.type?.name,
        query = filter.query.trim(),
        minMinor = filter.minMinor,
        maxMinor = filter.maxMinor
    )

    fun datedRows(type: TxnType, fromMillis: Long, toMillis: Long): Flow<List<DatedAmountRow>> =
        dao.observeDatedRows(type.name, fromMillis, toMillis)

    fun balanceEffects(toMillis: Long): Flow<List<BalanceEffect>> = dao.observeBalanceEffects(toMillis)

    fun nonRecurringSpend(fromMillis: Long, toMillis: Long): Flow<List<AmountRow>> =
        dao.observeNonRecurringSpend(fromMillis, toMillis)

    fun amountRows(type: TxnType, fromMillis: Long, toMillis: Long): Flow<List<AmountRow>> =
        dao.observeAmountRows(type.name, fromMillis, toMillis)

    suspend fun amountRowsOnce(type: TxnType, fromMillis: Long, toMillis: Long, categoryId: Long? = null): List<AmountRow> =
        dao.amountRows(type.name, fromMillis, toMillis, categoryId)

    suspend fun detailById(id: Long): TransactionDetail? = dao.detailById(id)

    suspend fun byId(id: Long): Transaction? = dao.byId(id)

    suspend fun splitsFor(id: Long): List<SplitDetail> = splitDao.forTransaction(id)

    suspend fun tagIdsFor(id: Long): List<Long> = tagDao.tagIdsFor(id)

    /** Tags for a page of rows, keyed by transaction, in one query. */
    suspend fun tagsFor(ids: List<Long>): Map<Long, List<TaggedRow>> =
        if (ids.isEmpty()) emptyMap() else tagDao.tagsForTransactions(ids).groupBy { it.txnId }

    suspend fun splitCountsFor(ids: List<Long>): Map<Long, Int> =
        if (ids.isEmpty()) emptyMap()
        else splitDao.forTransactions(ids).groupBy { it.txnId }.mapValues { it.value.size }

    suspend fun payeeSuggestions(prefix: String): List<String> =
        if (prefix.isBlank()) emptyList() else dao.payeeSuggestions(prefix.trim())

    suspend fun lastCategoryForPayee(payee: String): Long? =
        if (payee.isBlank()) null else dao.lastCategoryForPayee(payee.trim())

    suspend fun earliestDateMillis(): Long? = dao.earliestDateMillis()

    /**
     * Writes a transaction together with its splits and tags in one database transaction, so a
     * failure cannot leave an entry whose legs do not add up to it.
     *
     * Splits and tags are replaced wholesale rather than diffed: the editor always sends the full
     * intended set, and reconciling a partial update is a source of bugs with no upside here.
     */
    suspend fun save(
        transaction: Transaction,
        splits: List<SplitDraft>,
        tagIds: List<Long>
    ): Long = database.withTransaction {
        val id = if (transaction.id == 0L) {
            dao.insert(transaction)
        } else {
            dao.update(transaction)
            transaction.id
        }

        splitDao.deleteForTransaction(id)
        // A single split is just a categorised transaction wearing a hat, so it is not stored as
        // one - that keeps the "either categoryId or splits" invariant simple.
        if (splits.size >= 2) {
            splitDao.insertAll(
                splits.map { TxnSplit(txnId = id, categoryId = it.categoryId, amountMinor = it.amountMinor, note = it.note) }
            )
        }

        tagDao.clearTagsFor(id)
        if (tagIds.isNotEmpty()) {
            tagDao.linkAll(tagIds.distinct().map { TxnTag(txnId = id, tagId = it) })
        }
        id
    }

    suspend fun insert(transaction: Transaction): Long = dao.insert(transaction)

    suspend fun update(transaction: Transaction) = dao.update(transaction)

    suspend fun softDelete(id: Long) = dao.softDelete(id, System.currentTimeMillis())

    suspend fun restore(id: Long) = dao.restore(id)

    companion object {
        /** Sums mixed-currency rows into base minor units using each row's own frozen rate. */
        fun sumToBase(rows: List<AmountRow>, baseCurrency: String): Long =
            rows.sumOf { Money.toBaseMinor(it.amountMinor, it.currencyCode, it.fxRateToBase, baseCurrency) }
    }
}
