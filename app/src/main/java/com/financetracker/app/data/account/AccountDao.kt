package com.financetracker.app.data.account

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    /**
     * Balance = opening balance, minus everything that left the account (expenses and the outgoing
     * leg of transfers), plus everything that arrived (income, and the incoming leg of transfers
     * credited at the destination's own amount so cross-currency transfers stay balanced).
     */
    @Query(
        """
        SELECT a.id, a.name, a.type, a.currencyCode, a.colorArgb, a.includeInNetWorth,
               a.isArchived, a.sortOrder,
               a.openingBalanceMinor
                 + IFNULL((SELECT SUM(CASE WHEN t.type = 'INCOME' THEN t.amountMinor
                                           ELSE -t.amountMinor END)
                           FROM txn t
                           WHERE t.accountId = a.id AND t.deletedAtMillis IS NULL), 0)
                 + IFNULL((SELECT SUM(t.toAmountMinor)
                           FROM txn t
                           WHERE t.toAccountId = a.id AND t.deletedAtMillis IS NULL), 0)
               AS balanceMinor
        FROM account a
        ORDER BY a.isArchived ASC, a.sortOrder ASC, a.id ASC
        """
    )
    fun observeWithBalances(): Flow<List<AccountWithBalance>>

    @Query("SELECT * FROM account ORDER BY isArchived ASC, sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<Account>>

    @Query("SELECT * FROM account WHERE id = :id")
    suspend fun byId(id: Long): Account?

    @Query("SELECT COUNT(*) FROM account")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM txn WHERE (accountId = :id OR toAccountId = :id) AND deletedAtMillis IS NULL")
    suspend fun transactionCount(id: Long): Int

    @Insert
    suspend fun insert(account: Account): Long

    @Update
    suspend fun update(account: Account)

    @Delete
    suspend fun delete(account: Account)
}
