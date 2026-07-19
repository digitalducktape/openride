package dev.digitalducktape.openride.core.heartrate

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import dev.digitalducktape.openride.core.sensor.ConnectionState
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Real [HeartRateDataSource] for a single paired BLE strap, over the standard Bluetooth GATT
 * **Heart Rate Service** (`0x180D`) / **Heart Rate Measurement** characteristic (`0x2A37`) —
 * every commodity chest strap and most fitness watches implement this (PRD P1-4, T17).
 *
 * ## Protocol
 * - Connect via [android.bluetooth.BluetoothDevice.connectGatt], discover services, find the
 *   Heart Rate Measurement characteristic, enable notifications on it (write
 *   `ENABLE_NOTIFICATION_VALUE` to its Client Characteristic Configuration descriptor,
 *   `0x2902` — the standard GATT subscribe dance, not anything strap-specific).
 * - Each notification's raw bytes are decoded by [HeartRateParser] (reimplemented from the
 *   public Bluetooth SIG spec).
 *
 * ## Connection state
 * Same contract as [dev.digitalducktape.openride.core.sensor.PelotonBikeDataSource]:
 * [ConnectionState.Connected] only once a real frame has been decoded (a GATT connection that
 * never yields data doesn't masquerade as live), [ConnectionState.Disconnected] on GATT
 * disconnect after having been connected, [ConnectionState.Unavailable] for anything that
 * prevents even trying (bad address, missing permission, adapter absent). [start] never
 * throws.
 *
 * **Hardware caveat (T17/#17): this project has no physical BLE heart-rate strap.** This class
 * is built correctly against the public `android.bluetooth` GATT client APIs and the
 * Bluetooth SIG spec, but the actual connect → discover → subscribe → notify sequence has
 * **not been exercised against a real strap** — that needs someone with a physical device to
 * confirm. [HeartRateParser]'s byte-level decoding, which this class merely feeds real GATT
 * bytes into, *is* independently unit-tested (see [HeartRateParserTest]).
 */
class BleHeartRateDataSource(
    private val context: Context,
    private val deviceAddress: String,
) : ManagedHeartRateDataSource {

    private val _bpm = MutableStateFlow<Int?>(null)
    override val bpm: StateFlow<Int?> = _bpm.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Unavailable)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var gatt: BluetoothGatt? = null

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    try {
                        g?.discoverServices()
                    } catch (e: SecurityException) {
                        Log.w(TAG, "discoverServices denied", e)
                        _connectionState.value = ConnectionState.Unavailable
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    val wasEverConnected = _connectionState.value == ConnectionState.Connected
                    _connectionState.value = if (wasEverConnected) ConnectionState.Disconnected else ConnectionState.Unavailable
                    releaseGatt()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt?, status: Int) {
            val characteristic = if (status == BluetoothGatt.GATT_SUCCESS) {
                g?.getService(HEART_RATE_SERVICE_UUID)?.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)
            } else {
                null
            }
            if (g == null || characteristic == null) {
                Log.w(TAG, "Heart Rate Measurement characteristic not found (status=$status)")
                _connectionState.value = ConnectionState.Unavailable
                return
            }
            try {
                g.setCharacteristicNotification(characteristic, true)
                val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                if (descriptor != null) {
                    // The pre-API-33 CCCD-write path (descriptor.value + writeDescriptor(descriptor))
                    // is deprecated in favor of writeDescriptor(descriptor, value), but minSdk
                    // here is 30, so this is the form that works across this app's whole
                    // supported range.
                    @Suppress("DEPRECATION")
                    run {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        g.writeDescriptor(descriptor)
                    }
                }
                // Stay Unavailable/Disconnected until a real frame arrives — see class doc.
            } catch (e: SecurityException) {
                Log.w(TAG, "Notification subscribe denied", e)
                _connectionState.value = ConnectionState.Unavailable
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            // The pre-API-33 callback signature (deprecated but still invoked by the platform
            // on every supported API level here, including 33+) — kept to one overload rather
            // than duplicating logic across both signatures.
            val bytes = characteristic?.value ?: return
            handleFrame(bytes)
        }
    }

    private fun handleFrame(bytes: ByteArray) {
        val measurement = try {
            HeartRateParser.parse(bytes)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Malformed Heart Rate Measurement frame, dropping", e)
            return
        }
        _bpm.value = measurement.bpm
        if (_connectionState.value != ConnectionState.Connected) {
            _connectionState.value = ConnectionState.Connected
        }
    }

    /**
     * Connects to [deviceAddress]. Safe on any device/state — an invalid address, a missing
     * adapter, or a missing runtime permission all degrade to [ConnectionState.Unavailable]
     * rather than throwing. Call once; call [stop] to release.
     */
    override fun start() {
        try {
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            if (adapter == null) {
                _connectionState.value = ConnectionState.Unavailable
                return
            }
            val device = adapter.getRemoteDevice(deviceAddress)
            gatt = device.connectGatt(context, false, gattCallback)
            if (gatt == null) {
                _connectionState.value = ConnectionState.Unavailable
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Connect denied — missing BLUETOOTH_CONNECT permission", e)
            _connectionState.value = ConnectionState.Unavailable
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Invalid BLE device address: $deviceAddress", e)
            _connectionState.value = ConnectionState.Unavailable
        }
    }

    /** Disconnects and releases the GATT client. Safe to call even if [start] never connected. */
    override fun stop() {
        releaseGatt()
        _connectionState.value = ConnectionState.Unavailable
    }

    private fun releaseGatt() {
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: SecurityException) {
            // Permission revoked mid-session — nothing more we can do, just drop the reference.
        }
        gatt = null
    }

    private companion object {
        const val TAG = "BleHeartRateDataSource"

        val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
        val HEART_RATE_MEASUREMENT_UUID: UUID = UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB")

        /** Standard GATT Client Characteristic Configuration Descriptor (CCCD), 0x2902. */
        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
    }
}
