package dev.digitalducktape.openride.ui.theme

import androidx.compose.ui.graphics.Color
import dev.digitalducktape.openride.core.ride.PowerZone

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

    /** Translucent black behind text/metrics overlaid on video (v2 Video Ride spec). */
    val ScrimOverlay = Color(0xB3000000)

    /** Hairline separators: nav bar top edge, metric-cell dividers. */
    val Divider = Color(0x33FFFFFF)
}

/**
 * Power-zone accent ramp (v2 redesign spec): cool-to-hot, one color per [PowerZone],
 * used for the in-ride zone chip and the central output block's accent. Standard
 * training-zone color language (recovery grey-blue through threshold red), not any
 * vendor's palette.
 */
fun zoneColor(zone: PowerZone?): Color = when (zone?.number) {
    1 -> Color(0xFF6E7B8B)
    2 -> Color(0xFF3D7BD9)
    3 -> Color(0xFF2FA8A0)
    4 -> Color(0xFF35C46A)
    5 -> Color(0xFFF2B22B)
    6 -> Color(0xFFEF7D2E)
    7 -> Color(0xFFE8442D)
    else -> OpenRideColors.OnSurfaceVariant
}
