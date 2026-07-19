package dev.digitalducktape.openride.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * History tab (PRD P0-5): the active profile's rides, newest first (date, duration, output
 * kJ, avg cadence). Tapping a row opens the same [dev.digitalducktape.openride.ui.ride.RideSummaryScreen]
 * used right after a ride, loading that ride's samples from Room only when opened — the list
 * itself never touches per-ride sample data, which is what keeps 100+ rows smooth.
 */
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onRideSelected: (rideId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows by viewModel.rows.collectAsState(initial = emptyList())

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(48.dp)) {
            Text(
                text = "History",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            if (rows.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No rides yet — finish a ride to see it here",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.padding(top = 24.dp)) {
                    items(rows, key = { it.rideId }) { row ->
                        RideHistoryListItem(row = row, onClick = { onRideSelected(row.rideId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RideHistoryListItem(row: RideHistoryRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .background(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = row.dateLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = row.durationLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = row.outputKjLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = row.avgCadenceLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
