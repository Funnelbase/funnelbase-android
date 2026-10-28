package com.funnelbase.demo.trading.ui.format

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

class MoneyTest {

    /**
     * The point of pinning the locale rather than taking the device's. Run on a machine set to
     * tr-TR and a device-locale formatter would render `$1.234,56` — a dollar amount with
     * Turkish grouping. This test only fails if that regression is reintroduced.
     */
    @Test
    fun `formats as en-US regardless of the default locale`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("$1,234.56", Money.usd(1234.56))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `changes always carry a sign`() {
        assertEquals("+$12.50", Money.signedUsd(12.5))
        assertEquals("-$12.50", Money.signedUsd(-12.5))
        // Zero reads as up, matching ChangeText treating >= 0 as a rise.
        assertEquals("+$0.00", Money.signedUsd(0.0))
        assertEquals("+1.24%", Money.signedPercent(1.2449))
        assertEquals("-1.24%", Money.signedPercent(-1.2449))
    }

    @Test
    fun `compact notation switches at each magnitude`() {
        assertEquals("7.8M", Money.compact(7_800_000.0))
        assertEquals("1.53B", Money.compact(1_530_000_000.0))
        assertEquals("1.53T", Money.compact(1_530_000_000_000.0))
        assertEquals("950", Money.compact(950.0))
    }

    @Test
    fun `relative time collapses into the right unit`() {
        assertEquals("11m ago", Money.relativeTime(11))
        assertEquals("2h ago", Money.relativeTime(125))
        assertEquals("3d ago", Money.relativeTime(60 * 24 * 3 + 5))
    }
}
