package dev.digitalducktape.openride.core.heartrate

/**
 * A BLE device found while scanning for heart-rate straps (PRD P1-4, T17).
 *
 * @param address the device's BLE MAC address — what gets persisted as
 *   [dev.digitalducktape.openride.core.data.Profile.pairedHrDeviceAddress] once paired
 * @param name advertised device name, if any (some straps advertise with no name at all)
 */
data class BleDevice(
    val address: String,
    val name: String?,
)
