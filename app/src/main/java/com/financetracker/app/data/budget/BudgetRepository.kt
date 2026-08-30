package com.financetracker.app.data.budget

import com.financetracker.app.data.MonthPeriod
import com.financetracker.app.data.category.Category
import com.financetracker.app.data.txn.TransactionRepository
import com.financetracker.app.data.txn.TxnType
import kotlinx.coroutines.flow.Flow

class BudgetRepository(
    private val dao: BudgetDao,
    private val transactions: TransactionRepository
) {
    val activeBudgets: Flow<List<Budget>> = dao.observeActive()

    val allBudgets: Flow<List<Budget>> = dao.observeAll()

    suspend fun forCategory(categoryId: Long?): Budget? = dao.forCategory(categoryId)

    suspend fun upsert(budget: Budget): Long = dao.insert(budget)

    suspend fun update(budget: Budget) = dao.update(budget)

    suspend fun delete(budget: Budget) = dao.delete(budget)

    suspend fun deleteForCategory(categoryId: Long) = dao.deleteForCategory(categoryId)

    /** Spend against one budget in one period, in base minor units. */
    suspend fun spentInPeriod(budget: Budget, period: MonthPeriod, baseCurrency: String): Long {
        val rows = transactions.amountRowsOnce(
            type = TxnType.EXPENSE,
            fromMillis = period.startMillis,
            toMillis = period.endMillisExclusive,
            categoryId = budget.categoryId
        )
        return TransactionRepository.sumToBase(rows, baseCurrency)
    }

    /**
     * Unspent (or overspent) allowance carried into [period] from earlier months.
     *
     * Walks forward from the budget's start month, accumulating `budget - spent` for each completed
     * month. The walk is capped at [MAX_ROLLOVER_MONTHS] so a budget left running for years cannot
     * turn one screen into a hundred queries; anything older than that has stopped being a useful
     * signal anyway.
     */
    suspend fun carryOverInto(budget: Budget, period: MonthPeriod, baseCurrency: String): Long {
        if (!budget.rollover) return 0L

        val start = MonthPeriod.containing(budget.startMonthMillis, period.startDay)
        if (start.ordinal >= period.ordinal) return 0L

        val monthsBack = (period.ordinal - start.ordinal).coerceAtMost(MAX_ROLLOVER_MONTHS)
        var carry = 0L
        for (offset in monthsBack downTo 1) {
            val past = period.plusMonths(-offset.toLong())
            carry += budget.amountMinorBase - spentInPeriod(budget, past, baseCurrency)
        }
        return carry
    }

    /** Everything the budgets screen needs for one period, resolved in one pass. */
    suspend fun progressFor(
        budgets: List<Budget>,
        period: MonthPeriod,
        categoriesById: Map<Long, Category>,
        baseCurrency: String
    ): List<BudgetProgress> = budgets
        .filter { it.isActive && MonthPeriod.containing(it.startMonthMillis, period.startDay).ordinal <= period.ordinal }
        .map { budget ->
            val category = budget.categoryId?.let { categoriesById[it] }
            BudgetProgress(
                budget = budget,
                categoryName = category?.name ?: OVERALL_LABEL,
                colorArgb = category?.colorArgb ?: OVERALL_COLOR,
                spentMinorBase = spentInPeriod(budget, period, baseCurrency),
                carryOverMinorBase = carryOverInto(budget, period, baseCurrency)
            )
        }
        // Overall first, then the tightest budgets - the ones about to break are what you opened this for.
        .sortedWith(compareBy({ it.budget.categoryId != null }, { -it.fraction }))

    companion object {
        const val OVERALL_LABEL = "All spending"
        const val OVERALL_COLOR = 0xFF4C8DFF.toInt()
        private const val MAX_ROLLOVER_MONTHS = 24
    }
}
