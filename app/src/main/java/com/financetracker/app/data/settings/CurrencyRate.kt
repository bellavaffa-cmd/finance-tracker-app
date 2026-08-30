package com.financetracker.app.data.settings

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * The *current* rate for a currency, used for two things only: valuing account balances on the
 * net-worth line, and pre-filling the rate when you enter a new transaction.
 *
 * Rates are entered by hand and never fetched, which keeps the app fully offline and means no
 * background job can silently rewrite what your net worth was yesterday. Historical reporting does
 * not read this table at all - it uses the rate frozen on each transaction.
 */
@Entity(tableName = "currency_rate")
data class CurrencyRate(
    @PrimaryKey val code: String,
    /** How many major units of the base currency one major unit of [code] buys. */
    val rateToBase: Double,
    val updatedAtMillis: Long
)

@Dao
interface CurrencyRateDao {

    @Query("SELECT * FROM currency_rate ORDER BY code ASC")
    fun observeAll(): Flow<List<CurrencyRate>>

    @Query("SELECT * FROM currency_rate WHERE code = :code")
    suspend fun byCode(code: String): CurrencyRate?

    @Query("SELECT * FROM currency_rate")
    suspend fun all(): List<CurrencyRate>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rate: CurrencyRate)

    @Query("DELETE FROM currency_rate WHERE code = :code")
    suspend fun delete(code: String)
}
