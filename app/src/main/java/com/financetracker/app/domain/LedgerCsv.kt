package com.financetracker.app.domain

import com.financetracker.app.data.Account
import com.financetracker.app.data.Category
import com.financetracker.app.data.CategoryKind
import com.financetracker.app.data.Txn
import com.financetracker.app.data.TxnType
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * The ledger as a spreadsheet. The export is the import format, so a file round-trips; the
 * importer also accepts a typical bank export (Date, Description, Amount with a sign).
 */
object LedgerCsv {

    val HEADER = listOf("Date", "Type", "Account", "To account", "Category", "Payee", "Note", "Amount")

    fun export(txns: List<Txn>, accounts: List<Account>, categories: List<Category>, currency: String): String {
        val acc = accounts.associate { it.id to it.name }
        val cat = categories.associate { it.id to it.name }
        val rows = txns.sortedWith(compareBy({ it.date }, { it.id })).map { t ->
            listOf(
                LocalDate.ofEpochDay(t.date).toString(),
                t.type.name.lowercase(),
                acc[t.accountId].orEmpty(),
                t.toAccountId?.let { acc[it] }.orEmpty(),
                t.categoryId?.let { cat[it] }.orEmpty(),
                t.payee,
                t.note,
                Money.plain(t.amount, currency),
            )
        }
        return Csv.write(listOf(HEADER) + rows)
    }

    data class Row(
        val line: Int,
        val date: LocalDate,
        val type: TxnType,
        val account: String,
        val toAccount: String,
        val category: String,
        val payee: String,
        val note: String,
        val amount: Long,
    )

    data class Parsed(val rows: List<Row>, val errors: List<String>, val warnings: List<String>)

    private enum class Col { DATE, TYPE, ACCOUNT, TO_ACCOUNT, CATEGORY, PAYEE, NOTE, AMOUNT }

    private val aliases: Map<String, Col> = mapOf(
        "date" to Col.DATE, "bookingdate" to Col.DATE, "transactiondate" to Col.DATE,
        "type" to Col.TYPE,
        "account" to Col.ACCOUNT, "fromaccount" to Col.ACCOUNT,
        "toaccount" to Col.TO_ACCOUNT,
        "category" to Col.CATEGORY,
        "payee" to Col.PAYEE, "description" to Col.PAYEE, "merchant" to Col.PAYEE, "name" to Col.PAYEE,
        "note" to Col.NOTE, "notes" to Col.NOTE, "memo" to Col.NOTE,
        "amount" to Col.AMOUNT, "value" to Col.AMOUNT,
    )

    /** Rows it cannot read are reported by line with a reason, never silently dropped. */
    fun parse(text: String, currency: String): Parsed {
        val table = Csv.parse(text)
        if (table.isEmpty()) return Parsed(emptyList(), listOf("The file is empty."), emptyList())
        val cols = mutableMapOf<Col, Int>()
        table[0].forEachIndexed { i, h ->
            aliases[h.lowercase().filter { it.isLetter() }]?.let { cols.putIfAbsent(it, i) }
        }
        if (Col.DATE !in cols || Col.AMOUNT !in cols) {
            return Parsed(emptyList(), listOf("The first row must be a header with at least Date and Amount columns."), emptyList())
        }
        val body = table.drop(1)
        fun cell(r: List<String>, c: Col) = cols[c]?.let { r.getOrNull(it) }?.trim().orEmpty()

        val warnings = mutableListOf<String>()
        val order = DateOrder.detect(body.map { cell(it, Col.DATE) })
        if (order == DateOrder.AMBIGUOUS) {
            warnings += "Every date could be read day-first or month-first; they were read as day/month/year."
        }
        val errors = mutableListOf<String>()
        val rows = mutableListOf<Row>()
        body.forEachIndexed { index, r ->
            val line = index + 2
            val date = DateOrder.parse(cell(r, Col.DATE), order)
            if (date == null) { errors += "Line $line: can't read the date \"${cell(r, Col.DATE)}\"."; return@forEachIndexed }
            val signed = Money.parse(cell(r, Col.AMOUNT), currency)
            if (signed == null || signed == 0L) { errors += "Line $line: can't read the amount \"${cell(r, Col.AMOUNT)}\"."; return@forEachIndexed }
            val typeText = cell(r, Col.TYPE).lowercase()
            val type = when {
                typeText.isEmpty() -> if (signed < 0) TxnType.EXPENSE else TxnType.INCOME
                typeText.startsWith("exp") || typeText == "debit" -> TxnType.EXPENSE
                typeText.startsWith("inc") || typeText == "credit" -> TxnType.INCOME
                typeText.startsWith("trans") -> TxnType.TRANSFER
                else -> { errors += "Line $line: unknown type \"${cell(r, Col.TYPE)}\"."; return@forEachIndexed }
            }
            val toAccount = cell(r, Col.TO_ACCOUNT)
            if (type == TxnType.TRANSFER && toAccount.isEmpty()) {
                errors += "Line $line: a transfer needs a To account."; return@forEachIndexed
            }
            if (type == TxnType.TRANSFER && toAccount.equals(cell(r, Col.ACCOUNT), ignoreCase = true)) {
                errors += "Line $line: a transfer can't go to the same account."; return@forEachIndexed
            }
            rows += Row(
                line = line, date = date, type = type,
                account = cell(r, Col.ACCOUNT),
                toAccount = if (type == TxnType.TRANSFER) toAccount else "",
                category = if (type == TxnType.TRANSFER) "" else cell(r, Col.CATEGORY),
                payee = cell(r, Col.PAYEE), note = cell(r, Col.NOTE),
                amount = kotlin.math.abs(signed),
            )
        }
        return Parsed(rows, errors, warnings)
    }

    enum class DateOrder {
        ISO, DAY_FIRST, MONTH_FIRST, AMBIGUOUS, CONFLICTING;

        companion object {
            private val SLASHED = Regex("""^(\d{1,2})[./-](\d{1,2})[./-](\d{4})$""")

            /** Decided once for the whole file: "03/04/2026" means the same thing on every line. */
            fun detect(dates: List<String>): DateOrder {
                var dayFirst = false
                var monthFirst = false
                var anySlashed = false
                for (d in dates) {
                    val m = SLASHED.matchEntire(d.trim()) ?: continue
                    anySlashed = true
                    val (a, b) = m.destructured
                    if (a.toInt() > 12) dayFirst = true
                    if (b.toInt() > 12) monthFirst = true
                }
                return when {
                    !anySlashed -> ISO
                    dayFirst && monthFirst -> CONFLICTING
                    dayFirst -> DAY_FIRST
                    monthFirst -> MONTH_FIRST
                    else -> AMBIGUOUS
                }
            }

            fun parse(text: String, order: DateOrder): LocalDate? {
                val t = text.trim()
                try { return LocalDate.parse(t.take(10)) } catch (_: DateTimeParseException) {}
                val m = SLASHED.matchEntire(t) ?: return null
                val (a, b, y) = m.destructured
                val (day, month) = when (order) {
                    MONTH_FIRST -> b to a
                    CONFLICTING -> return null
                    else -> a to b
                }
                return runCatching { LocalDate.of(y.toInt(), month.toInt(), day.toInt()) }.getOrNull()
            }
        }
    }

    /** What an import will do, worked out before anything is written. */
    data class Plan(
        val newAccounts: List<String>,
        val newCategories: List<Pair<String, CategoryKind>>,
        val toInsert: List<Row>,
        val duplicates: Int,
    )

    /**
     * Rows that already exist (same date, type, accounts, amount and payee) are skipped, counted
     * per occurrence so two identical coffees on one day still import if only one is in the ledger.
     * Rows with no account go to [defaultAccount].
     */
    fun plan(
        rows: List<Row>,
        accounts: List<Account>,
        categories: List<Category>,
        existing: List<Txn>,
        defaultAccount: String,
    ): Plan {
        val accById = accounts.associate { it.id to it.name.lowercase() }
        fun key(date: Long, type: TxnType, from: String, to: String, amount: Long, payee: String) =
            listOf(date, type, from.lowercase(), to.lowercase(), amount, payee.trim().lowercase())

        val seen = existing.groupingBy {
            key(it.date, it.type, accById[it.accountId].orEmpty(), it.toAccountId?.let(accById::get).orEmpty(), it.amount, it.payee)
        }.eachCount().toMutableMap()

        val knownAccounts = accounts.map { it.name.lowercase() }.toMutableSet()
        val knownCategories = categories.map { it.name.lowercase() to it.kind }.toMutableSet()
        val newAccounts = mutableListOf<String>()
        val newCategories = mutableListOf<Pair<String, CategoryKind>>()
        val insert = mutableListOf<Row>()
        var dupes = 0

        for (raw in rows) {
            val r = if (raw.account.isBlank()) raw.copy(account = defaultAccount) else raw
            val k = key(r.date.toEpochDay(), r.type, r.account, r.toAccount, r.amount, r.payee)
            val left = seen[k] ?: 0
            if (left > 0) { seen[k] = left - 1; dupes++; continue }
            insert += r
            for (name in listOf(r.account, r.toAccount)) {
                if (name.isNotBlank() && knownAccounts.add(name.lowercase())) newAccounts += name
            }
            if (r.category.isNotBlank()) {
                val kind = if (r.type == TxnType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
                if (knownCategories.add(r.category.lowercase() to kind)) newCategories += r.category to kind
            }
        }
        return Plan(newAccounts, newCategories, insert, dupes)
    }
}
