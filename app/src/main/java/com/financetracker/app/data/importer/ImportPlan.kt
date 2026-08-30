package com.financetracker.app.data.importer

import com.financetracker.app.data.Money
import com.financetracker.app.data.txn.TxnType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A column the importer knows how to use. Everything else in the file is ignored. */
enum class ImportField(val label: String, val required: Boolean) {
    DATE("Date", true),
    AMOUNT("Amount", true),
    TYPE("Type", false),
    ACCOUNT("Account", false),
    CATEGORY("Category", false),
    PAYEE("Payee", false),
    NOTE("Note", false),
    CURRENCY("Currency", false)
}

/**
 * How the file's dates are laid out.
 *
 * `03/04/2026` is the 3rd of April in most of the world and the 4th of March in the US, and no
 * amount of cleverness can tell them apart from a single row. The importer guesses from the whole
 * file and then says which reading it chose, so a wrong guess is visible before anything is written
 * rather than discovered months later.
 */
enum class DateStyle(val label: String, val patterns: List<String>) {
    ISO("2026-04-03", listOf("yyyy-MM-dd", "yyyy/MM/dd")),
    DAY_FIRST("03/04/2026 (day first)", listOf("dd/MM/yyyy", "dd-MM-yyyy", "dd.MM.yyyy")),
    MONTH_FIRST("04/03/2026 (month first)", listOf("MM/dd/yyyy", "MM-dd-yyyy"));

    fun parse(value: String): LocalDate? {
        val trimmed = value.trim().substringBefore(' ')
        for (pattern in patterns) {
            val date = runCatching {
                LocalDate.parse(trimmed, DateTimeFormatter.ofPattern(pattern))
            }.getOrNull()
            if (date != null) return date
        }
        return null
    }
}

/** One parsed row, or the reason it could not be used. */
sealed interface ParsedRow {
    data class Usable(
        val lineNumber: Int,
        val dateMillis: Long,
        val type: TxnType,
        val amountMinor: Long,
        val currencyCode: String?,
        val accountName: String?,
        val categoryName: String?,
        val payee: String,
        val note: String
    ) : ParsedRow

    data class Skipped(val lineNumber: Int, val reason: String) : ParsedRow
}

data class ImportPreview(
    val usable: List<ParsedRow.Usable>,
    val skipped: List<ParsedRow.Skipped>,
    val dateStyle: DateStyle,
    val dateStyleWasAmbiguous: Boolean,
    val newAccounts: List<String>,
    val newCategories: List<String>
) {
    val totalRows: Int get() = usable.size + skipped.size
}

/**
 * Turns raw CSV rows into transactions, without touching the database.
 *
 * Kept separate from the repository so the awkward parts - date ambiguity, decimal separators,
 * inferring direction from a sign - can be reasoned about and checked on their own.
 */
object ImportPlan {

    /** Header names matched case-insensitively; the app's own export headers are included. */
    private val SYNONYMS: Map<ImportField, List<String>> = mapOf(
        ImportField.DATE to listOf("date", "transaction date", "booking date", "posted", "day"),
        ImportField.AMOUNT to listOf("amount", "value", "sum", "debit/credit", "betrag", "importo"),
        ImportField.TYPE to listOf("type", "kind", "direction"),
        ImportField.ACCOUNT to listOf("account", "account name", "from account", "wallet"),
        ImportField.CATEGORY to listOf("category", "categoria", "kategorie"),
        ImportField.PAYEE to listOf("payee", "merchant", "description", "counterparty", "name", "beneficiary"),
        ImportField.NOTE to listOf("note", "notes", "memo", "reference", "comment"),
        ImportField.CURRENCY to listOf("currency", "ccy")
    )

    /** Best-guess mapping from a header row. Exact matches win over partial ones. */
    fun detectMapping(header: List<String>): Map<ImportField, Int> {
        val normalised = header.map { it.trim().lowercase() }
        val mapping = mutableMapOf<ImportField, Int>()
        val taken = mutableSetOf<Int>()

        for ((field, names) in SYNONYMS) {
            val exact = normalised.indexOfFirst { it in names && it.isNotEmpty() }
            if (exact >= 0 && exact !in taken) {
                mapping[field] = exact
                taken += exact
            }
        }
        for ((field, names) in SYNONYMS) {
            if (field in mapping) continue
            val partial = normalised.indexOfFirst { column ->
                column.isNotEmpty() && column !in listOf("") &&
                    names.any { column.contains(it) || it.contains(column) }
            }
            if (partial >= 0 && partial !in taken) {
                mapping[field] = partial
                taken += partial
            }
        }
        return mapping
    }

    /**
     * Chooses a date layout by looking at the whole file. A value above 12 in either position
     * settles it; if nothing ever exceeds 12 the file is genuinely ambiguous and the caller is told.
     */
    fun detectDateStyle(rows: List<List<String>>, dateColumn: Int): Pair<DateStyle, Boolean> {
        val values = rows.mapNotNull { it.getOrNull(dateColumn)?.trim() }.filter { it.isNotEmpty() }
        if (values.isEmpty()) return DateStyle.ISO to false

        if (values.all { DateStyle.ISO.parse(it) != null }) return DateStyle.ISO to false

        var sawDayOverTwelve = false
        var sawMonthOverTwelve = false
        for (value in values) {
            val parts = value.substringBefore(' ').split('/', '-', '.')
            if (parts.size < 2) continue
            val first = parts[0].toIntOrNull() ?: continue
            val second = parts[1].toIntOrNull() ?: continue
            if (first > 12) sawDayOverTwelve = true
            if (second > 12) sawMonthOverTwelve = true
        }
        return when {
            sawDayOverTwelve -> DateStyle.DAY_FIRST to false
            sawMonthOverTwelve -> DateStyle.MONTH_FIRST to false
            // Nothing distinguishes them. Day-first is the more common convention worldwide, but
            // the caller must surface the choice rather than quietly assume it.
            else -> DateStyle.DAY_FIRST to true
        }
    }

    /**
     * Reads an amount that may be written in either convention: "1,234.56" or "1.234,56".
     *
     * The separator nearest the end is the decimal one. Currency symbols, spaces and parenthesised
     * negatives are all handled, because bank exports use all three.
     */
    fun parseAmount(raw: String, currencyCode: String): Long? {
        var text = raw.trim()
        if (text.isEmpty()) return null

        val parenthesised = text.startsWith("(") && text.endsWith(")")
        if (parenthesised) text = text.substring(1, text.length - 1)

        val negative = parenthesised || text.startsWith("-")
        text = text.removePrefix("-").removePrefix("+")
        text = text.filter { it.isDigit() || it == '.' || it == ',' }
        if (text.isEmpty()) return null

        val lastDot = text.lastIndexOf('.')
        val lastComma = text.lastIndexOf(',')
        val normalised = when {
            lastDot < 0 && lastComma < 0 -> text
            // Whichever separator comes last is the decimal point; the other groups thousands.
            lastDot > lastComma -> text.replace(",", "")
            else -> text.replace(".", "").replace(',', '.')
        }

        val decimal = runCatching { BigDecimal(normalised) }.getOrNull() ?: return null
        val minor = Money.parseToMinor(decimal.toPlainString(), currencyCode) ?: return null
        return if (negative) -minor else minor
    }

    fun build(
        rows: List<List<String>>,
        mapping: Map<ImportField, Int>,
        dateStyle: DateStyle,
        dateStyleWasAmbiguous: Boolean,
        defaultCurrency: String,
        defaultAccountName: String,
        knownAccounts: Set<String>,
        knownCategories: Set<String>
    ): ImportPreview {
        val usable = mutableListOf<ParsedRow.Usable>()
        val skipped = mutableListOf<ParsedRow.Skipped>()
        val zone = ZoneId.systemDefault()

        val dateColumn = mapping[ImportField.DATE]
        val amountColumn = mapping[ImportField.AMOUNT]
        if (dateColumn == null || amountColumn == null) {
            return ImportPreview(emptyList(), emptyList(), dateStyle, dateStyleWasAmbiguous, emptyList(), emptyList())
        }

        rows.forEachIndexed { index, row ->
            val line = index + 2 // +1 for zero-based, +1 for the header the caller stripped
            fun cell(field: ImportField): String =
                mapping[field]?.let { row.getOrNull(it) }?.trim().orEmpty()

            val rawDate = row.getOrNull(dateColumn)?.trim().orEmpty()
            val date = dateStyle.parse(rawDate)
            if (date == null) {
                skipped += ParsedRow.Skipped(line, "could not read the date \"$rawDate\"")
                return@forEachIndexed
            }

            val currency = cell(ImportField.CURRENCY).uppercase().takeIf { it.length == 3 }
                ?: defaultCurrency
            val rawAmount = row.getOrNull(amountColumn)?.trim().orEmpty()
            val signedAmount = parseAmount(rawAmount, currency)
            if (signedAmount == null || signedAmount == 0L) {
                skipped += ParsedRow.Skipped(line, "could not read the amount \"$rawAmount\"")
                return@forEachIndexed
            }

            val declaredType = cell(ImportField.TYPE).lowercase()
            val type = when {
                declaredType.startsWith("transfer") -> {
                    // A single CSV row cannot say what a transfer did to both accounts, and
                    // guessing would silently invent money. These are reported, not imported.
                    skipped += ParsedRow.Skipped(line, "transfers cannot be imported from CSV")
                    return@forEachIndexed
                }
                declaredType.startsWith("income") || declaredType.startsWith("credit") -> TxnType.INCOME
                declaredType.startsWith("expense") || declaredType.startsWith("debit") -> TxnType.EXPENSE
                // With no type column the sign carries the direction, which is how bank exports
                // almost always work.
                signedAmount < 0 -> TxnType.EXPENSE
                else -> TxnType.INCOME
            }

            usable += ParsedRow.Usable(
                lineNumber = line,
                dateMillis = date.atStartOfDay(zone).toInstant().toEpochMilli(),
                type = type,
                amountMinor = kotlin.math.abs(signedAmount),
                currencyCode = cell(ImportField.CURRENCY).uppercase().takeIf { it.length == 3 },
                accountName = cell(ImportField.ACCOUNT).ifBlank { null },
                categoryName = cell(ImportField.CATEGORY).ifBlank { null },
                payee = cell(ImportField.PAYEE),
                note = cell(ImportField.NOTE)
            )
        }

        val newAccounts = usable.mapNotNull { it.accountName }
            .distinct()
            .filter { it.lowercase() !in knownAccounts }
        val newCategories = usable.mapNotNull { it.categoryName }
            .distinct()
            .filter { it.lowercase() !in knownCategories }

        return ImportPreview(
            usable = usable,
            skipped = skipped,
            dateStyle = dateStyle,
            dateStyleWasAmbiguous = dateStyleWasAmbiguous,
            newAccounts = newAccounts,
            newCategories = newCategories
        )
    }
}
