package com.financetracker.app.data.forecast

import com.financetracker.app.data.MonthPeriod
import com.financetracker.app.data.recurring.Frequency
import com.financetracker.app.data.recurring.RecurringRepository
import com.financetracker.app.data.recurring.RecurringRule
import com.financetracker.app.data.txn.TxnType

/** A scheduled movement expected before the period ends. */
data class ExpectedItem(
    val name: String,
    val type: TxnType,
    val amountMinorBase: Long,
    val dueMillis: Long
)

data class Forecast(
    val period: MonthPeriod,
    val daysRemaining: Int,
    /** Cash and current accounts only - not savings, and not net worth. */
    val spendableMinorBase: Long,
    /** Recurring income still to arrive before the period ends. */
    val expectedIncomeMinorBase: Long,
    /** Recurring outgoings still to leave before the period ends. */
    val expectedOutgoingsMinorBase: Long,
    /** Everyday spending projected from recent history, excluding anything recurring. */
    val projectedDiscretionaryMinorBase: Long,
    val dailyBurnMinorBase: Long,
    val items: List<ExpectedItem>,
    /** False when there is too little history for the projection to mean anything. */
    val hasEnoughHistory: Boolean
) {
    val projectedEndMinorBase: Long
        get() = spendableMinorBase + expectedIncomeMinorBase -
            expectedOutgoingsMinorBase - projectedDiscretionaryMinorBase

    /** What is left once the bills already committed have gone out. */
    val availableAfterCommitmentsMinorBase: Long
        get() = spendableMinorBase + expectedIncomeMinorBase - expectedOutgoingsMinorBase

    /**
     * The most that can be spent per day and still reach the end of the period at zero.
     *
     * Only meaningful as a *limit*: quoted when the current rate would run short, where it says
     * how much to cut back to. Offered when there is money spare it would read as an instruction
     * to spend down to nothing by payday, which is not advice this app should give.
     */
    val budgetPerDayMinorBase: Long
        get() = if (daysRemaining <= 0) availableAfterCommitmentsMinorBase
        else availableAfterCommitmentsMinorBase / daysRemaining

    val willGoNegative: Boolean get() = projectedEndMinorBase < 0

    /** How far short the period ends, when it does. */
    val shortfallMinorBase: Long get() = if (willGoNegative) -projectedEndMinorBase else 0
}

/**
 * Projects the rest of the current period.
 *
 * The one thing this has to avoid is counting the same money twice. Recurring commitments are added
 * from the schedule, so transactions those rules already created must be excluded from the spending
 * average - otherwise rent is both averaged into the daily burn *and* added again as an upcoming
 * bill, and the forecast comes out far gloomier than reality.
 */
object ForecastEngine {

    /** Below this the average is noise, and a confident projection would be misleading. */
    const val MIN_HISTORY_DAYS = 14

    /** How far back the everyday spending average looks. */
    const val BURN_WINDOW_DAYS = 60

    private const val MILLIS_PER_DAY = 86_400_000L

    /**
     * Recurring items expected between [fromMillis] and the end of [period].
     *
     * Occurrences are walked forward from each rule's next due date, so a weekly rule contributes
     * every occurrence left in the period rather than just the first one.
     */
    fun expectedItems(
        rules: List<RecurringRule>,
        names: Map<Long, String>,
        toBase: (Long, Long) -> Long,
        fromMillis: Long,
        period: MonthPeriod
    ): List<ExpectedItem> {
        val items = mutableListOf<ExpectedItem>()
        for (rule in rules) {
            if (!rule.isActive) continue
            // A transfer moves money between the user's own accounts, so it changes no total.
            if (rule.type == TxnType.TRANSFER) continue

            var due = rule.nextDueMillis
            var guard = 0
            while (due < period.endMillisExclusive && guard < MAX_OCCURRENCES) {
                val end = rule.endDateMillis
                if (end != null && due > end) break
                if (due >= fromMillis) {
                    items += ExpectedItem(
                        name = names[rule.id] ?: rule.name,
                        type = rule.type,
                        amountMinorBase = toBase(rule.amountMinor, rule.id),
                        dueMillis = due
                    )
                }
                due = RecurringRepository.nextOccurrence(rule, due)
                guard++
                // Daily rules in a long period would otherwise generate a listing nobody reads.
                if (rule.frequency == Frequency.DAILY && guard > 31) break
            }
        }
        return items.sortedBy { it.dueMillis }
    }

    /**
     * Everyday spending per day, from non-recurring expenses only.
     *
     * [observedDays] is capped at the age of the ledger, so a two-week-old app does not divide a
     * fortnight of spending across sixty days and report a reassuringly tiny burn rate.
     */
    fun dailyBurn(nonRecurringSpendMinorBase: Long, observedDays: Int): Long {
        val days = observedDays.coerceAtLeast(1)
        return nonRecurringSpendMinorBase / days
    }

    fun build(
        period: MonthPeriod,
        nowMillis: Long,
        spendableMinorBase: Long,
        rules: List<RecurringRule>,
        ruleNames: Map<Long, String>,
        ruleToBase: (Long, Long) -> Long,
        nonRecurringSpendMinorBase: Long,
        observedDays: Int
    ): Forecast {
        val daysRemaining = daysBetween(nowMillis, period.endMillisExclusive)
        val items = expectedItems(rules, ruleNames, ruleToBase, nowMillis, period)

        val income = items.filter { it.type == TxnType.INCOME }.sumOf { it.amountMinorBase }
        val outgoings = items.filter { it.type == TxnType.EXPENSE }.sumOf { it.amountMinorBase }

        val burn = dailyBurn(nonRecurringSpendMinorBase, observedDays)

        return Forecast(
            period = period,
            daysRemaining = daysRemaining,
            spendableMinorBase = spendableMinorBase,
            expectedIncomeMinorBase = income,
            expectedOutgoingsMinorBase = outgoings,
            projectedDiscretionaryMinorBase = burn * daysRemaining,
            dailyBurnMinorBase = burn,
            items = items,
            hasEnoughHistory = observedDays >= MIN_HISTORY_DAYS
        )
    }

    /** Whole days from [fromMillis] to [toMillis], never negative. */
    fun daysBetween(fromMillis: Long, toMillis: Long): Int =
        ((toMillis - fromMillis).coerceAtLeast(0) / MILLIS_PER_DAY).toInt()

    private const val MAX_OCCURRENCES = 400
}
