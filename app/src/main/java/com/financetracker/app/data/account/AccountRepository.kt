package com.financetracker.app.data.account

import com.financetracker.app.data.Money
import com.financetracker.app.data.settings.CurrencyRate
import com.financetracker.app.data.settings.CurrencyRateDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Inputs for reconstructing past balances. */
data class HistoryInputs(
    val openingBalances: Map<Long, Long>,
    val rates: Map<String, Double>
)

class AccountRepository(
    private val dao: AccountDao,
    private val rateDao: CurrencyRateDao
) {
    val accountsWithBalances: Flow<List<AccountWithBalance>> = dao.observeWithBalances()

    val activeAccounts: Flow<List<AccountWithBalance>> =
        dao.observeWithBalances().map { list -> list.filter { !it.isArchived } }

    val rates: Flow<List<CurrencyRate>> = rateDao.observeAll()

    /**
     * The two things a historical reconstruction needs that a computed balance cannot supply:
     * where each account started, and what its currency is worth now. Emitted together so callers
     * do not have to spend two slots of a `combine` on them.
     */
    val historyInputs: Flow<HistoryInputs> = combine(dao.observeAll(), rates) { accounts, rateList ->
        HistoryInputs(
            openingBalances = accounts.associate { it.id to it.openingBalanceMinor },
            rates = rateList.associate { it.code to it.rateToBase }
        )
    }

    suspend fun byId(id: Long): Account? = dao.byId(id)

    suspend fun count(): Int = dao.count()

    suspend fun transactionCount(id: Long): Int = dao.transactionCount(id)

    suspend fun insert(account: Account): Long = dao.insert(account)

    suspend fun update(account: Account) = dao.update(account)

    suspend fun delete(account: Account) = dao.delete(account)

    /** Rate for [code] against the base currency; base itself is always exactly 1. */
    suspend fun rateToBase(code: String, baseCurrency: String): Double =
        if (code == baseCurrency) 1.0 else rateDao.byCode(code)?.rateToBase ?: 1.0

    suspend fun setRate(code: String, rateToBase: Double) {
        rateDao.upsert(CurrencyRate(code, rateToBase, System.currentTimeMillis()))
    }

    suspend fun deleteRate(code: String) = rateDao.delete(code)

    /**
     * Ensures a rate row exists for every currency in use, so the settings screen lists them
     * without the user having to know which ones matter. New rows default to 1.0, which is
     * deliberately wrong-looking: a net worth that is obviously off prompts you to set the rate,
     * whereas a silently omitted currency would just quietly under-report.
     */
    suspend fun ensureRatesFor(codes: Collection<String>, baseCurrency: String) {
        val existing = rateDao.all().map { it.code }.toSet()
        codes.filter { it != baseCurrency && it !in existing }.forEach { code ->
            rateDao.upsert(CurrencyRate(code, 1.0, System.currentTimeMillis()))
        }
    }

    companion object {
        /** Net worth in base minor units, valuing each account at the current rate for its currency. */
        fun netWorthMinorBase(
            accounts: List<AccountWithBalance>,
            rates: Map<String, Double>,
            baseCurrency: String
        ): Long = accounts
            .filter { it.includeInNetWorth && !it.isArchived }
            .sumOf { account ->
                val rate = if (account.currencyCode == baseCurrency) 1.0
                else rates[account.currencyCode] ?: 1.0
                Money.toBaseMinor(account.balanceMinor, account.currencyCode, rate, baseCurrency)
            }
    }
}
