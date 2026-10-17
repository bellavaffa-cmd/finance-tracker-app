package com.financetracker.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {

    @Query("SELECT * FROM accounts ORDER BY archived, name COLLATE NOCASE")
    fun accounts(): Flow<List<Account>>

    @Query("SELECT * FROM categories ORDER BY kind, name COLLATE NOCASE")
    fun categories(): Flow<List<Category>>

    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun transactions(): Flow<List<Txn>>

    @Query("SELECT * FROM budgets")
    fun budgets(): Flow<List<Budget>>

    @Query("SELECT * FROM accounts") suspend fun allAccounts(): List<Account>
    @Query("SELECT * FROM categories") suspend fun allCategories(): List<Category>
    @Query("SELECT * FROM transactions") suspend fun allTransactions(): List<Txn>
    @Query("SELECT * FROM budgets") suspend fun allBudgets(): List<Budget>

    @Insert suspend fun insert(account: Account): Long
    @Update suspend fun update(account: Account)
    @Query("DELETE FROM accounts WHERE id = :id") suspend fun deleteAccount(id: Long)
    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :id OR toAccountId = :id")
    suspend fun countForAccount(id: Long): Int

    @Insert suspend fun insert(category: Category): Long
    @Update suspend fun update(category: Category)
    @Query("DELETE FROM categories WHERE id = :id") suspend fun deleteCategory(id: Long)
    @Query("UPDATE transactions SET categoryId = NULL WHERE categoryId = :id") suspend fun uncategorise(id: Long)

    @Insert suspend fun insert(txn: Txn): Long
    @Insert suspend fun insertTxns(txns: List<Txn>)
    @Update suspend fun update(txn: Txn)
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun deleteTxn(id: Long)

    @Upsert suspend fun upsert(budget: Budget)
    @Query("DELETE FROM budgets WHERE categoryId = :categoryId") suspend fun deleteBudget(categoryId: Long)

    @Insert suspend fun insertAccounts(items: List<Account>)
    @Insert suspend fun insertCategories(items: List<Category>)
    @Insert suspend fun insertBudgets(items: List<Budget>)
    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM transactions") suspend fun clearTransactions()
    @Query("DELETE FROM budgets") suspend fun clearBudgets()
}
