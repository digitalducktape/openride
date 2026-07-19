package dev.digitalducktape.openride.ui.ride

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Post-ride summary, T7 minimal version: duration + avg/max cadence/output + total kJ +
 * calories. The Canvas power-over-time graph and reuse as history's detail view (loading
 * samples) land in T8.
 */
@Composable
fun RideSummaryScreen(
    viewModel: RideSummaryViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ride by viewModel.ride.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Ride complete",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            val current = ride
            if (current == null) {
                Text(
                    text = "Loading…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 32.dp),
                )
            } else {
                Column(modifier = Modifier.padding(top = 32.dp)) {
                    SummaryRow("Duration", formatDuration(current.durationSec))
                    SummaryRow("Avg cadence", "${current.avgCadence} rpm")
                    SummaryRow("Max cadence", "${current.maxCadence} rpm")
                    SummaryRow("Avg output", "${current.avgPower} W")
                    SummaryRow("Max output", "${current.maxPower} W")
                    SummaryRow("Total output", "%.1f kJ".format(current.outputKj))
                    SummaryRow("Calories", current.calories?.toString() ?: "--")
                }
            }

            Button(
                onClick = {
                    viewModel.dismiss()
                    onDismiss()
                },
                modifier = Modifier.padding(top = 40.dp),
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

private fun formatDuration(totalSec: Int): String {
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return "%02d:%02d".format(minutes, seconds)
}
