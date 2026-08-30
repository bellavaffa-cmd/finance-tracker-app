package com.financetracker.app.data.backup

import com.financetracker.app.data.Money
import com.financetracker.app.data.txn.SplitDetail
import com.financetracker.app.data.txn.TransactionDetail
import com.financetracker.app.data.txn.TxnType
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A transaction with everything hanging off it, ready to be flattened into CSV lines. */
data class ExportRow(
    val detail: TransactionDetail,
    val splits: List<SplitDetail> = emptyList(),
    val tags: List<String> = emptyList()
)

/**
 * Writes the ledger as CSV for a spreadsheet.
 *
 * Amounts are written twice: once in the account's own currency, and once converted to base at the
 * rate frozen on that transaction. A single-currency column would either lose the original figure
 * or produce a column that cannot be summed - both are worse than one extra column.
 *
 * A split transaction is emitted as one line per leg and never as its parent, so the Amount column
 * still totals correctly and each part lands under the category it was actually assigned to.
 */
object CsvExporter {

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    private val HEADERS = listOf(
        "Date", "Time", "Type", "Amount", "Currency", "AmountInBase", "BaseCurrency",
        "FxRateToBase", "Account", "ToAccount", "AmountReceived", "ReceivedCurrency",
        "Category", "Parent category", "Payee", "Note", "Tags", "Split", "Recurring"
    )

    fun export(rows: List<ExportRow>, baseCurrency: String): String {
        val out = StringBuilder()
        // Excel opens UTF-8 CSV as the local codepage unless it sees a BOM, which turns every
        // currency symbol into mojibake. The BOM is the pragmatic fix.
        out.append('\uFEFF')
        out.appendRow(HEADERS)

        for (row in rows) {
            if (row.splits.isEmpty()) {
                out.appendRow(line(row, baseCurrency))
            } else {
                row.splits.forEach { split -> out.appendRow(line(row, baseCurrency, split)) }
            }
        }
        return out.toString()
    }

    private fun line(row: ExportRow, baseCurrency: String, split: SplitDetail? = null): List<String> {
        val detail = row.detail
        val zoned = Instant.ofEpochMilli(detail.dateMillis).atZone(ZoneId.systemDefault())
        val amountMinor = split?.amountMinor ?: detail.amountMinor

        val amount = Money.toMajor(amountMinor, detail.accountCurrency).signedFor(detail.type)
        val baseMinor = Money.toBaseMinor(
            amountMinor, detail.accountCurrency, detail.fxRateToBase, baseCurrency
        )
        val inBase = Money.toMajor(baseMinor, baseCurrency).signedFor(detail.type)

        return listOf(
            zoned.format(dateFormat),
            zoned.format(timeFormat),
            detail.type.label,
            // Transfers keep an unsigned amount: they are neither income nor expense, and signing
            // them would make a naive SUM of this column wrong.
            if (detail.type == TxnType.TRANSFER) {
                Money.toMajor(amountMinor, detail.accountCurrency).toPlainString()
            } else {
                amount.toPlainString()
            },
            detail.accountCurrency,
            if (detail.type == TxnType.TRANSFER) "" else inBase.toPlainString(),
            baseCurrency,
            detail.fxRateToBase.toString(),
            detail.accountName,
            detail.toAccountName.orEmpty(),
            detail.toAmountMinor?.let {
                Money.toMajor(it, detail.toAccountCurrency ?: detail.accountCurrency).toPlainString()
            }.orEmpty(),
            detail.toAccountCurrency.orEmpty(),
            split?.categoryName ?: detail.categoryName.orEmpty(),
            if (split == null) detail.parentCategoryName.orEmpty() else "",
            detail.payee,
            split?.note?.takeIf { it.isNotBlank() } ?: detail.note,
            row.tags.joinToString(" "),
            if (split != null) "yes" else "",
            if (detail.recurringRuleId != null) "yes" else ""
        )
    }

    private fun BigDecimal.signedFor(type: TxnType): BigDecimal =
        if (type == TxnType.EXPENSE) negate() else this

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
    private val USER_TEXT_COLUMNS = setOf(8, 9, 12, 13, 14, 15, 16)

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
