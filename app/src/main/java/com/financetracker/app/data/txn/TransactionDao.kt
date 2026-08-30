package com.financetracker.app.data.txn

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Shared projection for every list query. Kotlin folds a template of `const val`s into a
 * compile-time constant, so Room still sees one literal SQL string per annotation.
 */
private const val DETAIL_SELECT = "SELECT t.id, t.type, t.dateMillis, t.amountMinor, t.toAmountMinor, " +
    "t.fxRateToBase, t.payee, t.note, " +
    "t.accountId, a.name AS accountName, a.currencyCode AS accountCurrency, " +
    "t.toAccountId, b.name AS toAccountName, b.currencyCode AS toAccountCurrency, " +
    "t.categoryId, c.name AS categoryName, c.colorArgb AS categoryColorArgb, " +
    "p.name AS parentCategoryName, t.recurringRuleId " +
    "FROM txn t " +
    "JOIN account a ON a.id = t.accountId " +
    "LEFT JOIN account b ON b.id = t.toAccountId " +
    "LEFT JOIN category c ON c.id = t.categoryId " +
    "LEFT JOIN category p ON p.id = c.parentId "

/**
 * Rows still in their own account's currency. Transfers are excluded from every aggregate here:
 * moving your own money between your own accounts is not spending, and counting it is the classic
 * way a homemade tracker ends up reporting double the expenses you actually had.
 *
 * Split transactions contribute their legs instead of themselves. The first half of the union
 * therefore excludes anything that has splits, and the second half supplies those legs - if both
 * halves matched the same transaction, every split payment would be counted twice.
 */
private const val UNSPLIT_AMOUNTS = "SELECT t.categoryId AS categoryId, " +
    "COALESCE(p.name, c.name) AS categoryName, " +
    "COALESCE(p.colorArgb, c.colorArgb) AS colorArgb, " +
    "t.amountMinor AS amountMinor, a.currencyCode AS currencyCode, " +
    "t.fxRateToBase AS fxRateToBase " +
    "FROM txn t " +
    "JOIN account a ON a.id = t.accountId " +
    "LEFT JOIN category c ON c.id = t.categoryId " +
    "LEFT JOIN category p ON p.id = c.parentId " +
    "WHERE t.deletedAtMillis IS NULL AND t.type = :type " +
    "AND t.dateMillis >= :fromMillis AND t.dateMillis < :toMillis " +
    "AND NOT EXISTS (SELECT 1 FROM txn_split x WHERE x.txnId = t.id) "

private const val SPLIT_AMOUNTS = "SELECT s.categoryId AS categoryId, " +
    "COALESCE(p.name, c.name) AS categoryName, " +
    "COALESCE(p.colorArgb, c.colorArgb) AS colorArgb, " +
    "s.amountMinor AS amountMinor, a.currencyCode AS currencyCode, " +
    "t.fxRateToBase AS fxRateToBase " +
    "FROM txn_split s " +
    "JOIN txn t ON t.id = s.txnId " +
    "JOIN account a ON a.id = t.accountId " +
    "LEFT JOIN category c ON c.id = s.categoryId " +
    "LEFT JOIN category p ON p.id = c.parentId " +
    "WHERE t.deletedAtMillis IS NULL AND t.type = :type " +
    "AND t.dateMillis >= :fromMillis AND t.dateMillis < :toMillis "

/** Dated variant of the amount union, used for trends and anomaly detection. */
private const val UNSPLIT_DATED = "SELECT t.categoryId AS categoryId, " +
    "COALESCE(p.name, c.name) AS categoryName, " +
    "COALESCE(p.colorArgb, c.colorArgb) AS colorArgb, " +
    "t.amountMinor AS amountMinor, a.currencyCode AS currencyCode, " +
    "t.fxRateToBase AS fxRateToBase, t.dateMillis AS dateMillis " +
    "FROM txn t " +
    "JOIN account a ON a.id = t.accountId " +
    "LEFT JOIN category c ON c.id = t.categoryId " +
    "LEFT JOIN category p ON p.id = c.parentId " +
    "WHERE t.deletedAtMillis IS NULL AND t.type = :type " +
    "AND t.dateMillis >= :fromMillis AND t.dateMillis < :toMillis " +
    "AND NOT EXISTS (SELECT 1 FROM txn_split x WHERE x.txnId = t.id) "

private const val SPLIT_DATED = "SELECT s.categoryId AS categoryId, " +
    "COALESCE(p.name, c.name) AS categoryName, " +
    "COALESCE(p.colorArgb, c.colorArgb) AS colorArgb, " +
    "s.amountMinor AS amountMinor, a.currencyCode AS currencyCode, " +
    "t.fxRateToBase AS fxRateToBase, t.dateMillis AS dateMillis " +
    "FROM txn_split s " +
    "JOIN txn t ON t.id = s.txnId " +
    "JOIN account a ON a.id = t.accountId " +
    "LEFT JOIN category c ON c.id = s.categoryId " +
    "LEFT JOIN category p ON p.id = c.parentId " +
    "WHERE t.deletedAtMillis IS NULL AND t.type = :type " +
    "AND t.dateMillis >= :fromMillis AND t.dateMillis < :toMillis "

@Dao
interface TransactionDao {

    @Query(UNSPLIT_DATED + "UNION ALL " + SPLIT_DATED)
    fun observeDatedRows(type: String, fromMillis: Long, toMillis: Long): Flow<List<DatedAmountRow>>

    /**
     * Every balance movement before [toMillis], both legs of transfers included. Replaying these
     * against each account's opening balance reconstructs history exactly, which is why net worth
     * over time needs no snapshot table and works retroactively over data entered before the
     * feature existed.
     */
    @Query(
        "SELECT t.accountId AS accountId, a.currencyCode AS currencyCode, " +
            "t.dateMillis AS dateMillis, " +
            "CASE WHEN t.type = 'INCOME' THEN t.amountMinor ELSE -t.amountMinor END AS deltaMinor " +
            "FROM txn t JOIN account a ON a.id = t.accountId " +
            "WHERE t.deletedAtMillis IS NULL AND t.dateMillis < :toMillis " +
            "UNION ALL " +
            "SELECT t.toAccountId AS accountId, b.currencyCode AS currencyCode, " +
            "t.dateMillis AS dateMillis, t.toAmountMinor AS deltaMinor " +
            "FROM txn t JOIN account b ON b.id = t.toAccountId " +
            "WHERE t.deletedAtMillis IS NULL AND t.toAccountId IS NOT NULL " +
            "AND t.toAmountMinor IS NOT NULL AND t.dateMillis < :toMillis"
    )
    fun observeBalanceEffects(toMillis: Long): Flow<List<BalanceEffect>>

    @Query(DETAIL_SELECT + "WHERE t.deletedAtMillis IS NULL ORDER BY t.dateMillis DESC, t.id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionDetail>>

    @Query(
        DETAIL_SELECT +
            "WHERE t.deletedAtMillis IS NULL " +
            "AND t.dateMillis >= :fromMillis AND t.dateMillis < :toMillis " +
            "ORDER BY t.dateMillis DESC, t.id DESC"
    )
    fun observeInRange(fromMillis: Long, toMillis: Long): Flow<List<TransactionDetail>>

    /**
     * One query serves every filter combination: each parameter short-circuits to "no filter" when
     * null (or empty, for the text search), which beats assembling SQL by hand at call sites.
     * An account filter matches either leg of a transfer so money moving in shows up too.
     */
    @Query(
        DETAIL_SELECT +
            "WHERE t.deletedAtMillis IS NULL " +
            "AND t.dateMillis >= :fromMillis AND t.dateMillis < :toMillis " +
            "AND (:accountId IS NULL OR t.accountId = :accountId OR t.toAccountId = :accountId) " +
            // A category filter must also match a split leg, or splitting a receipt would hide it
            // from the very category it was split into.
            "AND (:categoryId IS NULL OR t.categoryId = :categoryId OR c.parentId = :categoryId " +
            "     OR EXISTS (SELECT 1 FROM txn_split s LEFT JOIN category sc ON sc.id = s.categoryId " +
            "                WHERE s.txnId = t.id " +
            "                AND (s.categoryId = :categoryId OR sc.parentId = :categoryId))) " +
            "AND (:tagId IS NULL OR EXISTS (SELECT 1 FROM txn_tag tt " +
            "                               WHERE tt.txnId = t.id AND tt.tagId = :tagId)) " +
            "AND (:type IS NULL OR t.type = :type) " +
            "AND (:query = '' OR t.payee LIKE '%' || :query || '%' OR t.note LIKE '%' || :query || '%' " +
            "     OR c.name LIKE '%' || :query || '%') " +
            "AND (:minMinor IS NULL OR t.amountMinor >= :minMinor) " +
            "AND (:maxMinor IS NULL OR t.amountMinor <= :maxMinor) " +
            "ORDER BY t.dateMillis DESC, t.id DESC"
    )
    fun observeFiltered(
        fromMillis: Long,
        toMillis: Long,
        accountId: Long?,
        categoryId: Long?,
        tagId: Long?,
        type: String?,
        query: String,
        minMinor: Long?,
        maxMinor: Long?
    ): Flow<List<TransactionDetail>>

    @Query(DETAIL_SELECT + "WHERE t.id = :id")
    suspend fun detailById(id: Long): TransactionDetail?

    @Query("SELECT * FROM txn WHERE id = :id")
    suspend fun byId(id: Long): Transaction?

    /** Category totals for one window and one direction, grouped by top-level category. */
    @Query(UNSPLIT_AMOUNTS + "UNION ALL " + SPLIT_AMOUNTS)
    fun observeAmountRows(type: String, fromMillis: Long, toMillis: Long): Flow<List<AmountRow>>

    /**
     * Same as [observeAmountRows] but one-shot, and optionally narrowed to a category subtree.
     * This is what budgets are measured against, so the category filter has to reach split legs
     * too - otherwise splitting a supermarket receipt would quietly drop it out of its budget.
     */
    @Query(
        UNSPLIT_AMOUNTS +
            "AND (:categoryId IS NULL OR t.categoryId = :categoryId OR c.parentId = :categoryId) " +
            "UNION ALL " +
            SPLIT_AMOUNTS +
            "AND (:categoryId IS NULL OR s.categoryId = :categoryId OR c.parentId = :categoryId)"
    )
    suspend fun amountRows(type: String, fromMillis: Long, toMillis: Long, categoryId: Long?): List<AmountRow>

    /** Distinct payees for entry autocomplete, most recently used first. */
    @Query(
        "SELECT payee FROM txn " +
            "WHERE deletedAtMillis IS NULL AND payee != '' AND payee LIKE :prefix || '%' " +
            "GROUP BY payee ORDER BY MAX(dateMillis) DESC LIMIT 6"
    )
    suspend fun payeeSuggestions(prefix: String): List<String>

    /** The category last used for this payee - powers "type Lidl, get Groceries". */
    @Query(
        "SELECT categoryId FROM txn " +
            "WHERE deletedAtMillis IS NULL AND payee = :payee AND categoryId IS NOT NULL " +
            "ORDER BY dateMillis DESC LIMIT 1"
    )
    suspend fun lastCategoryForPayee(payee: String): Long?

    @Query("SELECT MIN(dateMillis) FROM txn WHERE deletedAtMillis IS NULL")
    suspend fun earliestDateMillis(): Long?

    // --- Backup, restore and export -----------------------------------------------------------

    /**
     * Everything, soft-deleted rows included. A backup that quietly dropped them would resurrect
     * deleted transactions on the next restore, because their ids would be free to reuse.
     */
    @Query("SELECT * FROM txn ORDER BY id ASC")
    suspend fun all(): List<Transaction>

    /** Joined rows for CSV export, live entries only, oldest first so the file reads chronologically. */
    @Query(DETAIL_SELECT + "WHERE t.deletedAtMillis IS NULL ORDER BY t.dateMillis ASC, t.id ASC")
    suspend fun allDetails(): List<TransactionDetail>

    @Insert
    suspend fun insertAll(transactions: List<Transaction>)

    @Query("DELETE FROM txn")
    suspend fun clear()

    @Insert
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction)

    @Query("UPDATE txn SET deletedAtMillis = :nowMillis WHERE id = :id")
    suspend fun softDelete(id: Long, nowMillis: Long)

    @Query("UPDATE txn SET deletedAtMillis = NULL WHERE id = :id")
    suspend fun restore(id: Long)
}
