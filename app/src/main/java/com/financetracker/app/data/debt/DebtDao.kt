package com.financetracker.app.data.debt

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtDao {

    @Query("SELECT * FROM debt ORDER BY isActive DESC, balanceMinor DESC")
    fun observeAll(): Flow<List<Debt>>

    @Query("SELECT * FROM debt ORDER BY id ASC")
    suspend fun all(): List<Debt>

    @Query("SELECT * FROM debt WHERE id = :id")
    suspend fun byId(id: Long): Debt?

    @Insert
    suspend fun insert(debt: Debt): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(debts: List<Debt>)

    @Update
    suspend fun update(debt: Debt)

    @Delete
    suspend fun delete(debt: Debt)

    @Query("DELETE FROM debt")
    suspend fun clear()
}
