package com.financetracker.app.domain

import com.financetracker.app.data.Account
import com.financetracker.app.data.AccountType
import com.financetracker.app.data.Category
import com.financetracker.app.data.CategoryKind
import com.financetracker.app.data.Txn
import com.financetracker.app.data.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LedgerCsvTest {

    private val accounts = listOf(Account(1, "Bank", AccountType.BANK), Account(2, "Savings", AccountType.SAVINGS))
    private val categories = listOf(Category(10, "Groceries", CategoryKind.EXPENSE, 0), Category(11, "Salary", CategoryKind.INCOME, 0))
    private val day = LocalDate.of(2026, 10, 3).toEpochDay()
    private val txns = listOf(
        Txn(1, TxnType.EXPENSE, 2350, 1, null, 10, day, "Lidl, Main St", "weekly \"big\" shop"),
        Txn(2, TxnType.INCOME, 300000, 1, null, 11, day, "Employer"),
        Txn(3, TxnType.TRANSFER, 50000, 1, 2, null, day),
    )

    @Test fun exportRoundTripsThroughImport() {
        val csv = LedgerCsv.export(txns, accounts, categories, "EUR")
        val parsed = LedgerCsv.parse(csv, "EUR")
        assertEquals(emptyList<String>(), parsed.errors)
        assertEquals(3, parsed.rows.size)
        val shop = parsed.rows[0]
        assertEquals(TxnType.EXPENSE, shop.type)
        assertEquals("Lidl, Main St", shop.payee)
        assertEquals("weekly \"big\" shop", shop.note)
        assertEquals(2350L, shop.amount)
        assertEquals("Savings", parsed.rows[2].toAccount)

        val plan = LedgerCsv.plan(parsed.rows, accounts, categories, txns, "Bank")
        assertEquals(3, plan.duplicates)
        assertTrue(plan.toInsert.isEmpty())
    }

    @Test fun readsSignedBankExport() {
        val csv = "Booking date;Description;Amount\n31/10/2026;Coffee;-3,20\n01/10/2026;Salary;2.500,00\n"
        val parsed = LedgerCsv.parse(csv, "EUR")
        assertEquals(emptyList<String>(), parsed.errors)
        assertEquals(TxnType.EXPENSE, parsed.rows[0].type)
        assertEquals(320L, parsed.rows[0].amount)
        assertEquals(LocalDate.of(2026, 10, 31), parsed.rows[0].date)
        assertEquals(TxnType.INCOME, parsed.rows[1].type)
        assertEquals(250000L, parsed.rows[1].amount)
    }

    @Test fun reportsBadRowsByLine() {
        val csv = "Date,Type,Account,To account,Amount\n2026-10-01,expense,Bank,,abc\nnope,expense,Bank,,1\n2026-10-01,transfer,Bank,,5\n2026-10-01,expense,Bank,,5\n"
        val parsed = LedgerCsv.parse(csv, "EUR")
        assertEquals(1, parsed.rows.size)
        assertEquals(3, parsed.errors.size)
        assertTrue(parsed.errors[0].startsWith("Line 2"))
        assertTrue(parsed.errors[2].startsWith("Line 4"))
    }

    @Test fun dateOrderIsDecidedForTheWholeFile() {
        val monthFirst = "Date,Amount\n03/04/2026,-1\n12/25/2026,-1\n"
        assertEquals(LocalDate.of(2026, 3, 4), LedgerCsv.parse(monthFirst, "EUR").rows[0].date)
        val ambiguous = LedgerCsv.parse("Date,Amount\n03/04/2026,-1\n", "EUR")
        assertEquals(LocalDate.of(2026, 4, 3), ambiguous.rows[0].date)
        assertEquals(1, ambiguous.warnings.size)
        val conflicting = LedgerCsv.parse("Date,Amount\n13/04/2026,-1\n04/13/2026,-1\n", "EUR")
        assertEquals(2, conflicting.errors.size)
    }

    @Test fun planCreatesMissingAccountsAndCategoriesAndCountsDuplicates() {
        val rows = LedgerCsv.parse(
            "Date,Account,Category,Payee,Amount\n2026-10-03,,Groceries,Lidl,-23.50\n2026-10-03,,Groceries,Lidl,-23.50\n2026-10-04,Wallet,Coffee,Cafe,-3\n",
            "EUR",
        ).rows
        val existing = listOf(Txn(1, TxnType.EXPENSE, 2350, 1, null, 10, day, "lidl"))
        val plan = LedgerCsv.plan(rows, accounts, categories, existing, "Bank")
        assertEquals(1, plan.duplicates)
        assertEquals(2, plan.toInsert.size)
        assertEquals(listOf("Wallet"), plan.newAccounts)
        assertEquals(listOf("Coffee" to CategoryKind.EXPENSE), plan.newCategories)
    }

    @Test fun readsFinanceTracker1Export() {
        val csv = "\uFEFFDate,Time,Type,Amount,Currency,AmountInBase,BaseCurrency,FxRateToBase,Account,ToAccount," +
            "AmountReceived,ReceivedCurrency,Category,Parent category,Payee,Note,Tags,Split,Recurring\r\n" +
            "2026-10-03,12:30,Expense,-20.00,USD,-18.40,EUR,0.92,US card,,,,Eating out,Food,Diner,,holiday,,\r\n" +
            "2026-10-04,09:00,Income,2500.00,EUR,2500.00,EUR,1,Bank,,,,Salary,,Employer,,,,yes\r\n" +
            "2026-10-05,10:00,Transfer,500.00,EUR,,EUR,1,Bank,Savings,500.00,EUR,,,,,,,\r\n"
        val parsed = LedgerCsv.parse(csv, "EUR")
        assertEquals(emptyList<String>(), parsed.errors)
        assertEquals(listOf(TxnType.EXPENSE, TxnType.INCOME, TxnType.TRANSFER), parsed.rows.map { it.type })
        assertEquals(listOf(1840L, 250000L, 50000L), parsed.rows.map { it.amount })
        assertEquals("Eating out", parsed.rows[0].category)
        assertEquals("Savings", parsed.rows[2].toAccount)
    }

    @Test fun csvHandlesQuotesNewlinesAndDelimiters() {
        val rows = Csv.parse("﻿a\tb\n\"x\ty\"\t\"line1\nline2\"\r\n\n")
        assertEquals(listOf(listOf("a", "b"), listOf("x\ty", "line1\nline2")), rows)
    }
}
