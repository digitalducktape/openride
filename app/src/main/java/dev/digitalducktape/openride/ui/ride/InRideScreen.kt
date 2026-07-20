package dev.digitalducktape.openride.ui.ride

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.digitalducktape.openride.core.ride.RideGoal
import dev.digitalducktape.openride.core.route.RoutePosition
import dev.digitalducktape.openride.ui.common.TimeFormat
import dev.digitalducktape.openride.ui.theme.MetricTextStyles
import dev.digitalducktape.openride.ui.theme.OpenRideColors
import dev.digitalducktape.openride.ui.theme.zoneColor
import kotlinx.coroutines.launch

/**
 * The core screen (PRD P0-7/P0-9/P0-10, v2 redesign spec): bike-app arrangement — elapsed
 * timer top-center with goal progress beneath it, the huge zone-colored current-output
 * numeral in the middle, and the shared [RideMetricsBar] pinned along the bottom edge.
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
            if (uiState.autoPaused) {
                AutoPausedBanner()
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = TimeFormat.elapsed(uiState.elapsedSec),
                        style = MetricTextStyles.TimerDisplay,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    if (uiState.goal != RideGoal.None) {
                        GoalProgressRow(
                            goal = uiState.goal,
                            progress = uiState.goalProgress ?: 0.0,
                            modifier = Modifier.width(360.dp).padding(top = 8.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 24.dp, end = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { if (uiState.isPaused) viewModel.resume() else viewModel.pause() },
                    ) {
                        Text(if (uiState.isPaused) "Resume" else "Pause")
                    }
                    Button(onClick = { showEndConfirmation = true }) {
                        Text("End Ride")
                    }
                }

                OutputHero(
                    uiState = uiState,
                    modifier = Modifier.align(Alignment.Center),
                )

                uiState.routePosition?.let { position ->
                    RouteProgressRow(
                        routeName = uiState.route?.name,
                        position = position,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 40.dp, vertical = 12.dp),
                    )
                }
            }

            RideMetricsBar(uiState = uiState, translucent = false)
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

/**
 * The screen's centerpiece: current output in watts, zone-colored once the rider has an
 * FTP set, with a zone chip beneath. "--" (never a fake zero, P0-9) while sensors are down.
 */
@Composable
private fun OutputHero(uiState: InRideUiState, modifier: Modifier = Modifier) {
    val zone = uiState.currentZone
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "OUTPUT (W)",
            style = MetricTextStyles.TileLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (uiState.sensorsAvailable) "${uiState.metrics.powerWatts}" else "--",
            style = MetricTextStyles.OutputHero,
            color = if (zone != null) zoneColor(zone) else MaterialTheme.colorScheme.onBackground,
        )
        if (zone != null) {
            Box(
                modifier = Modifier
                    .background(color = zoneColor(zone).copy(alpha = 0.18f), shape = RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "ZONE ${zone.number} · ${zone.label.uppercase()}",
                    style = MetricTextStyles.TileLabel,
                    color = zoneColor(zone),
                )
            }
        } else if (uiState.ftp == null) {
            Text(
                text = "Set an FTP in your profile to see power zones",
                style = MetricTextStyles.TileValueSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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

/** Shown while the ride auto-paused on freewheel (PRD #20/T20) — pedaling auto-resumes it,
 *  or the rider can tap Resume. Distinct from the sensor-failure banner. */
@Composable
private fun AutoPausedBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Auto-paused — pedal to resume",
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Progress along the loaded GPX route (PRD #21/T21) — grade, percent complete and distance
 * remaining, advancing purely with accumulated ride distance. Display-only: Gen 2 has no
 * auto-resistance, so nothing here drives the bike.
 */
@Composable
private fun RouteProgressRow(
    routeName: String?,
    position: RoutePosition,
    modifier: Modifier = Modifier,
) {
    val remainingMiles = position.remainingMeters / METERS_PER_MILE
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = routeName ?: "Route",
                style = MetricTextStyles.TileLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "%+.1f%% grade  •  %.2f mi to go".format(position.gradePercent, remainingMiles),
                style = MetricTextStyles.TileLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { position.progressFraction.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

/** Miles are the app's display unit; routes are measured in metres. */
private const val METERS_PER_MILE = 1609.344

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
