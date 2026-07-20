package dev.digitalducktape.openride.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Base Material3 [Typography]. The system sans-serif default is used everywhere (no bundled
 * font files, per design-language constraints) — the Peloton-like voice comes from scale,
 * weight, and tracked-uppercase labels, not a custom typeface.
 */
val OpenRideTypography = Typography()

/**
 * Oversized numeral + label styles for ride screens (PRD P0-7, v2 redesign spec). The
 * numbers are the whole point of these screens, so they get sizes beyond Material3's type
 * scale; labels are small tracked caps, the signature texture of the bike app's metrics.
 */
object MetricTextStyles {
    val TimerDisplay = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Bold)

    /** The huge central current-output numeral on the plain in-ride screen. */
    val OutputHero = TextStyle(fontSize = 112.sp, fontWeight = FontWeight.Bold)

    val TileValueLarge = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Bold)

    /** The three big current values (cadence / output / resistance) in the metrics panel. */
    val PrimaryValue = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Bold)

    /** AVG / BEST readouts flanking a primary value. */
    val FlankValue = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

    /** Values in the secondary strip (speed / distance / total output / heart rate). */
    val SecondaryValue = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold)

    /** Current value inside a compact bar cell (video top strip timer). */
    val BarValue = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold)

    val TileValueSecondary = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium)

    /** Small tracked-uppercase label above a metric value. Callers pass uppercase text. */
    val TileLabel = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)

    /** Tiny tracked-caps label for units and AVG/BEST flanks. */
    val UnitLabel = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp)

    /** Tracked-caps section headers: channel shelves, screen eyebrows. */
    val SectionEyebrow = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
}
