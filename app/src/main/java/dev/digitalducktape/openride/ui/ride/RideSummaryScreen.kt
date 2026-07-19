package dev.digitalducktape.openride.ui.ride

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.digitalducktape.openride.ui.common.TimeFormat
import kotlinx.coroutines.launch

/**
 * Post-ride summary (PRD P0-5): duration, avg/max cadence, avg/max output, total kJ,
 * calories, and a Canvas line graph of power over the ride. Reused unmodified as history's
 * detail view (T8) — [viewModel] loads by ride id from Room either way, so this screen never
 * needs to know whether it's showing a ride just stopped or one from history.
 */
@Composable
fun RideSummaryScreen(
    viewModel: RideSummaryViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ride by viewModel.ride.collectAsState()
    val samples by viewModel.samples.collectAsState()
    val suggestedFtp by viewModel.suggestedFtp.collectAsState()
    val ftpApplied by viewModel.ftpApplied.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.load() }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(48.dp),
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
                Column(modifier = Modifier.fillMaxWidth().padding(top = 32.dp)) {
                    SummaryRow("Duration", TimeFormat.elapsed(current.durationSec))
                    SummaryRow("Avg cadence", "${current.avgCadence} rpm")
                    SummaryRow("Max cadence", "${current.maxCadence} rpm")
                    SummaryRow("Avg output", "${current.avgPower} W")
                    SummaryRow("Max output", "${current.maxPower} W")
                    SummaryRow("Total output", "%.1f kJ".format(current.outputKj))
                    SummaryRow("Calories", current.calories?.toString() ?: "--")

                    Text(
                        text = "Power over time",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                    )
                    PowerGraph(samples = samples, modifier = Modifier.fillMaxWidth())

                    if (suggestedFtp != null) {
                        FtpSuggestionCard(
                            suggestedFtp = suggestedFtp!!,
                            applied = ftpApplied,
                            onApply = { scope.launch { viewModel.applySuggestedFtp() } },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp),
                        )
                    }
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

/**
 * Shown once a ride's per-second series spans at least 20 minutes (PRD P1-3): 95% of the
 * ride's best 20-minute average power, offered as an FTP update for the rider who logged it.
 */
@Composable
private fun FtpSuggestionCard(
    suggestedFtp: Int,
    applied: Boolean,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Estimated FTP: ${suggestedFtp}W",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "95% of your best 20-minute average power this ride",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (applied) {
            Text(
                text = "FTP updated",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Button(onClick = onApply, modifier = Modifier.padding(top = 8.dp)) {
                Text("Update my FTP")
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
