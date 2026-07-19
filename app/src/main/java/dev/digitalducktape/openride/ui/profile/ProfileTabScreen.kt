package dev.digitalducktape.openride.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.digitalducktape.openride.ui.common.ExportShare
import kotlinx.coroutines.launch

/**
 * Profile nav tab: shows the active rider, offers "Switch rider" (PRD P0-3/P1-6 — tap-to-
 * confirm before clearing the active profile and returning to profile select, so a stray tap
 * mid-session can't silently log the next ride under the wrong rider), quick shortcuts to
 * stock Android Wi-Fi/device settings (PRD P1-5, T12), and whole-database backup/restore
 * (PRD P1-8, T15).
 */
@Composable
fun ProfileTabScreen(
    viewModel: ProfileTabViewModel,
    onSwitchRider: () -> Unit,
    onRestoreComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeProfile by viewModel.activeProfile.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var pendingRestoreContent by remember { mutableStateOf<String?>(null) }
    var restoreError by remember { mutableStateOf<String?>(null) }
    var showSwitchConfirmation by remember { mutableStateOf(false) }

    val pickBackupFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        if (text != null) pendingRestoreContent = text
    }

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
            activeProfile?.let { profile ->
                val details = buildList {
                    profile.weightKg?.let { add("${it} kg") }
                    profile.ftp?.let { add("FTP ${it}W") }
                }
                if (details.isNotEmpty()) {
                    Text(
                        text = details.joinToString("  •  "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            OutlinedButton(
                onClick = { showSwitchConfirmation = true },
                modifier = Modifier.width(280.dp).padding(top = 32.dp),
            ) {
                Text("Switch rider")
            }

            SettingsShortcutsRow(modifier = Modifier.padding(top = 16.dp))

            Text(
                text = "Backup & restore",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 32.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                OutlinedButton(onClick = {
                    scope.launch {
                        val backup = viewModel.createBackupContent()
                        ExportShare.share(context, "openride_backup.json", backup, "application/json")
                    }
                }) {
                    Text("Back Up")
                }
                OutlinedButton(onClick = { pickBackupFile.launch("application/json") }) {
                    Text("Restore from file")
                }
            }
            restoreError?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    // Tap-to-confirm (PRD P1-6/T16): a stray tap on "Switch rider" shouldn't silently drop the
    // active profile mid-session — the whole point is that the *next* ride logged shouldn't
    // land under the wrong rider by accident.
    if (showSwitchConfirmation) {
        val riderName = activeProfile?.name ?: "the current rider"
        AlertDialog(
            onDismissRequest = { showSwitchConfirmation = false },
            title = { Text("Switch rider?") },
            text = { Text("You'll leave $riderName's session. Any ride you start next will be logged under whichever rider you pick.") },
            confirmButton = {
                TextButton(onClick = {
                    showSwitchConfirmation = false
                    viewModel.switchRider()
                    onSwitchRider()
                }) {
                    Text("Switch rider")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSwitchConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Destructive confirmation (tap-to-confirm, matching the End Ride pattern elsewhere in the
    // app) — restoring wipes every profile and ride currently on the device.
    pendingRestoreContent?.let { content ->
        AlertDialog(
            onDismissRequest = { pendingRestoreContent = null },
            title = { Text("Restore backup?") },
            text = { Text("This replaces ALL profiles and ride history on this device with the backup file's contents. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestoreContent = null
                    scope.launch {
                        viewModel.restoreFromContent(content).fold(
                            onSuccess = {
                                restoreError = null
                                onRestoreComplete()
                            },
                            onFailure = { error ->
                                restoreError = "Restore failed: ${error.message ?: "invalid backup file"}"
                            },
                        )
                    }
                }) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreContent = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}
