package com.financetracker.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class MoneyTest {

    @Test fun parsesBothDecimalConventions() {
        assertEquals(123456L, Money.parse("1,234.56", "EUR"))
        assertEquals(123456L, Money.parse("1.234,56", "EUR"))
        assertEquals(1250L, Money.parse("12,5", "EUR"))
        assertEquals(1250L, Money.parse("12.50", "EUR"))
        assertEquals(123400L, Money.parse("1,234", "EUR"))
        assertEquals(123456700L, Money.parse("1.234.567", "EUR"))
    }

    @Test fun parsesSignsSymbolsAndCodes() {
        assertEquals(-300L, Money.parse("-3", "EUR"))
        assertEquals(-400L, Money.parse("(4.00)", "EUR"))
        assertEquals(-400L, Money.parse("4.00-", "EUR"))
        assertEquals(1200L, Money.parse("€ 12", "EUR"))
        assertEquals(1200L, Money.parse("12.00 EUR", "EUR"))
        assertEquals(1200L, Money.parse("+12", "EUR"))
    }

    @Test fun respectsCurrencyDigits() {
        assertEquals(1234L, Money.parse("1,234", "JPY"))
        assertEquals(1234L, Money.parse("1.234", "KWD"))
        assertEquals(1500L, Money.parse("1500", "JPY"))
    }

    @Test fun rejectsNonsenseAndExcessPrecision() {
        assertNull(Money.parse("", "EUR"))
        assertNull(Money.parse("abc", "EUR"))
        assertNull(Money.parse("12x", "EUR"))
        assertNull(Money.parse("12 XYZ", "EUR"))
        assertNull(Money.parse("0.001", "EUR"))
        assertNull(Money.parse("1.2345", "EUR"))
        assertEquals(1200L, Money.parse("12.0000", "EUR"))
    }

    @Test fun formatsWithCurrencyDigits() {
        assertEquals("€1,234.50", Money.format(123450, "EUR", locale = Locale.UK))
        assertEquals("-€3.00", Money.format(-300, "EUR", locale = Locale.UK))
        assertEquals("+€3.00", Money.format(300, "EUR", signed = true, locale = Locale.UK))
        assertEquals("1234.50", Money.plain(123450, "EUR"))
        assertEquals("1500", Money.plain(1500, "JPY"))
    }
}
