package com.financetracker.app.data.receipt

/**
 * A recognised line with enough geometry to know what sits beside it.
 *
 * The geometry is not decoration. Text recognition returns a receipt's label column and amount
 * column as separate blocks, so the line that follows "TOTAL" in list order is very often the item
 * two rows above it, not the amount printed alongside. Matching by position is the only reliable
 * way to pair a label with its figure.
 */
data class OcrLine(
    val text: String,
    val top: Int,
    val bottom: Int,
    val left: Int
) {
    val centreY: Int get() = (top + bottom) / 2
    val height: Int get() = (bottom - top).coerceAtLeast(1)
}

/** What could be read off a receipt. Every field is a guess the user can ignore. */
data class ReceiptReading(
    val totalMinor: Long?,
    /** The line the total came from, so the suggestion can show its working. */
    val totalSourceLine: String?,
    val merchant: String?
)

/**
 * Pulls a total out of recognised receipt text.
 *
 * Free of any ML Kit types so the awkward part - which number on a page of numbers is the amount
 * you paid - can be tested directly against real receipt layouts.
 *
 * Deliberately conservative: a wrong amount silently filled into a finance app is worse than no
 * amount at all, so anything read here is offered as a suggestion and never applied on its own.
 */
object ReceiptParser {

    /** Words that mark the line carrying the amount actually paid, across a few languages. */
    private val TOTAL_KEYWORDS = listOf(
        "total", "totale", "total due", "amount due", "balance due", "grand total",
        "gesamt", "summe", "betrag", "montant", "importe", "suma", "totaal", "totalt"
    )

    /**
     * Lines that look like a total but are not the one you paid. "Subtotal" is the dangerous one:
     * it contains "total", comes before it, and is usually a smaller number.
     */
    private val EXCLUDED_KEYWORDS = listOf(
        "subtotal", "sub total", "sub-total", "zwischensumme", "imponibile",
        "vat", "tax", "iva", "mwst", "tva",
        "change", "cash", "card", "tendered", "rounding", "discount", "saving", "points"
    )

    /**
     * A money-looking token: optional thousands separators and a two-digit decimal part. Requiring
     * the decimals is what keeps quantities, dates and till numbers out.
     */
    private val MONEY = Regex("""(?<![0-9.,])([0-9]{1,3}(?:[ .,][0-9]{3})*|[0-9]+)[.,]([0-9]{2})(?![0-9])""")

    fun parse(lines: List<OcrLine>): ReceiptReading {
        val cleaned = lines.filter { it.text.isNotBlank() }.sortedBy { it.top }
        if (cleaned.isEmpty()) return ReceiptReading(null, null, null)

        val candidates = mutableListOf<Pair<Long, String>>()

        for (line in cleaned) {
            val lower = line.text.lowercase()
            if (EXCLUDED_KEYWORDS.any { lower.contains(it) }) continue
            if (TOTAL_KEYWORDS.none { lower.contains(it) }) continue

            val amount = amountsIn(line.text).maxOrNull()
                // Nothing on the label itself, so look along the same row of the page.
                ?: amountsBeside(line, cleaned).maxOrNull()
                // Only then fall back to the line printed directly underneath.
                ?: amountsBelow(line, cleaned)

            if (amount != null) candidates += amount to line.text
        }

        // Receipts print the figure you paid last, after any subtotals, so the final match wins.
        val total = candidates.lastOrNull()

        return ReceiptReading(
            totalMinor = total?.first,
            totalSourceLine = total?.second,
            merchant = merchantFrom(cleaned)
        )
    }

    /**
     * Amounts printed on the same row as [label] and to its right - the amount column.
     *
     * Rows are matched by vertical overlap rather than exact coordinates, because a label and its
     * figure are rarely recognised with identical bounds.
     */
    private fun amountsBeside(label: OcrLine, all: List<OcrLine>): List<Long> {
        val tolerance = label.height / 2
        return all
            .filter { it !== label }
            .filter { kotlin.math.abs(it.centreY - label.centreY) <= tolerance }
            .filter { it.left >= label.left }
            .flatMap { amountsIn(it.text) }
    }

    /** The nearest line below [label], used when a total's amount wraps onto the next row. */
    private fun amountsBelow(label: OcrLine, all: List<OcrLine>): Long? = all
        .filter { it.top > label.centreY }
        .minByOrNull { it.top }
        ?.let { amountsIn(it.text).maxOrNull() }

    /** Every money-looking amount on a line, in minor units. */
    fun amountsIn(line: String): List<Long> =
        MONEY.findAll(line).mapNotNull { match ->
            val whole = match.groupValues[1].filter { it.isDigit() }
            "$whole${match.groupValues[2]}".toLongOrNull()
        }.toList()

    /**
     * The shop name, guessed from the top of the receipt. Anything that looks like an address, a
     * phone number or a price is skipped - those are what usually sit directly under the name.
     */
    private fun merchantFrom(lines: List<OcrLine>): String? =
        lines.take(4).firstOrNull { line ->
            val text = line.text
            text.count { it.isLetter() } >= 3 &&
                text.length <= 40 &&
                amountsIn(text).isEmpty() &&
                text.count { it.isDigit() } <= 2 &&
                TOTAL_KEYWORDS.none { text.lowercase().contains(it) }
        }?.text?.trim()
}
