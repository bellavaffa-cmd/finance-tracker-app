package com.financetracker.app.data.budget

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A monthly spending limit. Exactly one budget may exist per category, plus at most one "overall"
 * budget with a null [categoryId] that caps total spending.
 *
 * The amount is held in **base-currency** minor units: a budget is a plan you make once, so it
 * should not drift every time an exchange rate moves.
 */
@Entity(
    tableName = "budget",
    indices = [Index(value = ["categoryId"], unique = true)]
)
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Null means this is the overall budget across all spending. */
    val categoryId: Long? = null,
    val amountMinorBase: Long,
    /**
     * When true, an underspent month raises next month's allowance and an overspent one lowers it,
     * so a lean January actually buys you a roomier February.
     */
    val rollover: Boolean = false,
    /** Month the budget starts applying, as epoch millis at the start of that budget month. */
    val startMonthMillis: Long,
    val isActive: Boolean = true
)

/** A budget resolved for one specific month, with its spend and carry-over already computed. */
data class BudgetProgress(
    val budget: Budget,
    val categoryName: String,
    val colorArgb: Int,
    val spentMinorBase: Long,
    /** Signed: positive is unspent allowance carried in, negative is last month's overspend. */
    val carryOverMinorBase: Long
) {
    val allowanceMinorBase: Long get() = budget.amountMinorBase + carryOverMinorBase
    val remainingMinorBase: Long get() = allowanceMinorBase - spentMinorBase
    val isOverBudget: Boolean get() = remainingMinorBase < 0
    /** 0f..1f for the bar; values above 1 are clamped and shown by the over-budget colour instead. */
    val fraction: Float
        get() = if (allowanceMinorBase <= 0) 1f
        else (spentMinorBase.toFloat() / allowanceMinorBase.toFloat()).coerceIn(0f, 1f)
}
