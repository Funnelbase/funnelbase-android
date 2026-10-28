package com.funnelbase.demo.trading.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The demo's palette.
 *
 * The violet accents are the Funnelbase dashboard's own brand ramp, so the demo app and the analytics UI read as one product rather than as
 * two unrelated screenshots.
 *
 * Green and red are reserved strictly for price direction and are never the only signal — every
 * place they appear, a ▲/▼ glyph appears with them. Roughly one man in twelve cannot separate
 * the two, and "is my portfolio up" is not a question to answer with hue alone.
 */
object TradeDemoColors {
    val Accent = Color(0xFF8B5CF6)
    val AccentMuted = Color(0xFF6D3FD6)
    val OnAccent = Color(0xFFFFFFFF)

    val Background = Color(0xFF0B0B10)
    val Surface = Color(0xFF15151E)
    val SurfaceElevated = Color(0xFF1D1D28)
    val Outline = Color(0xFF2E2E3D)

    val TextPrimary = Color(0xFFF2F2F7)
    val TextSecondary = Color(0xFF9E9EB3)

    val Up = Color(0xFF3DDC97)
    val Down = Color(0xFFFF6B6B)

    val Pending = Color(0xFFF5C451)
    val Filled = Up
    val Cancelled = Color(0xFF7C7C93)
}
