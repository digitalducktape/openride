package dev.digitalducktape.openride.core.heartrate

import dev.digitalducktape.openride.core.sensor.ConnectionState
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over a single paired BLE heart-rate strap's live feed (PRD P1-4, T17). Mirrors
 * [dev.digitalducktape.openride.core.sensor.BikeDataSource]'s shape deliberately — same
 * "narrow interface + real/fake implementations" pattern, reusing the existing
 * [ConnectionState] sealed type rather than inventing a parallel one.
 */
interface HeartRateDataSource {
    /** Latest heart rate in bpm, or `null` before any reading has arrived. */
    val bpm: StateFlow<Int?>

    /** Whether the strap's connection is currently live. */
    val connectionState: StateFlow<ConnectionState>
}

/**
 * A [HeartRateDataSource] that also owns a connect/disconnect lifecycle — separated from the
 * plain read-only interface so [HeartRateManager] can test its reconnect-on-profile-switch
 * logic against a lightweight fake without that fake needing to be a real
 * [BleHeartRateDataSource].
 */
interface ManagedHeartRateDataSource : HeartRateDataSource {
    /** Begins connecting. Implementations must never throw (see [BleHeartRateDataSource]'s doc). */
    fun start()

    /** Disconnects and releases any held resources. Safe to call even if [start] was never called. */
    fun stop()
}
