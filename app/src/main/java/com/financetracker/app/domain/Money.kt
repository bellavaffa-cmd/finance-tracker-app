package com.financetracker.app.domain

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object Money {

    /** Minor digits come from the currency (EUR 2, JPY 0, KWD 3), never a hardcoded 100. */
    fun digits(currency: String): Int =
        runCatching { Currency.getInstance(currency).defaultFractionDigits }
            .getOrNull()?.takeIf { it >= 0 } ?: 2

    fun isValidCurrency(code: String): Boolean =
        code.length == 3 && runCatching { Currency.getInstance(code.uppercase()) }.isSuccess

    fun format(minor: Long, currency: String, signed: Boolean = false, locale: Locale = Locale.getDefault()): String {
        val d = digits(currency)
        val nf = NumberFormat.getCurrencyInstance(locale)
        runCatching { nf.currency = Currency.getInstance(currency) }
        nf.minimumFractionDigits = d
        nf.maximumFractionDigits = d
        val body = nf.format(BigDecimal.valueOf(minor, d).abs())
        return when {
            minor < 0 -> "-$body"
            signed && minor > 0 -> "+$body"
            else -> body
        }
    }

    /** Machine-readable form for CSV and edit fields: "1234.50". */
    fun plain(minor: Long, currency: String): String =
        BigDecimal.valueOf(minor, digits(currency)).toPlainString()

    /**
     * Reads a typed or imported amount in either decimal convention: "1,234.56", "1.234,56",
     * "12,5", "-3", "(4.00)", "€ 12". Returns null when the text is not an amount, or when it has
     * more fractional digits than the currency allows (rounding silently would invent money).
     */
    fun parse(text: String, currency: String): Long? {
        var s = text.trim()
        CODE_AFFIX.find(s)?.let { m ->
            if (!isValidCurrency(m.value.trim())) return null
            s = s.removeRange(m.range)
        }
        s = s.filterNot { it.isWhitespace() || it == '\'' || isCurrencySymbol(it) }
        var negative = false
        if (s.startsWith("(") && s.endsWith(")")) { negative = true; s = s.substring(1, s.length - 1) }
        when {
            s.startsWith("-") -> { negative = true; s = s.drop(1) }
            s.startsWith("+") -> s = s.drop(1)
            s.endsWith("-") -> { negative = true; s = s.dropLast(1) }
        }
        if (s.none { it.isDigit() } || s.any { !it.isDigit() && it != '.' && it != ',' }) return null

        val d = digits(currency)
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        val decimalAt: Int = when {
            lastDot >= 0 && lastComma >= 0 -> maxOf(lastDot, lastComma)
            lastDot < 0 && lastComma < 0 -> -1
            else -> {
                val sep = if (lastDot >= 0) '.' else ','
                val at = maxOf(lastDot, lastComma)
                val after = s.length - at - 1
                // "1,234" or "1.234.567" is grouping; "12,5", "12.50" or "0.001" is a decimal point.
                val grouping = s.count { it == sep } > 1 || (after == 3 && d != 3 && !s.startsWith("0"))
                if (grouping) -1 else at
            }
        }
        val intPart = (if (decimalAt >= 0) s.substring(0, decimalAt) else s).filter { it.isDigit() }
        var frac = if (decimalAt >= 0) s.substring(decimalAt + 1) else ""
        if (frac.any { !it.isDigit() }) return null
        if (frac.length > d) {
            if (frac.substring(d).any { it != '0' }) return null
            frac = frac.substring(0, d)
        }
        val value = BigDecimal(intPart.ifEmpty { "0" } + if (frac.isEmpty()) "" else ".$frac")
        val minor = runCatching { value.movePointRight(d).longValueExact() }.getOrNull() ?: return null
        return if (negative) -minor else minor
    }

    private val CODE_AFFIX = Regex("^[A-Za-z]{3}\\s*|\\s*[A-Za-z]{3}$")

    private fun isCurrencySymbol(c: Char) = Character.getType(c) == Character.CURRENCY_SYMBOL.toInt()
}
