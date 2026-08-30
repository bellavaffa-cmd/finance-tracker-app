package com.financetracker.app.data.debt

/** What paying a single debt on a fixed monthly amount works out to. */
data class PayoffProjection(
    val months: Int,
    val totalInterestMinor: Long,
    val totalPaidMinor: Long,
    /** True when the payment does not even cover the interest, so the balance never falls. */
    val neverClears: Boolean = false
)

/** One debt's place in a repayment plan. */
data class PayoffStep(
    val debtId: Long,
    val name: String,
    val clearedInMonth: Int,
    val interestPaidMinor: Long
)

/** The outcome of running one strategy over the whole set of debts. */
data class StrategyResult(
    val strategy: PayoffStrategy,
    val months: Int,
    val totalInterestMinor: Long,
    val order: List<PayoffStep>,
    val neverClears: Boolean = false
)

enum class PayoffStrategy(val label: String, val explanation: String) {
    /** Smallest balance first: slower on paper, but the early wins are what keep people going. */
    SNOWBALL("Snowball", "Clear the smallest balance first"),

    /** Highest rate first: mathematically optimal, always pays the least interest. */
    AVALANCHE("Avalanche", "Clear the highest interest rate first")
}

/**
 * Amortisation in whole minor units.
 *
 * Everything is integer arithmetic: interest is rounded to the nearest minor unit each month, the
 * way a lender actually charges it. Running the simulation in floating point and rounding only at
 * the end drifts by a few units over a long term and produces a payoff date that is off by a month.
 */
object DebtCalculator {

    /** Beyond this the answer is "not in any useful timeframe", and the loop must not run forever. */
    const val MAX_MONTHS = 600

    private fun monthlyInterest(balanceMinor: Long, annualRatePercent: Double): Long {
        if (annualRatePercent <= 0.0 || balanceMinor <= 0) return 0
        return Math.round(balanceMinor * (annualRatePercent / 100.0 / 12.0))
    }

    /** One debt, one fixed monthly payment. */
    fun project(balanceMinor: Long, annualRatePercent: Double, monthlyPaymentMinor: Long): PayoffProjection {
        if (balanceMinor <= 0) return PayoffProjection(0, 0, 0)
        if (monthlyPaymentMinor <= 0) {
            return PayoffProjection(0, 0, 0, neverClears = true)
        }

        var balance = balanceMinor
        var totalInterest = 0L
        var totalPaid = 0L
        var months = 0

        while (balance > 0 && months < MAX_MONTHS) {
            val interest = monthlyInterest(balance, annualRatePercent)
            // A payment that does not clear the month's interest leaves the balance flat or growing.
            if (monthlyPaymentMinor <= interest) {
                return PayoffProjection(0, 0, 0, neverClears = true)
            }
            // The final month only needs whatever is left plus that month's interest.
            val payment = minOf(monthlyPaymentMinor, balance + interest)
            balance = balance + interest - payment
            totalInterest += interest
            totalPaid += payment
            months++
        }

        return if (balance > 0) {
            PayoffProjection(0, 0, 0, neverClears = true)
        } else {
            PayoffProjection(months, totalInterest, totalPaid)
        }
    }

    /**
     * Runs a whole set of debts under one strategy.
     *
     * Every debt receives its minimum each month; anything left in the pool goes at whichever debt
     * the strategy targets. When a debt clears, its minimum stays in the pool and rolls onto the
     * next one - that rolling is what makes either strategy beat paying minimums forever, and is
     * the part a naive "pay each one in turn" model gets wrong.
     */
    fun simulate(
        debts: List<DebtSnapshot>,
        extraMonthlyMinor: Long,
        strategy: PayoffStrategy
    ): StrategyResult {
        val active = debts.filter { it.balanceMinor > 0 }
        if (active.isEmpty()) return StrategyResult(strategy, 0, 0, emptyList())

        val balances = active.associate { it.id to it.balanceMinor }.toMutableMap()
        val interestPaid = active.associate { it.id to 0L }.toMutableMap()
        val byId = active.associateBy { it.id }
        val cleared = mutableListOf<PayoffStep>()

        var months = 0
        while (balances.values.any { it > 0 } && months < MAX_MONTHS) {
            months++

            // The pool never shrinks as debts clear, which is the whole point of the method.
            var pool = active.sumOf { it.minimumPaymentMinor } + extraMonthlyMinor

            // Interest first, on every outstanding balance.
            for (debt in active) {
                val balance = balances.getValue(debt.id)
                if (balance <= 0) continue
                val interest = monthlyInterest(balance, debt.annualRatePercent)
                balances[debt.id] = balance + interest
                interestPaid[debt.id] = interestPaid.getValue(debt.id) + interest
            }

            // Minimums, then everything remaining at the strategy's target.
            for (debt in active) {
                val balance = balances.getValue(debt.id)
                if (balance <= 0) continue
                val payment = minOf(debt.minimumPaymentMinor, balance, pool)
                balances[debt.id] = balance - payment
                pool -= payment
            }

            val targets = order(active.filter { balances.getValue(it.id) > 0 }, strategy)
            for (target in targets) {
                if (pool <= 0) break
                val balance = balances.getValue(target.id)
                if (balance <= 0) continue
                val payment = minOf(pool, balance)
                balances[target.id] = balance - payment
                pool -= payment
            }

            for (debt in active) {
                if (balances.getValue(debt.id) <= 0 && cleared.none { it.debtId == debt.id }) {
                    cleared += PayoffStep(
                        debtId = debt.id,
                        name = byId.getValue(debt.id).name,
                        clearedInMonth = months,
                        interestPaidMinor = interestPaid.getValue(debt.id)
                    )
                }
            }
        }

        val stuck = balances.values.any { it > 0 }
        return StrategyResult(
            strategy = strategy,
            months = if (stuck) 0 else months,
            totalInterestMinor = interestPaid.values.sum(),
            order = cleared.sortedBy { it.clearedInMonth },
            neverClears = stuck
        )
    }

    /** Snowball targets the smallest balance, avalanche the highest rate. */
    private fun order(debts: List<DebtSnapshot>, strategy: PayoffStrategy): List<DebtSnapshot> =
        when (strategy) {
            PayoffStrategy.SNOWBALL -> debts.sortedBy { it.balanceMinor }
            // Ties on rate fall back to the smaller balance, so the order stays deterministic.
            PayoffStrategy.AVALANCHE -> debts.sortedWith(
                compareByDescending<DebtSnapshot> { it.annualRatePercent }.thenBy { it.balanceMinor }
            )
        }
}

/** The minimum a debt needs to expose for the calculator to work with it. */
data class DebtSnapshot(
    val id: Long,
    val name: String,
    val balanceMinor: Long,
    val annualRatePercent: Double,
    val minimumPaymentMinor: Long
)
