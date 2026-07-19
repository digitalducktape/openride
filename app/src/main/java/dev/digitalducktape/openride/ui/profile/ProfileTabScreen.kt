package dev.digitalducktape.openride.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
 * Profile nav tab, minimal T11 version: shows the active rider. "Switch rider" (T6's job)
 * hangs off [ProfileTabViewModel.activeProfile] already exposing what it needs.
 */
@Composable
fun ProfileTabScreen(
    viewModel: ProfileTabViewModel,
    modifier: Modifier = Modifier,
) {
    val activeProfile by viewModel.activeProfile.collectAsState(initial = null)

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(
                        color = activeProfile?.let { Color(it.avatarColor) }
                            ?: MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = activeProfile?.avatarEmoji ?: "👤", fontSize = 40.sp)
            }
            Text(
                text = activeProfile?.name ?: "No rider selected",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
