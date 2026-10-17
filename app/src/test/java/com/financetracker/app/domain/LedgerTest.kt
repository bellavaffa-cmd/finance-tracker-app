package com.financetracker.app.domain

import com.financetracker.app.data.Account
import com.financetracker.app.data.AccountType
import com.financetracker.app.data.Budget
import com.financetracker.app.data.Category
import com.financetracker.app.data.CategoryKind
import com.financetracker.app.data.Txn
import com.financetracker.app.data.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class LedgerTest {

    private val oct = YearMonth.of(2026, 10)
    private fun d(day: Int, month: Int = 10) = LocalDate.of(2026, month, day).toEpochDay()
    private val accounts = listOf(Account(1, "Bank", AccountType.BANK, 100000), Account(2, "Savings", AccountType.SAVINGS))
    private val categories = listOf(
        Category(10, "Groceries", CategoryKind.EXPENSE, 0),
        Category(11, "Rent", CategoryKind.EXPENSE, 0),
        Category(12, "Salary", CategoryKind.INCOME, 0),
    )
    private val txns = listOf(
        Txn(1, TxnType.EXPENSE, 5000, 1, null, 10, d(2)),
        Txn(2, TxnType.EXPENSE, 80000, 1, null, 11, d(1)),
        Txn(3, TxnType.INCOME, 250000, 1, null, 12, d(25)),
        Txn(4, TxnType.TRANSFER, 30000, 1, 2, null, d(26)),
        Txn(5, TxnType.EXPENSE, 1000, 1, null, null, d(30, 9)),
    )

    @Test fun balancesIncludeTransfers() {
        val b = Ledger.balances(accounts, txns)
        assertEquals(100000L - 5000 - 80000 + 250000 - 30000 - 1000, b[1])
        assertEquals(30000L, b[2])
    }

    @Test fun totalsExcludeTransfersAndOtherMonths() {
        val t = Ledger.totals(txns, oct)
        assertEquals(250000L, t.income)
        assertEquals(85000L, t.expense)
        assertEquals(1000L, Ledger.totals(txns, oct.minusMonths(1)).expense)
        assertEquals(listOf(1000L, 85000L), Ledger.history(txns, oct, 2).map { it.expense })
    }

    @Test fun categoriesAndBudgets() {
        assertEquals(listOf(CategorySlice(11, 80000), CategorySlice(10, 5000)), Ledger.byCategory(txns, oct, CategoryKind.EXPENSE))
        val lines = Ledger.budgetLines(categories, listOf(Budget(10, 4000)), txns, oct)
        assertEquals(listOf("Groceries", "Rent"), lines.map { it.category.name })
        assertEquals(-1000L, lines[0].remaining)
        assertEquals(null, lines[1].limit)
    }

    @Test fun backupRoundTripsAndValidates() {
        val snap = Snapshot("EUR", accounts, categories, txns, listOf(Budget(10, 4000)))
        assertEquals(snap, Backup.decode(Backup.encode(snap)))
        val broken = snap.copy(txns = txns + Txn(9, TxnType.EXPENSE, 1, 99, null, null, d(1)))
        assertThrows(IllegalArgumentException::class.java) { Backup.decode(Backup.encode(broken)) }
        assertThrows(IllegalArgumentException::class.java) { Backup.decode("{}") }
        assertThrows(IllegalArgumentException::class.java) { Backup.decode("not json") }
    }
}
