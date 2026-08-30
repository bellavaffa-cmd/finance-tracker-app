package com.financetracker.app.data.budget

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Query("SELECT * FROM budget WHERE isActive = 1")
    fun observeActive(): Flow<List<Budget>>

    @Query("SELECT * FROM budget")
    fun observeAll(): Flow<List<Budget>>

    @Query("SELECT * FROM budget WHERE categoryId IS :categoryId LIMIT 1")
    suspend fun forCategory(categoryId: Long?): Budget?

    @Query("DELETE FROM budget WHERE categoryId = :categoryId")
    suspend fun deleteForCategory(categoryId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: Budget): Long

    @Query("SELECT * FROM budget ORDER BY id ASC")
    suspend fun all(): List<Budget>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(budgets: List<Budget>)

    @Query("DELETE FROM budget")
    suspend fun clear()

    @Update
    suspend fun update(budget: Budget)

    @Delete
    suspend fun delete(budget: Budget)
}
