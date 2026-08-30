package com.financetracker.app.data.txn

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

private const val SPLIT_SELECT = "SELECT s.id, s.txnId, s.categoryId, " +
    "c.name AS categoryName, c.colorArgb AS colorArgb, s.amountMinor, s.note " +
    "FROM txn_split s LEFT JOIN category c ON c.id = s.categoryId "

@Dao
interface SplitDao {

    @Query(SPLIT_SELECT + "WHERE s.txnId = :txnId ORDER BY s.id ASC")
    suspend fun forTransaction(txnId: Long): List<SplitDetail>

    /** Bulk fetch, so rendering a list of split transactions is one query rather than one each. */
    @Query(SPLIT_SELECT + "WHERE s.txnId IN (:txnIds) ORDER BY s.id ASC")
    suspend fun forTransactions(txnIds: List<Long>): List<SplitDetail>

    @Query("SELECT txnId FROM txn_split GROUP BY txnId")
    suspend fun splitTransactionIds(): List<Long>

    @Insert
    suspend fun insertAll(splits: List<TxnSplit>)

    @Query("DELETE FROM txn_split WHERE txnId = :txnId")
    suspend fun deleteForTransaction(txnId: Long)

    // --- Backup ---------------------------------------------------------------------------------

    @Query("SELECT * FROM txn_split ORDER BY id ASC")
    suspend fun all(): List<TxnSplit>

    @Query("DELETE FROM txn_split")
    suspend fun clear()
}
