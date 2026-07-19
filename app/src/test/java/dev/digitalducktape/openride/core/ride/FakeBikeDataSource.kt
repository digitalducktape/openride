package dev.digitalducktape.openride.core.ride

import dev.digitalducktape.openride.core.sensor.BikeDataSource
import dev.digitalducktape.openride.core.sensor.BikeMetrics
import dev.digitalducktape.openride.core.sensor.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fully-controllable [BikeDataSource] test double: metrics only change when [setMetrics] is
 * called, so [RideSessionManager] tests can assert exact aggregate math instead of dealing
 * with [dev.digitalducktape.openride.core.sensor.MockBikeDataSource]'s randomized ride
 * simulation.
 */
class FakeBikeDataSource : BikeDataSource {
    private val _metrics = MutableStateFlow(BikeMetrics.ZERO)
    override val metrics: StateFlow<BikeMetrics> = _metrics.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    fun setMetrics(cadenceRpm: Int, resistancePercent: Int, powerWatts: Int, speedMph: Double = 0.0) {
        _metrics.value = BikeMetrics(cadenceRpm, resistancePercent, powerWatts, speedMph)
    }

    fun setConnectionState(state: ConnectionState) {
        _connectionState.value = state
    }
}
