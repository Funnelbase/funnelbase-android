package com.funnelbase.demo.trading.ui.format

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

/**
 * Every number the user sees passes through here.
 *
 * Fixed to `en-US`/USD rather than the device locale: the instruments are S&P 500 constituents
 * priced in dollars, and formatting them with the phone's locale would render a US dollar
 * amount with European grouping — the digits would be right and the currency would be a lie.
 *
 * The formatters are not thread-safe, so each is built per call rather than cached in a field.
 * At the rate a list row formats a price that is not a cost worth a `ThreadLocal`.
 */
object Money {

    private val LOCALE: Locale = Locale.US

    /** `$1,234.56`. */
    fun usd(value: Double): String =
        NumberFormat.getCurrencyInstance(LOCALE).apply {
            currency = java.util.Currency.getInstance("USD")
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(value)

    /** `+$1,234.56` / `-$1,234.56` — always signed, because a change without a sign is ambiguous. */
    fun signedUsd(value: Double): String =
        (if (value >= 0) "+" else "-") + usd(abs(value))

    /** `+1.24%` / `-1.24%`. */
    fun signedPercent(value: Double): String =
        String.format(LOCALE, "%+.2f%%", value)

    /** `1,250` — quantities are whole shares, so no decimals. */
    fun quantity(value: Int): String =
        NumberFormat.getIntegerInstance(LOCALE).format(value)

    /** `12.4M`, `1.02B` — full digits would dominate a row that is mostly prices. */
    fun compact(value: Double): String = when {
        abs(value) >= 1_000_000_000_000 -> String.format(LOCALE, "%.2fT", value / 1_000_000_000_000)
        abs(value) >= 1_000_000_000 -> String.format(LOCALE, "%.2fB", value / 1_000_000_000)
        abs(value) >= 1_000_000 -> String.format(LOCALE, "%.1fM", value / 1_000_000)
        abs(value) >= 1_000 -> String.format(LOCALE, "%.1fK", value / 1_000)
        else -> String.format(LOCALE, "%.0f", value)
    }

    fun compactUsd(value: Double): String = "$" + compact(value)

    /** `3m ago`, `4h ago`, `2d ago`. */
    fun relativeTime(minutesAgo: Int): String = when {
        minutesAgo < 60 -> "${minutesAgo}m ago"
        minutesAgo < 60 * 24 -> "${minutesAgo / 60}h ago"
        else -> "${minutesAgo / (60 * 24)}d ago"
    }
}
