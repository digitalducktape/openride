package dev.digitalducktape.openride.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * OpenRide's dark, high-contrast palette (PRD P0-7: "Peloton-style, not Peloton-copied").
 * Near-black background, white/high-contrast text, and a single red accent for calls to
 * action and "live/active" indicators — no Peloton assets, fonts, or brand colors are used.
 */
object OpenRideColors {
    val Background = Color(0xFF0C0C0E)
    val Surface = Color(0xFF1C1C1F)
    val SurfaceVariant = Color(0xFF29292D)
    val Accent = Color(0xFFE8442D)
    val AccentMuted = Color(0xFF7A281E)
    val OnBackground = Color(0xFFF5F5F7)
    val OnSurfaceVariant = Color(0xFFA4A4AA)
    val Warning = Color(0xFFF2B22B)
    val Success = Color(0xFF35C46A)
}
