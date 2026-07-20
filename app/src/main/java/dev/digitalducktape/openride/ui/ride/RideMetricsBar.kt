package dev.digitalducktape.openride.ui.ride

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.digitalducktape.openride.ui.theme.MetricTextStyles
import dev.digitalducktape.openride.ui.theme.OpenRideColors
import dev.digitalducktape.openride.ui.theme.zoneColor

/**
 * The two-tier in-ride metrics panel (v2 redesign spec's signature element, refined against
 * the bike app's in-class layout):
 *
 * - **Primary row** — the three big numbers: CADENCE / OUTPUT / RESISTANCE, each flanked
 *   left by its running AVG (with an up/down trend arrow vs. the current reading) and right
 *   by its BEST, unit in tracked caps under the value. Resistance has no BEST flank — the
 *   session aggregates deliberately don't track a max for it.
 * - **Secondary strip** — SPEED / DISTANCE / TOTAL OUTPUT (+ HEART RATE once a strap is
 *   paired), smaller, label-and-unit above value.
 *
 * Over video ([translucent]) the panel sits on a bottom-up black gradient rather than a
 * card, exactly like the class player; on the plain in-ride screen it gets a solid surface.
 * Values follow PRD P0-9: "--" whenever the sensor feed is down, never a fake zero.
 */
@Composable
fun RideMetricsBar(
    uiState: InRideUiState,
    translucent: Boolean,
    modifier: Modifier = Modifier,
) {
    val background = if (translucent) {
        Modifier.background(
            Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))),
        )
    } else {
        Modifier.background(OpenRideColors.Surface)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(background)
            .padding(top = if (translucent) 28.dp else 16.dp, bottom = 10.dp),
    ) {
        PrimaryRow(uiState)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 10.dp)
                .height(1.dp)
                .background(OpenRideColors.Divider),
        )
        SecondaryRow(uiState)
    }
}

@Composable
private fun PrimaryRow(uiState: InRideUiState, modifier: Modifier = Modifier) {
    val dash = "--"
    val live = uiState.sensorsAvailable

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PrimaryMetric(
            label = "CADENCE",
            unit = "RPM",
            current = if (live) "${uiState.metrics.cadenceRpm}" else dash,
            avg = "${uiState.aggregates.avgCadence}",
            avgTrend = trend(live, uiState.metrics.cadenceRpm, uiState.aggregates.avgCadence),
            best = "${uiState.aggregates.maxCadence}",
            modifier = Modifier.weight(1f),
        )
        PrimaryDivider()
        PrimaryMetric(
            label = "OUTPUT",
            unit = "WATTS",
            current = if (live) "${uiState.metrics.powerWatts}" else dash,
            currentColor = uiState.currentZone?.let { zoneColor(it) },
            avg = "${uiState.aggregates.avgPower}",
            avgTrend = trend(live, uiState.metrics.powerWatts, uiState.aggregates.avgPower),
            best = "${uiState.aggregates.maxPower}",
            modifier = Modifier.weight(1f),
        )
        PrimaryDivider()
        PrimaryMetric(
            label = "RESISTANCE",
            unit = "PERCENT",
            current = if (live) "${uiState.metrics.resistancePercent}" else dash,
            avg = "${uiState.aggregates.avgResistance}",
            avgTrend = trend(live, uiState.metrics.resistancePercent, uiState.aggregates.avgResistance),
            best = null,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Direction of the current reading vs. its running average, or `null` when sensors are
 *  down or the two are equal — drives the small arrow beside the AVG flank. */
private fun trend(live: Boolean, current: Int, avg: Int): Boolean? =
    if (!live || current == avg) null else current > avg

@Composable
private fun PrimaryMetric(
    label: String,
    unit: String,
    current: String,
    avg: String,
    avgTrend: Boolean?,
    best: String?,
    modifier: Modifier = Modifier,
    currentColor: Color? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Flank(label = "AVG", value = avg, trend = avgTrend)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 20.dp),
        ) {
            Text(
                text = label,
                style = MetricTextStyles.TileLabel,
                color = OpenRideColors.OnSurfaceVariant,
            )
            Text(
                text = current,
                style = MetricTextStyles.PrimaryValue,
                color = currentColor ?: OpenRideColors.OnBackground,
            )
            Text(
                text = unit,
                style = MetricTextStyles.UnitLabel,
                color = OpenRideColors.OnSurfaceVariant,
            )
        }
        if (best != null) {
            Flank(label = "BEST", value = best, trend = null)
        } else {
            // Keep the big value optically centered when there's no BEST readout.
            Box(modifier = Modifier.width(FlankWidth))
        }
    }
}

private val FlankWidth = 64.dp

@Composable
private fun Flank(label: String, value: String, trend: Boolean?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(FlankWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (trend != null) {
                // Core icons only ship the down-drop arrow; the up variant is it, flipped.
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = if (trend) "above average" else "below average",
                    tint = if (trend) OpenRideColors.Success else OpenRideColors.Accent,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(if (trend) 180f else 0f),
                )
            }
            Text(
                text = label,
                style = MetricTextStyles.UnitLabel,
                color = OpenRideColors.OnSurfaceVariant,
            )
        }
        Text(
            text = value,
            style = MetricTextStyles.FlankValue,
            color = OpenRideColors.OnBackground,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun SecondaryRow(uiState: InRideUiState, modifier: Modifier = Modifier) {
    val dash = "--"
    val live = uiState.sensorsAvailable

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SecondaryMetric(
            label = "SPEED",
            unit = "MPH",
            value = if (live) "%.1f".format(uiState.metrics.speedMph) else dash,
            modifier = Modifier.weight(1f),
        )
        SecondaryDivider()
        SecondaryMetric(
            label = "DISTANCE",
            unit = "MI",
            value = if (live) "%.2f".format(uiState.distanceMiles) else dash,
            modifier = Modifier.weight(1f),
        )
        SecondaryDivider()
        SecondaryMetric(
            label = "TOTAL OUTPUT",
            unit = "KJ",
            value = "%.0f".format(uiState.liveOutputKj),
            modifier = Modifier.weight(1f),
        )
        if (uiState.heartRateTileVisible) {
            SecondaryDivider()
            SecondaryMetric(
                label = "HEART RATE",
                unit = "BPM",
                value = uiState.heartRateBpm
                    ?.takeIf { uiState.heartRateConnected }
                    ?.toString() ?: dash,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SecondaryMetric(label: String, unit: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = MetricTextStyles.UnitLabel,
            color = OpenRideColors.OnSurfaceVariant,
        )
        Text(
            text = "($unit)",
            style = MetricTextStyles.UnitLabel,
            color = OpenRideColors.OnSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        Text(
            text = value,
            style = MetricTextStyles.SecondaryValue,
            color = OpenRideColors.OnBackground,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
private fun PrimaryDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(88.dp)
            .background(OpenRideColors.Divider),
    )
}

@Composable
private fun SecondaryDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(28.dp)
            .background(OpenRideColors.Divider),
    )
}
