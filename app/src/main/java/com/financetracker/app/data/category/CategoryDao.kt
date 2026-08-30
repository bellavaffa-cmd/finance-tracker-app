package com.financetracker.app.data.category

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM category ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM category WHERE kind = :kind AND isArchived = 0 ORDER BY sortOrder ASC, name ASC")
    fun observeByKind(kind: CategoryKind): Flow<List<Category>>

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun byId(id: Long): Category?

    @Query("SELECT COUNT(*) FROM category")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM txn WHERE categoryId = :id AND deletedAtMillis IS NULL")
    suspend fun transactionCount(id: Long): Int

    @Query("SELECT COUNT(*) FROM category WHERE parentId = :id")
    suspend fun childCount(id: Long): Int

    /** Re-parents a deleted category's children to top level rather than orphaning them. */
    @Query("UPDATE category SET parentId = NULL WHERE parentId = :id")
    suspend fun detachChildren(id: Long)

    @Query("UPDATE txn SET categoryId = NULL WHERE categoryId = :id")
    suspend fun clearCategoryOnTransactions(id: Long)

    @Insert
    suspend fun insert(category: Category): Long

    @Insert
    suspend fun insertAll(categories: List<Category>): List<Long>

    @Query("SELECT * FROM category ORDER BY id ASC")
    suspend fun all(): List<Category>

    @Query("DELETE FROM category")
    suspend fun clear()

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)
}
