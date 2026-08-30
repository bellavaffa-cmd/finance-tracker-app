package com.financetracker.app.data.rules

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PayeeRuleDao {

    @Query("SELECT * FROM payee_rule ORDER BY priority ASC, LENGTH(pattern) DESC, id ASC")
    fun observeAll(): Flow<List<PayeeRule>>

    @Query("SELECT * FROM payee_rule WHERE isActive = 1")
    suspend fun active(): List<PayeeRule>

    @Query("SELECT * FROM payee_rule ORDER BY id ASC")
    suspend fun all(): List<PayeeRule>

    @Query("SELECT COALESCE(MAX(priority), -1) FROM payee_rule")
    suspend fun highestPriority(): Int

    /**
     * Payees already filed consistently, for suggesting rules. Transfers are excluded because they
     * have no category to learn from, and deleted rows must not teach anything.
     */
    @Query(
        "SELECT t.payee AS payee, t.categoryId AS categoryId, c.name AS categoryName, " +
            "COUNT(*) AS count " +
            "FROM txn t JOIN category c ON c.id = t.categoryId " +
            "WHERE t.deletedAtMillis IS NULL AND t.payee != '' AND t.categoryId IS NOT NULL " +
            "AND t.type != 'TRANSFER' " +
            "GROUP BY t.payee, t.categoryId " +
            "ORDER BY COUNT(*) DESC LIMIT 50"
    )
    suspend fun payeeHistory(): List<PayeeCategoryCount>

    /** Live entries with a payee but no category - what a retroactive run would touch. */
    @Query(
        "SELECT id, payee FROM txn " +
            "WHERE deletedAtMillis IS NULL AND payee != '' AND categoryId IS NULL " +
            "AND type != 'TRANSFER'"
    )
    suspend fun uncategorisedWithPayee(): List<UncategorisedRow>

    @Query("UPDATE txn SET categoryId = :categoryId WHERE id = :id")
    suspend fun setCategory(id: Long, categoryId: Long)

    @Insert
    suspend fun insert(rule: PayeeRule): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<PayeeRule>)

    @Update
    suspend fun update(rule: PayeeRule)

    @Delete
    suspend fun delete(rule: PayeeRule)

    @Query("DELETE FROM payee_rule")
    suspend fun clear()
}

data class UncategorisedRow(val id: Long, val payee: String)
