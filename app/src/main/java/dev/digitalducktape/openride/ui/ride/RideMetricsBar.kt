package dev.digitalducktape.openride.ui.ride

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.digitalducktape.openride.ui.theme.MetricTextStyles
import dev.digitalducktape.openride.ui.theme.OpenRideColors
import dev.digitalducktape.openride.ui.theme.zoneColor

/**
 * The bottom in-ride metrics strip (v2 redesign spec's signature element): evenly divided
 * cells — tracked-caps label, big current value, small avg/max subline — separated by
 * hairline dividers. Shared between the plain in-ride screen (solid background) and the
 * video-ride overlay (translucent scrim), so the two ride surfaces read as one system.
 *
 * Values follow PRD P0-9: "--" whenever the sensor feed is down, never a fake zero.
 */
@Composable
fun RideMetricsBar(
    uiState: InRideUiState,
    translucent: Boolean,
    modifier: Modifier = Modifier,
) {
    val dash = "--"
    val live = uiState.sensorsAvailable

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (translucent) OpenRideColors.ScrimOverlay else OpenRideColors.Surface)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetricCell(
            label = "CADENCE",
            value = if (live) "${uiState.metrics.cadenceRpm}" else dash,
            sub = "AVG ${uiState.aggregates.avgCadence} · MAX ${uiState.aggregates.maxCadence}",
            modifier = Modifier.weight(1f),
        )
        CellDivider()
        MetricCell(
            label = "OUTPUT",
            value = if (live) "${uiState.metrics.powerWatts}" else dash,
            sub = "AVG ${uiState.aggregates.avgPower} · MAX ${uiState.aggregates.maxPower}",
            valueColor = uiState.currentZone?.let { zoneColor(it) },
            modifier = Modifier.weight(1f),
        )
        CellDivider()
        MetricCell(
            label = "RESISTANCE",
            value = if (live) "${uiState.metrics.resistancePercent}" else dash,
            sub = "AVG ${uiState.aggregates.avgResistance}",
            modifier = Modifier.weight(1f),
        )
        CellDivider()
        MetricCell(
            label = "SPEED",
            value = if (live) "%.1f".format(uiState.metrics.speedMph) else dash,
            sub = "MPH",
            modifier = Modifier.weight(1f),
        )
        CellDivider()
        MetricCell(
            label = "DISTANCE",
            value = if (live) "%.2f".format(uiState.distanceMiles) else dash,
            sub = "MI",
            modifier = Modifier.weight(1f),
        )
        if (uiState.heartRateTileVisible) {
            CellDivider()
            MetricCell(
                label = "HEART RATE",
                value = uiState.heartRateBpm
                    ?.takeIf { uiState.heartRateConnected }
                    ?.toString() ?: dash,
                sub = "BPM",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MetricCell(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MetricTextStyles.TileLabel,
            color = OpenRideColors.OnSurfaceVariant,
        )
        Text(
            text = value,
            style = MetricTextStyles.BarValue,
            color = valueColor ?: OpenRideColors.OnBackground,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = sub,
            style = MetricTextStyles.TileValueSecondary,
            color = OpenRideColors.OnSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun CellDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(56.dp)
            .background(OpenRideColors.Divider),
    )
}
