package dev.digitalducktape.openride.ui.ride

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.digitalducktape.openride.core.ride.RideGoal
import dev.digitalducktape.openride.ui.common.TimeFormat
import dev.digitalducktape.openride.ui.theme.MetricTextStyles
import dev.digitalducktape.openride.ui.theme.OpenRideColors
import kotlinx.coroutines.launch

/**
 * The core screen (PRD P0-7/P0-9/P0-10): big elapsed timer, cadence/resistance/output tiles
 * with current+avg+max, a distance/speed/live-output secondary row, and pause/end controls.
 * Keep-screen-on is driven from [dev.digitalducktape.openride.MainActivity] observing
 * [dev.digitalducktape.openride.core.ride.RideSessionManager.isRideActive] directly, not
 * from this screen, so the flag can't be left dangling across navigation/recomposition.
 */
@Composable
fun InRideScreen(
    viewModel: InRideViewModel,
    onRideEnded: (rideId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState(initial = InRideUiState())
    val scope = rememberCoroutineScope()
    var showEndConfirmation by remember { mutableStateOf(false) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!uiState.sensorsAvailable) {
                SensorFailureBanner()
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 40.dp, vertical = 24.dp),
            ) {
                Text(
                    text = TimeFormat.elapsed(uiState.elapsedSec),
                    style = MetricTextStyles.TimerDisplay,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                ) {
                    val dash = "--"
                    MetricTile(
                        label = "CADENCE (rpm)",
                        currentValue = if (uiState.sensorsAvailable) "${uiState.metrics.cadenceRpm}" else dash,
                        avgValue = "${uiState.aggregates.avgCadence}",
                        maxValue = "${uiState.aggregates.maxCadence}",
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        label = "RESISTANCE (%)",
                        currentValue = if (uiState.sensorsAvailable) "${uiState.metrics.resistancePercent}" else dash,
                        avgValue = "${uiState.aggregates.avgResistance}",
                        maxValue = null,
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        label = "OUTPUT (W)",
                        currentValue = if (uiState.sensorsAvailable) "${uiState.metrics.powerWatts}" else dash,
                        avgValue = "${uiState.aggregates.avgPower}",
                        maxValue = "${uiState.aggregates.maxPower}",
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        label = "ZONE",
                        currentValue = uiState.currentZone?.let { "Z${it.number}" } ?: dash,
                        avgValue = uiState.currentZone?.label ?: (if (uiState.ftp == null) "No FTP set" else "--"),
                        maxValue = null,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (uiState.goal != RideGoal.None) {
                    GoalProgressRow(
                        goal = uiState.goal,
                        progress = uiState.goalProgress ?: 0.0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    SecondaryStat(
                        label = "Distance",
                        value = if (uiState.sensorsAvailable) {
                            "%.2f mi".format(uiState.distanceMiles)
                        } else {
                            "--"
                        },
                    )
                    SecondaryStat(
                        label = "Speed",
                        value = if (uiState.sensorsAvailable) {
                            "%.1f mph".format(uiState.metrics.speedMph)
                        } else {
                            "--"
                        },
                    )
                    SecondaryStat(
                        label = "Output",
                        value = "%.1f kJ".format(uiState.liveOutputKj),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedButton(
                        onClick = { if (uiState.isPaused) viewModel.resume() else viewModel.pause() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (uiState.isPaused) "Resume" else "Pause")
                    }
                    Button(
                        onClick = { showEndConfirmation = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("End Ride")
                    }
                }
            }
        }
    }

    if (showEndConfirmation) {
        AlertDialog(
            onDismissRequest = { showEndConfirmation = false },
            title = { Text("End ride?") },
            text = { Text("This will stop and save your ride.") },
            confirmButton = {
                TextButton(onClick = {
                    showEndConfirmation = false
                    scope.launch {
                        val ride = viewModel.endRide()
                        if (ride != null) onRideEnded(ride.id)
                    }
                }) {
                    Text("End Ride")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SensorFailureBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(OpenRideColors.Warning)
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Sensors not detected — check bike connection",
            color = OpenRideColors.Background,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Live progress toward the rider's pre-ride goal (PRD P1-3). */
@Composable
private fun GoalProgressRow(goal: RideGoal, progress: Double, modifier: Modifier = Modifier) {
    val label = when (goal) {
        is RideGoal.Duration -> "Goal: ${TimeFormat.elapsed(goal.targetSec)}"
        is RideGoal.Output -> "Goal: %.0f kJ".format(goal.targetKj)
        RideGoal.None -> ""
    }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MetricTextStyles.TileLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MetricTextStyles.TileLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { progress.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SecondaryStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MetricTextStyles.TileLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MetricTextStyles.TileValueSecondary,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
