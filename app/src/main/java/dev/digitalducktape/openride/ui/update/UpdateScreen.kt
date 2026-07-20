package dev.digitalducktape.openride.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Opt-in self-updater (PRD #22/T22): point OpenRide at a JSON manifest URL, check it, download
 * the APK, and hand it to the system installer — so updating the bike's tablet doesn't require
 * re-running ADB.
 *
 * Each step is a separate explicit tap (Check → Download → Install); nothing chains on its own,
 * and the final install is the platform installer's own confirmation UI.
 */
@Composable
fun UpdateScreen(
    viewModel: UpdateViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(48.dp)) {
            Text(
                text = "App updates",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Installed: ${uiState.currentVersionName} (build ${uiState.currentVersionCode})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "Optional. With no URL set, OpenRide never checks for updates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            OutlinedTextField(
                value = uiState.urlDraft,
                onValueChange = viewModel::onUrlDraftChange,
                label = { Text("Update manifest URL (https)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                OutlinedButton(
                    onClick = { viewModel.saveUrl() },
                    enabled = !uiState.isBusy,
                ) {
                    Text("Save URL")
                }
                if (uiState.manifestUrl != null) {
                    OutlinedButton(
                        onClick = { viewModel.clearUrl() },
                        enabled = !uiState.isBusy,
                    ) {
                        Text("Clear")
                    }
                }
                Button(
                    onClick = { scope.launch { viewModel.checkForUpdate() } },
                    enabled = uiState.manifestUrl != null && !uiState.isBusy,
                ) {
                    Text("Check for updates")
                }
            }

            uiState.status?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            uiState.available?.notes?.let { notes ->
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                if (uiState.available != null && uiState.downloadedApk == null) {
                    Button(
                        onClick = { scope.launch { viewModel.downloadUpdate() } },
                        enabled = !uiState.isBusy,
                    ) {
                        Text("Download")
                    }
                }
                if (uiState.downloadedApk != null) {
                    Button(
                        onClick = {
                            // Hands off to the platform installer, which shows its own
                            // confirmation — this tap requests, it does not install.
                            viewModel.installIntent()?.let { context.startActivity(it) }
                        },
                        enabled = !uiState.isBusy,
                    ) {
                        Text("Install")
                    }
                }
            }

            OutlinedButton(onClick = onDone, modifier = Modifier.padding(top = 32.dp)) {
                Text("Done")
            }
        }
    }
}
