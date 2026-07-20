package dev.digitalducktape.openride.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.digitalducktape.openride.core.backup.MediaStoreAutoBackupStore
import dev.digitalducktape.openride.core.profile.WeightUnits
import dev.digitalducktape.openride.core.route.RouteHolder
import dev.digitalducktape.openride.ui.common.ExportShare
import dev.digitalducktape.openride.ui.common.ProfileAvatar
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
    onEditProfile: () -> Unit,
    onSwitchRider: () -> Unit,
    onRestoreComplete: () -> Unit,
    onManageHeartRateStrap: () -> Unit,
    onManageAppUpdates: () -> Unit,
    onManageContentSources: () -> Unit,
    routeHolder: RouteHolder,
    modifier: Modifier = Modifier,
) {
    val activeProfile by viewModel.activeProfile.collectAsState(initial = null)
    val activeRoute by routeHolder.activeRoute.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var pendingRestoreContent by remember { mutableStateOf<String?>(null) }
    var restoreError by remember { mutableStateOf<String?>(null) }
    var showSwitchConfirmation by remember { mutableStateOf(false) }

    var routeError by remember { mutableStateOf<String?>(null) }

    // GPX route import (PRD #21/T21). GetContent rather than a typed picker because GPX has no
    // reliably-registered MIME type across file providers — "*/*" keeps every provider listing it.
    val pickRouteFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val loaded = runCatching {
            context.contentResolver.openInputStream(uri)?.use { routeHolder.load(it) }
        }.getOrNull()
        routeError = if (loaded == null) "Couldn't read that file as a GPX route" else null
    }

    val pickBackupFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        if (text != null) pendingRestoreContent = text
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            // Scrollable: with Edit profile added, the stack can exceed the screen.
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProfileAvatar(profile = activeProfile, size = 96.dp, emojiSize = 40.sp)
            Text(
                text = activeProfile?.name ?: "No rider selected",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 16.dp),
            )
            activeProfile?.let { profile ->
                val details = buildList {
                    profile.weightKg?.let { add("${WeightUnits.formatLbs(it)} lb") }
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
                onClick = onEditProfile,
                modifier = Modifier.width(280.dp).padding(top = 32.dp),
            ) {
                Text("Edit profile")
            }

            OutlinedButton(
                onClick = { showSwitchConfirmation = true },
                modifier = Modifier.width(280.dp).padding(top = 16.dp),
            ) {
                Text("Switch rider")
            }

            SettingsShortcutsRow(modifier = Modifier.padding(top = 16.dp))

            OutlinedButton(
                onClick = onManageHeartRateStrap,
                modifier = Modifier.width(280.dp).padding(top = 16.dp),
            ) {
                Text(
                    if (activeProfile?.pairedHrDeviceAddress != null) {
                        "Heart-rate strap: paired"
                    } else {
                        "Pair heart-rate strap"
                    },
                )
            }

            OutlinedButton(
                onClick = onManageAppUpdates,
                modifier = Modifier.width(280.dp).padding(top = 16.dp),
            ) {
                Text("App updates")
            }

            OutlinedButton(
                onClick = onManageContentSources,
                modifier = Modifier.width(280.dp).padding(top = 16.dp),
            ) {
                Text("Content sources")
            }

            Text(
                text = "Route (GPX)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 32.dp),
            )
            activeRoute?.let { route ->
                Text(
                    text = "%s  •  %.2f mi".format(
                        route.name ?: "Loaded route",
                        route.totalDistanceMeters / 1609.344,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            routeError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                OutlinedButton(onClick = { pickRouteFile.launch("*/*") }) {
                    Text(if (activeRoute == null) "Load Route" else "Change Route")
                }
                if (activeRoute != null) {
                    OutlinedButton(onClick = {
                        routeHolder.clear()
                        routeError = null
                    }) {
                        Text("Clear Route")
                    }
                }
            }

            Text(
                text = "Backup & restore",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 32.dp),
            )
            Text(
                text = "Also backed up automatically after every ride to " +
                    MediaStoreAutoBackupStore.RELATIVE_PATH +
                    MediaStoreAutoBackupStore.fileNameFor(context.packageName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
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
