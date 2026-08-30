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
 */
private const val AMOUNT_SELECT = "SELECT t.categoryId AS categoryId, " +
    "COALESCE(p.name, c.name) AS categoryName, " +
    "COALESCE(p.colorArgb, c.colorArgb) AS colorArgb, " +
    "t.amountMinor AS amountMinor, a.currencyCode AS currencyCode, " +
    "t.fxRateToBase AS fxRateToBase " +
    "FROM txn t " +
    "JOIN account a ON a.id = t.accountId " +
    "LEFT JOIN category c ON c.id = t.categoryId " +
    "LEFT JOIN category p ON p.id = c.parentId "

@Dao
interface TransactionDao {

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
            "AND (:categoryId IS NULL OR t.categoryId = :categoryId OR c.parentId = :categoryId) " +
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
    @Query(
        AMOUNT_SELECT +
            "WHERE t.deletedAtMillis IS NULL AND t.type = :type " +
            "AND t.dateMillis >= :fromMillis AND t.dateMillis < :toMillis"
    )
    fun observeAmountRows(type: String, fromMillis: Long, toMillis: Long): Flow<List<AmountRow>>

    /** Same as [observeAmountRows] but one-shot, and optionally narrowed to a category subtree. */
    @Query(
        AMOUNT_SELECT +
            "WHERE t.deletedAtMillis IS NULL AND t.type = :type " +
            "AND t.dateMillis >= :fromMillis AND t.dateMillis < :toMillis " +
            "AND (:categoryId IS NULL OR t.categoryId = :categoryId OR c.parentId = :categoryId)"
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

    @Insert
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction)

    @Query("UPDATE txn SET deletedAtMillis = :nowMillis WHERE id = :id")
    suspend fun softDelete(id: Long, nowMillis: Long)

    @Query("UPDATE txn SET deletedAtMillis = NULL WHERE id = :id")
    suspend fun restore(id: Long)
}
