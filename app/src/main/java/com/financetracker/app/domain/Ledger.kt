package com.financetracker.app.domain

import com.financetracker.app.data.Account
import com.financetracker.app.data.Budget
import com.financetracker.app.data.Category
import com.financetracker.app.data.CategoryKind
import com.financetracker.app.data.Txn
import com.financetracker.app.data.TxnType
import java.time.YearMonth

data class MonthTotals(val month: YearMonth, val income: Long, val expense: Long) {
    val net: Long get() = income - expense
}

/** [categoryId] null is the "Uncategorised" bucket. */
data class CategorySlice(val categoryId: Long?, val amount: Long)

data class BudgetLine(val category: Category, val limit: Long?, val spent: Long) {
    val remaining: Long? get() = limit?.minus(spent)
    val fraction: Float get() = if (limit == null || limit <= 0) 0f else spent.toFloat() / limit
}

/**
 * Pure ledger arithmetic. Transfers move money between your own accounts, so they change
 * balances but are excluded from every income and expense total.
 */
object Ledger {

    fun YearMonth.epochDays(): LongRange = atDay(1).toEpochDay()..atEndOfMonth().toEpochDay()

    fun monthOf(epochDay: Long): YearMonth = YearMonth.from(java.time.LocalDate.ofEpochDay(epochDay))

    fun balances(accounts: List<Account>, txns: List<Txn>): Map<Long, Long> {
        val out = accounts.associate { it.id to it.openingBalance }.toMutableMap()
        for (t in txns) {
            when (t.type) {
                TxnType.EXPENSE -> out.merge(t.accountId, -t.amount, Long::plus)
                TxnType.INCOME -> out.merge(t.accountId, t.amount, Long::plus)
                TxnType.TRANSFER -> {
                    out.merge(t.accountId, -t.amount, Long::plus)
                    t.toAccountId?.let { out.merge(it, t.amount, Long::plus) }
                }
            }
        }
        return out
    }

    fun totals(txns: List<Txn>, month: YearMonth): MonthTotals {
        val days = month.epochDays()
        var income = 0L
        var expense = 0L
        for (t in txns) {
            if (t.date !in days) continue
            when (t.type) {
                TxnType.INCOME -> income += t.amount
                TxnType.EXPENSE -> expense += t.amount
                TxnType.TRANSFER -> Unit
            }
        }
        return MonthTotals(month, income, expense)
    }

    /** Oldest first, ending with [last]. */
    fun history(txns: List<Txn>, last: YearMonth, months: Int): List<MonthTotals> =
        (months - 1 downTo 0).map { totals(txns, last.minusMonths(it.toLong())) }

    fun byCategory(txns: List<Txn>, month: YearMonth, kind: CategoryKind): List<CategorySlice> {
        val type = if (kind == CategoryKind.EXPENSE) TxnType.EXPENSE else TxnType.INCOME
        val days = month.epochDays()
        return txns.asSequence()
            .filter { it.type == type && it.date in days }
            .groupBy { it.categoryId }
            .map { (id, list) -> CategorySlice(id, list.sumOf { it.amount }) }
            .sortedByDescending { it.amount }
    }

    /** Every expense category, budgeted ones first (most used up first), then the rest by spend. */
    fun budgetLines(categories: List<Category>, budgets: List<Budget>, txns: List<Txn>, month: YearMonth): List<BudgetLine> {
        val spent = byCategory(txns, month, CategoryKind.EXPENSE).associate { it.categoryId to it.amount }
        val limits = budgets.associate { it.categoryId to it.monthlyLimit }
        return categories.filter { it.kind == CategoryKind.EXPENSE }
            .map { BudgetLine(it, limits[it.id], spent[it.id] ?: 0) }
            .sortedWith(
                compareBy<BudgetLine> { it.limit == null }
                    .thenByDescending { if (it.limit != null) it.fraction else it.spent.toFloat() }
                    .thenBy { it.category.name.lowercase() }
            )
    }
}
