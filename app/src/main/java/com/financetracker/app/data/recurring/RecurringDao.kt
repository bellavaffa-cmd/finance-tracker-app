package com.financetracker.app.data.recurring

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

private const val RULE_SELECT = "SELECT r.id, r.name, r.type, r.accountId, " +
    "a.name AS accountName, a.currencyCode AS accountCurrency, " +
    "r.toAccountId, b.name AS toAccountName, " +
    "r.categoryId, c.name AS categoryName, c.colorArgb AS colorArgb, " +
    "r.amountMinor, r.toAmountMinor, r.payee, r.note, r.frequency, r.interval, " +
    "r.anchorDayOfMonth, r.nextDueMillis, r.endDateMillis, r.autoPost, r.isActive " +
    "FROM recurring_rule r " +
    "JOIN account a ON a.id = r.accountId " +
    "LEFT JOIN account b ON b.id = r.toAccountId " +
    "LEFT JOIN category c ON c.id = r.categoryId "

@Dao
interface RecurringDao {

    @Query(RULE_SELECT + "ORDER BY r.isActive DESC, r.nextDueMillis ASC")
    fun observeAll(): Flow<List<RecurringRuleDetail>>

    /** Active rules whose next occurrence has come due and still needs your confirmation. */
    @Query(
        RULE_SELECT +
            "WHERE r.isActive = 1 AND r.autoPost = 0 AND r.nextDueMillis <= :nowMillis " +
            "AND (r.endDateMillis IS NULL OR r.nextDueMillis <= r.endDateMillis) " +
            "ORDER BY r.nextDueMillis ASC"
    )
    fun observeDueForConfirmation(nowMillis: Long): Flow<List<RecurringRuleDetail>>

    /** Total of the active monthly-equivalent commitments, for the "subscriptions" summary. */
    @Query(RULE_SELECT + "WHERE r.isActive = 1 ORDER BY r.nextDueMillis ASC")
    fun observeActive(): Flow<List<RecurringRuleDetail>>

    @Query("SELECT * FROM recurring_rule WHERE isActive = 1 AND nextDueMillis <= :nowMillis")
    suspend fun dueRules(nowMillis: Long): List<RecurringRule>

    @Query("SELECT * FROM recurring_rule WHERE id = :id")
    suspend fun byId(id: Long): RecurringRule?

    @Insert
    suspend fun insert(rule: RecurringRule): Long

    @Query("SELECT * FROM recurring_rule ORDER BY id ASC")
    suspend fun all(): List<RecurringRule>

    @Insert
    suspend fun insertAll(rules: List<RecurringRule>)

    @Query("DELETE FROM recurring_rule")
    suspend fun clear()

    @Update
    suspend fun update(rule: RecurringRule)

    @Delete
    suspend fun delete(rule: RecurringRule)

    @Query("DELETE FROM recurring_rule WHERE id = :id")
    suspend fun deleteById(id: Long)
}
