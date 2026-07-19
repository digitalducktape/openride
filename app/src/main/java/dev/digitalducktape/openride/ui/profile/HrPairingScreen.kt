package dev.digitalducktape.openride.ui.profile

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.digitalducktape.openride.core.heartrate.BleDevice
import dev.digitalducktape.openride.core.heartrate.BlePermissionState
import dev.digitalducktape.openride.core.heartrate.reduceBlePermissionState
import dev.digitalducktape.openride.core.heartrate.requiredBlePermissions

/**
 * BLE heart-rate strap pairing screen (PRD P1-4, T17): requests the platform-appropriate BLE
 * runtime permission(s) (see [requiredBlePermissions]) with a clean rationale and graceful
 * degradation if denied, then scans and lists nearby straps for the rider to pick.
 *
 * **Hardware caveat (T17/#17)**: this project has no physical BLE heart-rate strap, so the
 * actual "a real strap shows up in the scan results and pairing sticks" path is unverified —
 * see [dev.digitalducktape.openride.core.heartrate.AndroidBleScanner]'s doc. The permission
 * flow, scan lifecycle, and persistence-to-profile logic are all exercised here and in
 * [HrPairingViewModelTest] against fakes.
 */
@Composable
fun HrPairingScreen(
    viewModel: HrPairingViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val permissions = remember { requiredBlePermissions() }
    var everRequested by remember { mutableStateOf(false) }

    fun refreshPermissionState() {
        val state = reduceBlePermissionState(
            permissions = permissions,
            granted = { permission ->
                ContextCompat.checkSelfPermission(context, permission) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            },
            shouldShowRationale = { permission ->
                val activity = context as? android.app.Activity
                activity != null &&
                    androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            },
            everRequested = everRequested,
        )
        viewModel.onPermissionState(state)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        everRequested = true
        refreshPermissionState()
    }

    LaunchedEffect(Unit) { refreshPermissionState() }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(48.dp)) {
            Text(
                text = "Pair heart-rate strap",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Text(
                text = uiState.pairedAddress?.let { "Paired: $it" } ?: "No strap paired",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (uiState.pairedAddress != null) {
                OutlinedButton(
                    onClick = { viewModel.forgetDevice() },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text("Forget strap")
                }
            }

            when (uiState.permissionState) {
                BlePermissionState.Granted -> {
                    ScanSection(
                        isScanning = uiState.isScanning,
                        devices = uiState.devices,
                        error = uiState.error,
                        onStartScan = { viewModel.startScan() },
                        onStopScan = { viewModel.stopScan() },
                        onSelectDevice = { viewModel.selectDevice(it) },
                    )
                }
                BlePermissionState.NotRequested -> {
                    Text(
                        text = "Bluetooth permission is needed to scan for heart-rate straps.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                    Button(
                        onClick = { permissionLauncher.launch(permissions) },
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text("Grant Bluetooth permission")
                    }
                }
                BlePermissionState.ShouldShowRationale -> {
                    Text(
                        text = "OpenRide needs Bluetooth (and, on this device, location) " +
                            "permission to find nearby heart-rate straps. It's only used to " +
                            "scan for and connect to the strap you pick — never to track your " +
                            "location.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                    Button(
                        onClick = { permissionLauncher.launch(permissions) },
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text("Grant permission")
                    }
                }
                BlePermissionState.PermanentlyDenied -> {
                    Text(
                        text = "Bluetooth permission was denied. Heart-rate pairing will stay " +
                            "unavailable until it's enabled from this app's system Settings page.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                    Button(
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            )
                            context.startActivity(intent)
                        },
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text("Open Settings")
                    }
                }
            }

            OutlinedButton(onClick = onDone, modifier = Modifier.padding(top = 32.dp)) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun ScanSection(
    isScanning: Boolean,
    devices: List<BleDevice>,
    error: String?,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onSelectDevice: (BleDevice) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(top = 24.dp)) {
        Button(onClick = { if (isScanning) onStopScan() else onStartScan() }) {
            Text(if (isScanning) "Stop scan" else "Scan for straps")
        }
        if (isScanning) {
            Column(
                modifier = Modifier.padding(top = 12.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        }
        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        if (devices.isEmpty() && !isScanning && error == null) {
            Text(
                text = "No straps found yet. Make sure yours is in pairing mode and tap Scan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            items(devices, key = { it.address }) { device ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    onClick = { onSelectDevice(device) },
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = device.name ?: "Unknown device",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = device.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
