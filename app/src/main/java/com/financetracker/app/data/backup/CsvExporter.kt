package com.financetracker.app.data.backup

import com.financetracker.app.data.Money
import com.financetracker.app.data.txn.TransactionDetail
import com.financetracker.app.data.txn.TxnType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Writes the ledger as CSV for a spreadsheet.
 *
 * Amounts are written twice: once in the account's own currency, and once converted to base at the
 * rate frozen on that transaction. A single-currency column would either lose the original figure
 * or produce a column that cannot be summed - both are worse than one extra column.
 */
object CsvExporter {

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    private val HEADERS = listOf(
        "Date", "Time", "Type", "Amount", "Currency", "AmountInBase", "BaseCurrency",
        "FxRateToBase", "Account", "ToAccount", "AmountReceived", "ReceivedCurrency",
        "Category", "Parent category", "Payee", "Note", "Recurring"
    )

    fun export(rows: List<TransactionDetail>, baseCurrency: String): String {
        val out = StringBuilder()
        // Excel opens UTF-8 CSV as the local codepage unless it sees a BOM, which turns every
        // currency symbol into mojibake. The BOM is the pragmatic fix.
        out.append('\uFEFF')
        out.appendRow(HEADERS)

        for (row in rows) {
            val zoned = Instant.ofEpochMilli(row.dateMillis).atZone(ZoneId.systemDefault())
            val signedAmount = Money.toMajor(row.amountMinor, row.accountCurrency).let {
                if (row.type == TxnType.EXPENSE) it.negate() else it
            }
            val baseMinor = Money.toBaseMinor(
                row.amountMinor, row.accountCurrency, row.fxRateToBase, baseCurrency
            )
            val signedBase = Money.toMajor(baseMinor, baseCurrency).let {
                if (row.type == TxnType.EXPENSE) it.negate() else it
            }

            out.appendRow(
                listOf(
                    zoned.format(dateFormat),
                    zoned.format(timeFormat),
                    row.type.label,
                    // Transfers keep an unsigned amount: they are neither income nor expense, and
                    // signing them would make a naive SUM of this column wrong.
                    if (row.type == TxnType.TRANSFER) {
                        Money.toMajor(row.amountMinor, row.accountCurrency).toPlainString()
                    } else {
                        signedAmount.toPlainString()
                    },
                    row.accountCurrency,
                    if (row.type == TxnType.TRANSFER) "" else signedBase.toPlainString(),
                    baseCurrency,
                    row.fxRateToBase.toString(),
                    row.accountName,
                    row.toAccountName.orEmpty(),
                    row.toAmountMinor?.let {
                        Money.toMajor(it, row.toAccountCurrency ?: row.accountCurrency).toPlainString()
                    }.orEmpty(),
                    row.toAccountCurrency.orEmpty(),
                    row.categoryName.orEmpty(),
                    row.parentCategoryName.orEmpty(),
                    row.payee,
                    row.note,
                    if (row.recurringRuleId != null) "yes" else ""
                )
            )
        }
        return out.toString()
    }

    private fun StringBuilder.appendRow(values: List<String>) {
        values.forEachIndexed { index, value ->
            if (index > 0) append(',')
            append(escape(value, guardFormula = index in USER_TEXT_COLUMNS))
        }
        // CRLF per RFC 4180 - some spreadsheet importers on Windows still need it.
        append("\r\n")
    }

    /**
     * Columns whose content the user typed, and which therefore get the formula guard. Numeric
     * columns must not get it: a legitimate "-23.50" starts with a formula trigger, and prefixing
     * that would turn every expense in the file into text the spreadsheet refuses to sum.
     */
    private val USER_TEXT_COLUMNS = setOf(8, 9, 12, 13, 14, 15)

    /**
     * RFC 4180 quoting: a field is quoted when it contains a comma, a quote or a newline, and any
     * embedded quote is doubled. When [guardFormula] is set, a leading '=', '+', '-' or '@' is also
     * prefixed with an apostrophe, so a payee named "=cmd|..." is shown as text by the spreadsheet
     * instead of being executed as a formula.
     */
    private fun escape(value: String, guardFormula: Boolean): String {
        val guarded = if (guardFormula && value.isNotEmpty() && value.first() in FORMULA_TRIGGERS) {
            "'$value"
        } else {
            value
        }
        val needsQuotes = guarded.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"${guarded.replace("\"", "\"\"")}\"" else guarded
    }

    private val FORMULA_TRIGGERS = charArrayOf('=', '+', '-', '@')
}
