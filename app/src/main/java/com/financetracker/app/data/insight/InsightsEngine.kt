package com.financetracker.app.data.insight

import com.financetracker.app.data.Money
import com.financetracker.app.data.MonthPeriod
import com.financetracker.app.data.account.AccountWithBalance
import com.financetracker.app.data.txn.BalanceEffect
import com.financetracker.app.data.txn.DatedAmountRow

/** One month of the trend chart. */
data class MonthlyFlow(
    val period: MonthPeriod,
    val incomeMinorBase: Long,
    val expenseMinorBase: Long
) {
    val netMinorBase: Long get() = incomeMinorBase - expenseMinorBase
}

/** Net worth at the close of one month. */
data class NetWorthPoint(
    val period: MonthPeriod,
    val netWorthMinorBase: Long
)

/** A category spending unusually far from its own recent norm. */
data class SpendingAnomaly(
    val categoryId: Long?,
    val categoryName: String,
    val colorArgb: Int,
    val thisMonthMinorBase: Long,
    val averageMinorBase: Long,
    val percentChange: Int
) {
    val isIncrease: Boolean get() = percentChange > 0
    val differenceMinorBase: Long get() = kotlin.math.abs(thisMonthMinorBase - averageMinorBase)
}

/**
 * Derives the longer-range views from the same ledger everything else reads.
 *
 * All of it is computed rather than stored. Net worth history in particular replays balance
 * movements against opening balances instead of relying on monthly snapshots, which means it works
 * over data entered long before this screen existed and can never disagree with the account
 * balances shown elsewhere.
 */
object InsightsEngine {

    /** Months of history a category needs before its average is worth comparing against. */
    const val MIN_MONTHS_FOR_ANOMALY = 3

    /** Below this the noise in a small category produces alarming percentages about pennies. */
    private const val MIN_ABSOLUTE_DIFFERENCE_MINOR = 1000L

    private const val ANOMALY_THRESHOLD_PERCENT = 35

    /** Buckets dated rows into the given periods, converting each at its own frozen rate. */
    fun monthlyTotals(
        rows: List<DatedAmountRow>,
        periods: List<MonthPeriod>,
        baseCurrency: String
    ): Map<Int, Long> {
        val byOrdinal = periods.associateBy { it.ordinal }
        val totals = mutableMapOf<Int, Long>()
        for (row in rows) {
            val period = periods.firstOrNull { it.contains(row.dateMillis) } ?: continue
            val key = period.ordinal
            if (key !in byOrdinal) continue
            totals[key] = (totals[key] ?: 0L) +
                Money.toBaseMinor(row.amountMinor, row.currencyCode, row.fxRateToBase, baseCurrency)
        }
        return totals
    }

    fun trend(
        incomeRows: List<DatedAmountRow>,
        expenseRows: List<DatedAmountRow>,
        periods: List<MonthPeriod>,
        baseCurrency: String
    ): List<MonthlyFlow> {
        val income = monthlyTotals(incomeRows, periods, baseCurrency)
        val expense = monthlyTotals(expenseRows, periods, baseCurrency)
        return periods.map { period ->
            MonthlyFlow(
                period = period,
                incomeMinorBase = income[period.ordinal] ?: 0L,
                expenseMinorBase = expense[period.ordinal] ?: 0L
            )
        }
    }

    /**
     * Net worth at the end of each period.
     *
     * Every account's balance is rolled forward from its opening balance through the movements that
     * happened up to each month end, then converted at the *current* rate. Historical rates are not
     * kept per currency - only per transaction - so a past net worth is stated in today's money.
     * That is the honest reading of the data available, and it keeps the newest point identical to
     * the net worth on the dashboard.
     */
    fun netWorthHistory(
        effects: List<BalanceEffect>,
        accounts: List<AccountWithBalance>,
        /** Each account's balance before its first transaction, keyed by account id. */
        openingBalances: Map<Long, Long>,
        periods: List<MonthPeriod>,
        rates: Map<String, Double>,
        baseCurrency: String
    ): List<NetWorthPoint> {
        val counted = accounts.filter { it.includeInNetWorth }
        if (counted.isEmpty() || periods.isEmpty()) return emptyList()

        val countedIds = counted.map { it.id }.toSet()
        val relevant = effects.filter { it.accountId in countedIds }.sortedBy { it.dateMillis }
        val opening = counted.associate { it.id to (openingBalances[it.id] ?: 0L) }
        val currencyOf = counted.associate { it.id to it.currencyCode }

        val running = opening.toMutableMap()
        var index = 0

        return periods.map { period ->
            // Effects are pre-sorted, so the whole history is walked once rather than re-scanned
            // for every month.
            while (index < relevant.size && relevant[index].dateMillis < period.endMillisExclusive) {
                val effect = relevant[index]
                running[effect.accountId] = (running[effect.accountId] ?: 0L) + effect.deltaMinor
                index++
            }
            val total = running.entries.sumOf { (accountId, balance) ->
                val code = currencyOf[accountId] ?: baseCurrency
                val rate = if (code == baseCurrency) 1.0 else rates[code] ?: 1.0
                Money.toBaseMinor(balance, code, rate, baseCurrency)
            }
            NetWorthPoint(period, total)
        }
    }

    /**
     * Categories whose current-month spending sits well away from their own recent average.
     *
     * Two guards keep this from crying wolf: a category needs [MIN_MONTHS_FOR_ANOMALY] months of
     * history before it has a norm worth comparing against, and the change has to be material in
     * cash as well as in percent - otherwise a category that went from 40p to £1 reports a
     * terrifying 150% rise about 60p.
     *
     * The current month is deliberately excluded from its own average, or every comparison would be
     * partly against itself and would understate the difference.
     */
    fun anomalies(
        expenseRows: List<DatedAmountRow>,
        current: MonthPeriod,
        historyPeriods: List<MonthPeriod>,
        baseCurrency: String
    ): List<SpendingAnomaly> {
        val past = historyPeriods.filter { it.ordinal < current.ordinal }
        if (past.size < MIN_MONTHS_FOR_ANOMALY) return emptyList()

        val grouped = expenseRows.groupBy { it.categoryId }
        val result = mutableListOf<SpendingAnomaly>()

        for ((categoryId, rows) in grouped) {
            val thisMonth = rows
                .filter { current.contains(it.dateMillis) }
                .sumOf { Money.toBaseMinor(it.amountMinor, it.currencyCode, it.fxRateToBase, baseCurrency) }

            val monthly = past.map { period ->
                rows.filter { period.contains(it.dateMillis) }
                    .sumOf { Money.toBaseMinor(it.amountMinor, it.currencyCode, it.fxRateToBase, baseCurrency) }
            }

            // The average must start at the category's first activity, not at the start of the
            // window. Months before a category was ever used are not part of its norm, and
            // averaging them in drags it down far enough to flag perfectly steady spending as a
            // spike - which is exactly what a new app, or a newly created category, would do to
            // every category at once.
            val firstActive = monthly.indexOfFirst { it > 0 }
            if (firstActive < 0) continue

            // Interior zeros are kept: a quarterly bill genuinely averages lower than a monthly one.
            val history = monthly.subList(firstActive, monthly.size)
            if (history.size < MIN_MONTHS_FOR_ANOMALY) continue

            val average = history.sum() / history.size
            if (average <= 0) continue

            val difference = thisMonth - average
            if (kotlin.math.abs(difference) < MIN_ABSOLUTE_DIFFERENCE_MINOR) continue

            val percent = Math.round(difference * 100.0 / average).toInt()
            if (kotlin.math.abs(percent) < ANOMALY_THRESHOLD_PERCENT) continue

            val sample = rows.firstOrNull()
            result += SpendingAnomaly(
                categoryId = categoryId,
                categoryName = sample?.categoryName ?: "Uncategorised",
                colorArgb = sample?.colorArgb ?: UNCATEGORISED_COLOR,
                thisMonthMinorBase = thisMonth,
                averageMinorBase = average,
                percentChange = percent
            )
        }

        // Biggest cash difference first: a large overspend matters more than a large percentage.
        return result.sortedByDescending { it.differenceMinorBase }
    }

    private const val UNCATEGORISED_COLOR = 0xFF6B7688.toInt()
}
