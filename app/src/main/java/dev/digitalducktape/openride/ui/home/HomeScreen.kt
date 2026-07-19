package dev.digitalducktape.openride.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Home tab (PRD P0-7): greeting with the active rider and a prominent Quick Start button.
 * The curated-classes browser (PRD P0-6) lives in the Classes tab (T10) rather than
 * duplicated here — see [dev.digitalducktape.openride.ui.classes.ClassesScreen].
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onQuickStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeProfile by viewModel.activeProfile.collectAsState(initial = null)

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(48.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            color = activeProfile?.let { Color(it.avatarColor) }
                                ?: MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = activeProfile?.avatarEmoji ?: "👤", fontSize = 28.sp)
                }
                Text(
                    text = "Hi, ${activeProfile?.name ?: "rider"}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }

            Button(
                onClick = { if (viewModel.startQuickRide()) onQuickStart() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp)
                    .height(88.dp),
            ) {
                Text(
                    text = "Quick Start",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
