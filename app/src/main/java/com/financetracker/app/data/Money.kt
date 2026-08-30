package com.financetracker.app.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

/**
 * All money in this app is stored as a [Long] count of a currency's *minor units* (cents, pence,
 * yen). Doubles are never used to hold a balance - only as FX rates, where a rounding error of
 * 1e-9 is harmless because the product is immediately re-rounded back to whole minor units.
 *
 * The number of minor digits varies by currency (EUR 2, JPY 0, KWD 3), so every conversion between
 * a display string and a stored Long has to go through the currency, never a hardcoded 100.
 */
object Money {

    /** Currencies offered in the picker. Any ISO-4217 code java.util.Currency knows also works. */
    val COMMON_CODES = listOf(
        "EUR", "USD", "GBP", "CHF", "JPY", "CNY", "AUD", "CAD", "SEK", "NOK",
        "DKK", "PLN", "CZK", "HUF", "RON", "TRY", "BRL", "MXN", "INR", "SGD",
        "HKD", "NZD", "ZAR", "AED", "THB", "KRW", "PHP", "IDR", "VND", "ILS"
    )

    private val fallbackDigits = mapOf(
        "JPY" to 0, "KRW" to 0, "VND" to 0, "IDR" to 0, "CLP" to 0, "ISK" to 0,
        "KWD" to 3, "BHD" to 3, "OMR" to 3, "TND" to 3, "JOD" to 3
    )

    private val fallbackSymbols = mapOf(
        "EUR" to "\u20AC", "USD" to "$", "GBP" to "\u00A3", "JPY" to "\u00A5",
        "CNY" to "\u00A5", "INR" to "\u20B9", "KRW" to "\u20A9", "TRY" to "\u20BA",
        "PLN" to "z\u0142", "CHF" to "CHF", "SEK" to "kr", "NOK" to "kr", "DKK" to "kr"
    )

    private fun currencyOrNull(code: String): Currency? =
        runCatching { Currency.getInstance(code) }.getOrNull()

    /** Minor-unit digits for [code] - 2 for EUR, 0 for JPY, 3 for KWD. */
    fun digits(code: String): Int {
        val fromJdk = currencyOrNull(code)?.defaultFractionDigits
        // getDefaultFractionDigits() returns -1 for pseudo-currencies like XAU.
        if (fromJdk != null && fromJdk >= 0) return fromJdk
        return fallbackDigits[code] ?: 2
    }

    fun symbol(code: String): String =
        currencyOrNull(code)?.getSymbol(Locale.getDefault())?.takeIf { it != code }
            ?: fallbackSymbols[code]
            ?: code

    private fun scaleOf(code: String): BigDecimal = BigDecimal.TEN.pow(digits(code))

    /** "12.34" -> 1234 minor units for a 2-digit currency. Returns null if unparseable. */
    fun parseToMinor(input: String, code: String): Long? {
        val cleaned = input.trim().replace(',', '.').replace(" ", "")
        if (cleaned.isEmpty()) return null
        val decimal = runCatching { BigDecimal(cleaned) }.getOrNull() ?: return null
        return decimal.multiply(scaleOf(code))
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
    }

    /** 1234 minor units -> BigDecimal("12.34"). */
    fun toMajor(minor: Long, code: String): BigDecimal =
        BigDecimal(minor).divide(scaleOf(code), digits(code), RoundingMode.HALF_UP)

    /** Bare number, no symbol - for text fields being edited. */
    fun editString(minor: Long, code: String): String = toMajor(minor, code).toPlainString()

    /**
     * Display form: "-\u20AC1,234.50". [withSign] shows an explicit + for positive values, which
     * balance rows want and expense rows do not.
     */
    fun format(minor: Long, code: String, withSign: Boolean = false): String {
        val digits = digits(code)
        val abs = abs(minor)
        val scale = BigDecimal.TEN.pow(digits).toLong()
        val whole = abs / scale
        val frac = abs % scale
        val grouped = groupDigits(whole)
        val body = if (digits == 0) grouped else "$grouped.${frac.toString().padStart(digits, '0')}"
        val sign = when {
            minor < 0 -> "-"
            withSign && minor > 0 -> "+"
            else -> ""
        }
        return "$sign${symbol(code)}$body"
    }

    /** Compact form for chart labels and tight rows: "\u20AC1.2k", "\u20AC3.4M". */
    fun formatCompact(minor: Long, code: String): String {
        val major = toMajor(minor, code).toDouble()
        val a = abs(major)
        val sign = if (major < 0) "-" else ""
        val sym = symbol(code)
        return when {
            a >= 1_000_000 -> "$sign$sym%.1fM".format(a / 1_000_000)
            a >= 1_000 -> "$sign$sym%.1fk".format(a / 1_000)
            else -> "$sign$sym%.0f".format(a)
        }
    }

    private fun groupDigits(value: Long): String {
        val s = value.toString()
        if (s.length <= 3) return s
        val sb = StringBuilder()
        for ((i, c) in s.withIndex()) {
            if (i > 0 && (s.length - i) % 3 == 0) sb.append(',')
            sb.append(c)
        }
        return sb.toString()
    }

    /**
     * Convert [minor] of [fromCode] into minor units of [toCode], where [rateToBase] is the number
     * of *base* major units one [fromCode] major unit buys, and [toRateToBase] is the same for the
     * destination. Re-rounds to whole minor units of the destination, so digit counts differing
     * between the two currencies (EUR 2 -> JPY 0) can never leak a fractional yen.
     */
    fun convert(minor: Long, fromCode: String, rateToBase: Double, toCode: String, toRateToBase: Double): Long {
        if (toRateToBase == 0.0) return 0L
        val majorInBase = toMajor(minor, fromCode).toDouble() * rateToBase
        val majorInTarget = majorInBase / toRateToBase
        return BigDecimal(majorInTarget)
            .multiply(scaleOf(toCode))
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
    }

    /** Convert [minor] of [fromCode] into base-currency minor units at the stored [rateToBase]. */
    fun toBaseMinor(minor: Long, fromCode: String, rateToBase: Double, baseCode: String): Long {
        if (fromCode == baseCode && rateToBase == 1.0) return minor
        val majorInBase = toMajor(minor, fromCode).toDouble() * rateToBase
        return BigDecimal(majorInBase)
            .multiply(scaleOf(baseCode))
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
    }
}
