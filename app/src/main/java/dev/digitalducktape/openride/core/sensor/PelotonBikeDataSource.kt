package dev.digitalducktape.openride.core.sensor

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Real [BikeDataSource] for the Bike Gen 2's onboard sensors (PRD P0-2, issue #3),
 * implemented against the undocumented internal Android system service the stock Peloton
 * app itself uses to read cadence/resistance/power off the flywheel's serial connection —
 * the technique [grupetto](https://github.com/selalipop/grupetto) demonstrated and this
 * project cites as its foundation for subscription-independent sensor access.
 *
 * ############################################################################
 * # THIS CLASS IS UNVERIFIED ON HARDWARE. Refs #3 — issue stays OPEN.        #
 * #                                                                          #
 * # It was written by studying grupetto's public source (no physical bike   #
 * # available in this environment), and is a best-effort port of the        #
 * # *mechanism*, not a copy of grupetto's code (grupetto has no LICENSE      #
 * # file, i.e. all-rights-reserved by default — see PRD's licensing note).  #
 * # Every constant, field layout, and scaling factor below needs on-device  #
 * # confirmation before this can be trusted. See the TODOs throughout.      #
 * ############################################################################
 *
 * ## Two known mechanisms (from grupetto's source), and why this targets the newer one
 *
 * grupetto contains two independent sensor-access code paths, which read like two different
 * bike-generation eras:
 *
 * 1. **Legacy** — binds `Intent("android.intent.action.peloton.SensorData")` /
 *    package `com.peloton.service.SensorData`, then talks to it via an `android.os.Messenger`:
 *    send a `Message` whose `what` selects a repeating command (rpm=1, power=2,
 *    resistance=3), with a `replyTo` Messenger receiving `Bundle` replies containing a raw
 *    float value, an epoch timestamp, and (when timed out) a sentinel string.
 * 2. **Newer** — binds a component in package `com.onepeloton.affernetservice`
 *    (`AffernetService`), then registers a callback `Binder` via a raw AIDL-style
 *    `Parcel`/`IBinder.transact()` call (interface token `...affernetservice.IV1Interface`),
 *    which the service invokes back with a large `Parcelable` bundle of raw bike fields
 *    (rpm, power, resistance, plus a lot of calibration/error/firmware metadata we don't
 *    need) on every sensor update.
 *
 * Since PRD/issue #3 confirms this hardware is specifically **Gen 2** (not the original
 * Gen 1 bike), the newer `affernetservice` mechanism is the more plausible match — package
 * naming reads like a later Peloton software generation. **This is an inference from public
 * reverse-engineering, not a verified fact about this exact tablet's firmware build** — see
 * `docs/DECISIONS.md` for the reasoning, and TODO(#3) below for what confirms or refutes it.
 *
 * ## Failure handling
 *
 * Every failure mode (service package not present, bind denied, binder death, malformed
 * callback data, transact failure) degrades to [ConnectionState.Unavailable] — this class
 * must never throw out of [start], and never fabricate a zero/stale reading dressed up as
 * live data (PRD P0-9).
 */
class PelotonBikeDataSource(
    private val context: Context,
) : BikeDataSource {

    private val _metrics = MutableStateFlow(BikeMetrics.ZERO)
    override val metrics: StateFlow<BikeMetrics> = _metrics.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Unavailable)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var callbackBinder: Binder? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.i(TAG, "Peloton sensor service connected: $name")
            if (binder == null) {
                _connectionState.value = ConnectionState.Unavailable
                return
            }
            try {
                registerCallback(binder)
                _connectionState.value = ConnectionState.Connected
            } catch (e: RemoteException) {
                Log.w(TAG, "registerCallback transaction failed", e)
                _connectionState.value = ConnectionState.Unavailable
            } catch (e: RuntimeException) {
                // TODO(#3): broad catch is deliberate here — with the exact interface
                // descriptor/transaction codes unverified, a mismatch could throw anything
                // from a SecurityException (bad interface token) to an IllegalStateException
                // (Parcel underflow reading a shorter reply than expected). Once verified on
                // device, narrow this to the specific exceptions actually observed.
                Log.w(TAG, "Failed to register sensor callback with Peloton service", e)
                _connectionState.value = ConnectionState.Unavailable
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "Peloton sensor service disconnected: $name")
            _connectionState.value = ConnectionState.Disconnected
        }

        override fun onBindingDied(name: ComponentName?) {
            Log.w(TAG, "Peloton sensor service binding died: $name")
            _connectionState.value = ConnectionState.Unavailable
        }

        override fun onNullBinding(name: ComponentName?) {
            Log.w(TAG, "Peloton sensor service returned a null binding: $name")
            _connectionState.value = ConnectionState.Unavailable
        }
    }

    /**
     * Attempts to bind the sensor service. Safe to call on any device, including an
     * emulator or any non-Peloton hardware — [Context.bindService] returning `false`, or the
     * service package simply not existing, both degrade to [ConnectionState.Unavailable]
     * rather than throwing. Call once (AppContainer does this at construction when the
     * real-sensor toggle is on); call [stop] when the app no longer needs live metrics.
     */
    fun start() {
        // TODO(#3): VERIFY ON DEVICE — confirm this component actually exists on the bike's
        // firmware (e.g. `adb shell dumpsys package com.onepeloton.affernetservice`) before
        // trusting this path. If it doesn't, fall back to trying the legacy
        // com.peloton.service.SensorData / Messenger mechanism documented above instead.
        try {
            val intent = Intent(SERVICE_ACTION).apply {
                setPackage(SERVICE_PACKAGE)
            }
            val bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            if (!bound) {
                Log.w(TAG, "bindService returned false for $SERVICE_PACKAGE — service not available")
                _connectionState.value = ConnectionState.Unavailable
            }
            // If bindService returned true, onServiceConnected/onNullBinding will resolve
            // connectionState asynchronously — nothing further to do here.
        } catch (e: SecurityException) {
            Log.w(TAG, "Bind denied for Peloton sensor service", e)
            _connectionState.value = ConnectionState.Unavailable
        } catch (e: Exception) {
            // Never let a sensor-binding failure crash the app (PRD P0-9's "explicit message,
            // not a crash" spirit extends to bind-time failures too).
            Log.w(TAG, "Unexpected failure binding Peloton sensor service", e)
            _connectionState.value = ConnectionState.Unavailable
        }
    }

    /** Unbinds the service (e.g. on app shutdown). Safe to call even if [start] never bound. */
    fun stop() {
        try {
            context.unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
            // Never bound (or already unbound) — fine, this is the common case in the
            // emulator/off-bike, and stop() should stay a no-op rather than throw.
        }
        callbackBinder = null
        _connectionState.value = ConnectionState.Unavailable
    }

    /**
     * Registers a callback [Binder] with the service via a raw `transact()` call, mirroring
     * grupetto's `CallbackSensor.registerCallback` technique (AIDL-by-hand, since we don't
     * have the service's real `.aidl` definition to generate a proper stub/proxy from).
     *
     * TODO(#3): VERIFY ON DEVICE — [INTERFACE_DESCRIPTOR], [TRANSACT_REGISTER_CALLBACK], and
     * whether a register call needs any additional data written to the request [Parcel]
     * beyond the interface token + callback binder (grupetto's implementation also writes an
     * identifying string after the binder; unclear whether that's load-bearing or vestigial).
     */
    private fun registerCallback(serviceBinder: IBinder) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(INTERFACE_DESCRIPTOR)
            val callback = buildCallbackBinder()
            data.writeStrongBinder(callback)
            val transacted = serviceBinder.transact(TRANSACT_REGISTER_CALLBACK, data, reply, 0)
            if (!transacted) {
                throw RemoteException("registerCallback transact() returned false")
            }
            reply.readException()
            callbackBinder = callback
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /**
     * The [Binder] we hand to the service so it can call us back with sensor updates. Its
     * [Binder.onTransact] plays the role grupetto's `IV1Callback` interface implementation
     * does — receiving transactions the service initiates, rather than us initiating them.
     *
     * TODO(#3): VERIFY ON DEVICE — [CALLBACK_INTERFACE_DESCRIPTOR] and the exact wire format
     * of [CALLBACK_ON_SENSOR_DATA]'s payload (field order/types in [readRawFrame] below).
     * A layout mismatch here won't necessarily crash — [Parcel] reads can silently return
     * garbage for the wrong type at the wrong offset — so this genuinely cannot be trusted
     * without a real device to log actual transact() payloads against.
     */
    private fun buildCallbackBinder(): Binder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            return when (code) {
                CALLBACK_ON_SENSOR_DATA -> {
                    try {
                        data.enforceInterface(CALLBACK_INTERFACE_DESCRIPTOR)
                        val hasData = data.readInt()
                        if (hasData != 0) {
                            _metrics.value = readRawFrame(data).toBikeMetrics()
                        }
                        true
                    } catch (e: RuntimeException) {
                        // A malformed/unexpected payload here means our field-layout
                        // assumption in readRawFrame is wrong — degrade gracefully rather
                        // than crash the whole app over one bad callback.
                        Log.w(TAG, "Failed to parse sensor data callback", e)
                        true
                    }
                }
                CALLBACK_ON_SENSOR_ERROR -> {
                    Log.w(TAG, "Peloton sensor service reported a sensor error")
                    _connectionState.value = ConnectionState.Disconnected
                    true
                }
                else -> super.onTransact(code, data, reply, flags)
            }
        }
    }

    /**
     * TODO(#3): VERIFY ON DEVICE — this reads only the handful of leading fields we actually
     * care about (rpm, power, resistance) from what grupetto's decompiled `BikeData`
     * Parcelable shows is a much larger struct (calibration state, firmware/serial strings,
     * load-cell diagnostics, error codes, etc.). Reading a `Parcel` requires consuming
     * fields in the *exact* order/type they were written — if this service's actual field
     * order differs even slightly from grupetto's `BikeData.writeToParcel`, these reads will
     * desync and return garbage (most likely silently, not as a crash). This needs to be
     * confirmed by logging the raw parcel bytes against a real callback on-device.
     */
    private fun readRawFrame(data: Parcel): RawBikeFrame {
        val rpm = data.readLong()
        val power = data.readLong()
        val resistance = data.readInt()
        return RawBikeFrame(rpmRaw = rpm, powerRaw = power, resistanceRaw = resistance)
    }

    /**
     * Raw values as received from the service, before mapping to [BikeMetrics]' public
     * units. Scaling factors below are transcribed from grupetto's sensor classes (e.g. its
     * `V1NewPowerSensor` divides raw power by 100) — TODO(#3): confirm these scaling factors
     * and resistance's actual 0-100 range hold for this specific firmware.
     */
    private data class RawBikeFrame(
        val rpmRaw: Long,
        val powerRaw: Long,
        val resistanceRaw: Int,
    ) {
        fun toBikeMetrics(): BikeMetrics = BikeMetrics(
            cadenceRpm = rpmRaw.toInt(),
            resistancePercent = resistanceRaw.coerceIn(0, 100),
            powerWatts = (powerRaw / 100L).toInt(),
            // TODO(#3): no raw speed field observed anywhere in grupetto's reverse-engineered
            // structs — other implementations derive an estimate from power via a curve fit.
            // Left at 0.0 rather than guessing at a formula we can't validate without a bike
            // to compare readings against.
            speedMph = 0.0,
        )
    }

    private companion object {
        const val TAG = "PelotonBikeDataSource"

        // TODO(#3): VERIFY ON DEVICE. Transcribed from grupetto's public source as the
        // "newer" (presumed Gen 2) mechanism — see the class doc above for why this one was
        // chosen over the legacy com.peloton.service.SensorData path.
        const val SERVICE_PACKAGE = "com.onepeloton.affernetservice"
        const val SERVICE_ACTION = "com.onepeloton.affernetservice.IV1Interface"
        const val INTERFACE_DESCRIPTOR = "com.onepeloton.affernetservice.IV1Interface"
        const val CALLBACK_INTERFACE_DESCRIPTOR = "com.onepeloton.affernetservice.IV1Callback"

        // TODO(#3): VERIFY ON DEVICE — transact/callback codes below.
        const val TRANSACT_REGISTER_CALLBACK = 1
        const val CALLBACK_ON_SENSOR_DATA = 1
        const val CALLBACK_ON_SENSOR_ERROR = 2
    }
}
