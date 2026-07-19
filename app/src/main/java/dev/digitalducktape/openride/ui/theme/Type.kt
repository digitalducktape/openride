package dev.digitalducktape.openride.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Base Material3 [Typography]. The system sans-serif default is used everywhere (no bundled
 * font files, per design-language constraints) — big, bold numerals come from weight/size,
 * not a custom typeface.
 */
val OpenRideTypography = Typography()

/**
 * Extra oversized numeral styles for the ride screen's timer and metric tiles — bigger than
 * anything Material3's default type scale offers, since those numbers are the whole point of
 * the screen (PRD P0-7: big bold numerals).
 */
object MetricTextStyles {
    val TimerDisplay = TextStyle(fontSize = 88.sp, fontWeight = FontWeight.Bold)
    val TileValueLarge = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Bold)
    val TileValueSecondary = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium)
    val TileLabel = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
}
