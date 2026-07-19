package dev.digitalducktape.openride.ui.ride

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Minimal T11 placeholder for the in-ride screen — just the elapsed timer and an End Ride
 * button, enough to close the Quick Start loop. T7 replaces this with the full Peloton-style
 * metrics layout (cadence/resistance/output tiles, sensor-failure banner, pause/resume,
 * keep-screen-on).
 */
@Composable
fun InRideScreen(
    viewModel: InRideViewModel,
    onRideEnded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val elapsedSec by viewModel.elapsedSec.collectAsState()
    val scope = rememberCoroutineScope()

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = formatElapsed(elapsedSec),
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Button(
                onClick = {
                    scope.launch {
                        viewModel.endRide()
                        onRideEnded()
                    }
                },
                modifier = Modifier.padding(top = 32.dp),
            ) {
                Text("End Ride")
            }
        }
    }
}

private fun formatElapsed(totalSec: Int): String {
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return "%02d:%02d".format(minutes, seconds)
}
