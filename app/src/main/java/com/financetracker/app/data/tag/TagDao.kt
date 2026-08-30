package com.financetracker.app.data.tag

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Query("SELECT * FROM tag ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Tag>>

    @Query("SELECT * FROM tag ORDER BY id ASC")
    suspend fun all(): List<Tag>

    @Query("SELECT * FROM tag WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun byName(name: String): Tag?

    @Query("SELECT COUNT(*) FROM txn_tag WHERE tagId = :tagId")
    suspend fun usageCount(tagId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: Tag): Long

    @Update
    suspend fun update(tag: Tag)

    @Delete
    suspend fun delete(tag: Tag)

    // --- Links ---------------------------------------------------------------------------------

    @Query("SELECT tagId FROM txn_tag WHERE txnId = :txnId")
    suspend fun tagIdsFor(txnId: Long): List<Long>

    /**
     * Tags for a set of transactions in one query, so a list of 200 rows does not become 200
     * round-trips to render its chips.
     */
    @Query(
        "SELECT tt.txnId AS txnId, t.id AS id, t.name AS name, t.colorArgb AS colorArgb " +
            "FROM txn_tag tt JOIN tag t ON t.id = tt.tagId " +
            "WHERE tt.txnId IN (:txnIds)"
    )
    suspend fun tagsForTransactions(txnIds: List<Long>): List<TaggedRow>

    @Query(
        "SELECT tt.txnId AS txnId, t.id AS id, t.name AS name, t.colorArgb AS colorArgb " +
            "FROM txn_tag tt JOIN tag t ON t.id = tt.tagId"
    )
    fun observeAllLinks(): Flow<List<TaggedRow>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(link: TxnTag)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkAll(links: List<TxnTag>)

    @Query("DELETE FROM txn_tag WHERE txnId = :txnId")
    suspend fun clearTagsFor(txnId: Long)

    @Query("SELECT * FROM txn_tag")
    suspend fun allLinks(): List<TxnTag>

    @Query("DELETE FROM tag")
    suspend fun clearTags()

    @Query("DELETE FROM txn_tag")
    suspend fun clearLinks()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tags: List<Tag>)

    /**
     * Amounts per tag for a window, still in each transaction's own currency so the caller can
     * convert with the rate frozen on that row. Splits need no special handling here: a tag applies
     * to the whole transaction, not to one leg of it.
     *
     * Totals across tags can legitimately exceed the period total, because one transaction can
     * carry several tags. That is inherent to cross-cutting labels, not a bug.
     */
    @Query(
        "SELECT t.id AS tagId, t.name AS name, t.colorArgb AS colorArgb, " +
            "x.id AS txnId, x.amountMinor AS amountMinor, " +
            "a.currencyCode AS currencyCode, x.fxRateToBase AS fxRateToBase " +
            "FROM txn_tag tt " +
            "JOIN tag t ON t.id = tt.tagId " +
            "JOIN txn x ON x.id = tt.txnId " +
            "JOIN account a ON a.id = x.accountId " +
            "WHERE x.deletedAtMillis IS NULL AND x.type = :type " +
            "AND x.dateMillis >= :fromMillis AND x.dateMillis < :toMillis"
    )
    fun observeTagAmounts(type: String, fromMillis: Long, toMillis: Long): Flow<List<TagAmountRow>>
}

/** One tagged transaction's amount, awaiting conversion to the base currency. */
data class TagAmountRow(
    val tagId: Long,
    val name: String,
    val colorArgb: Int,
    val txnId: Long,
    val amountMinor: Long,
    val currencyCode: String,
    val fxRateToBase: Double
)

/** Flat join row: which tag belongs to which transaction. */
data class TaggedRow(
    val txnId: Long,
    val id: Long,
    val name: String,
    val colorArgb: Int
)
