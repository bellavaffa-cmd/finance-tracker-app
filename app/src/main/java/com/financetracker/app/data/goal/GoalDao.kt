package com.financetracker.app.data.goal

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {

    @Query("SELECT * FROM goal ORDER BY isArchived ASC, createdAtMillis ASC")
    fun observeAll(): Flow<List<Goal>>

    @Query("SELECT * FROM goal ORDER BY id ASC")
    suspend fun all(): List<Goal>

    @Query("SELECT * FROM goal WHERE id = :id")
    suspend fun byId(id: Long): Goal?

    @Insert
    suspend fun insert(goal: Goal): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(goals: List<Goal>)

    @Update
    suspend fun update(goal: Goal)

    @Delete
    suspend fun delete(goal: Goal)

    @Query("DELETE FROM goal")
    suspend fun clear()
}
