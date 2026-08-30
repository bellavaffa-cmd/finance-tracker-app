package com.financetracker.app.data.debt

import com.financetracker.app.data.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Both strategies run over the same debts, so they can be compared side by side. */
data class StrategyComparison(
    val snowball: StrategyResult,
    val avalanche: StrategyResult
) {
    /** Interest avalanche saves over snowball. Never negative - avalanche is optimal by definition. */
    val interestSavedMinor: Long
        get() = (snowball.totalInterestMinor - avalanche.totalInterestMinor).coerceAtLeast(0)

    val monthsSaved: Int get() = (snowball.months - avalanche.months).coerceAtLeast(0)

    /**
     * True only when the two strategies produce the same *outcome*.
     *
     * Comparing the clearing order is not enough: with a small low-rate debt and a large high-rate
     * one, both strategies clear them in the same sequence while taking very different routes there,
     * so an order comparison would claim they agree while one of them quietly costs more interest.
     */
    val strategiesAgree: Boolean
        get() = interestSavedMinor == 0L && monthsSaved == 0

    val usable: Boolean get() = !snowball.neverClears && !avalanche.neverClears
}

class DebtRepository(private val dao: DebtDao) {

    val allDebts: Flow<List<Debt>> = dao.observeAll()

    /** Only what you owe can be paid down, so anything owed to you is excluded from strategies. */
    val payableDebts: Flow<List<Debt>> = dao.observeAll().map { list ->
        list.filter { it.isActive && it.kind == DebtKind.OWED_BY_ME && it.balanceMinor > 0 }
    }

    suspend fun all(): List<Debt> = dao.all()

    suspend fun byId(id: Long): Debt? = dao.byId(id)

    suspend fun insert(debt: Debt): Long = dao.insert(debt)

    suspend fun update(debt: Debt) = dao.update(debt)

    suspend fun delete(debt: Debt) = dao.delete(debt)

    /** Records a payment by reducing the balance, closing the debt when it reaches zero. */
    suspend fun applyPayment(debtId: Long, amountMinor: Long) {
        val debt = dao.byId(debtId) ?: return
        val remaining = (debt.balanceMinor - amountMinor).coerceAtLeast(0)
        dao.update(debt.copy(balanceMinor = remaining, isActive = remaining > 0))
    }

    companion object {
        /**
         * Total owed, converted to the base currency at [rates]. Debts owed *to* you are counted
         * positively and ones you owe negatively, so the figure reads as a net position.
         */
        fun netPositionMinorBase(
            debts: List<Debt>,
            rates: Map<String, Double>,
            baseCurrency: String
        ): Long = debts.filter { it.isActive }.sumOf { debt ->
            val rate = if (debt.currencyCode == baseCurrency) 1.0 else rates[debt.currencyCode] ?: 1.0
            val inBase = Money.toBaseMinor(debt.balanceMinor, debt.currencyCode, rate, baseCurrency)
            if (debt.kind == DebtKind.OWED_TO_ME) inBase else -inBase
        }

        /**
         * Runs both strategies. Mixed currencies are compared in their raw minor units, which is
         * only meaningful when the debts share a currency - the screen says so rather than silently
         * adding pounds to euros.
         */
        fun compare(debts: List<Debt>, extraMonthlyMinor: Long): StrategyComparison {
            val snapshots = debts.map { it.toSnapshot() }
            return StrategyComparison(
                snowball = DebtCalculator.simulate(snapshots, extraMonthlyMinor, PayoffStrategy.SNOWBALL),
                avalanche = DebtCalculator.simulate(snapshots, extraMonthlyMinor, PayoffStrategy.AVALANCHE)
            )
        }
    }
}
