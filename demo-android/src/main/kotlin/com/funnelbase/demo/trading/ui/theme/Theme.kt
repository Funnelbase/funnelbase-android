package com.funnelbase.demo.trading.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = TradeDemoColors.Accent,
    onPrimary = TradeDemoColors.OnAccent,
    primaryContainer = TradeDemoColors.AccentMuted,
    onPrimaryContainer = TradeDemoColors.OnAccent,
    background = TradeDemoColors.Background,
    onBackground = TradeDemoColors.TextPrimary,
    surface = TradeDemoColors.Surface,
    onSurface = TradeDemoColors.TextPrimary,
    surfaceVariant = TradeDemoColors.SurfaceElevated,
    onSurfaceVariant = TradeDemoColors.TextSecondary,
    outline = TradeDemoColors.Outline,
    outlineVariant = TradeDemoColors.Outline,
    error = TradeDemoColors.Down,
)

/**
 * Dark only, and deliberately so: [isSystemInDarkTheme] is not consulted.
 *
 * A trading app is a dark-UI genre, the palette above was chosen against a near-black ground,
 * and shipping a half-considered light theme so the toggle "works" would look worse in light
 * mode than having no light mode at all. Dynamic colour is off for the same reason — the violet
 * is the point of the accent.
 */
@Composable
fun TradeDemoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = TradeDemoTypography,
        content = content,
    )
}

/**
 * Prices, quantities and totals are set in a monospaced face wherever they line up in a column,
 * so digits share a width and the decimal points form a straight edge down a list. Proportional
 * digits make a price column look ragged even when every value is correctly formatted.
 */
private val TradeDemoTypography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
    )
}

/** The numeric style: use for anything that sits in a column with other numbers. */
val NumericTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 15.sp,
)
