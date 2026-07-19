package dev.digitalducktape.openride

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/**
 * Single activity for the whole app (per project scaffold ticket). Screens for later
 * tickets (profile select, ride, history, classes) will be composed as destinations
 * within this activity rather than as separate Activities.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenRideTheme {
                Scaffold { innerPadding ->
                    OpenRideHome(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun OpenRideHome(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "OpenRide")
        }
    }
}

@Composable
fun OpenRideTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

@Preview(showBackground = true)
@Composable
private fun OpenRideHomePreview() {
    OpenRideTheme {
        OpenRideHome()
    }
}
