package dev.digitalducktape.openride.ui.ride

import androidx.lifecycle.ViewModel
import dev.digitalducktape.openride.core.data.Ride
import dev.digitalducktape.openride.core.ride.LiveAggregates
import dev.digitalducktape.openride.core.ride.RideSessionManager
import dev.digitalducktape.openride.core.ride.RideSessionState
import dev.digitalducktape.openride.core.sensor.BikeDataSource
import dev.digitalducktape.openride.core.sensor.BikeMetrics
import dev.digitalducktape.openride.core.sensor.ConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.scan

/**
 * Combined snapshot the in-ride screen renders from. Kept as one immutable value (rather
 * than the screen collecting five separate flows) so a single [combine] is the only place
 * that has to reason about how the pieces line up.
 *
 * @param connectionState anything other than [ConnectionState.Connected] means the screen
 *   must show the "Sensors not detected" banner and blank tiles (PRD P0-9) — never a fake
 *   zero reading.
 */
data class InRideUiState(
    val elapsedSec: Int = 0,
    val sessionState: RideSessionState = RideSessionState.Idle,
    val metrics: BikeMetrics = BikeMetrics.ZERO,
    val aggregates: LiveAggregates = LiveAggregates(),
    val connectionState: ConnectionState = ConnectionState.Connected,
    val distanceMiles: Double = 0.0,
) {
    val sensorsAvailable: Boolean get() = connectionState == ConnectionState.Connected
    val isPaused: Boolean get() = sessionState == RideSessionState.Paused

    /**
     * Live running output in kilojoules. `avgPower * elapsedSec` reconstructs the summed
     * joules exactly (avgPower is already `sum / elapsedSec`), matching
     * [RideSessionManager.stop]'s own `outputKj` math without needing a second running sum
     * threaded through the manager.
     */
    val liveOutputKj: Double get() = aggregates.avgPower * elapsedSec / 1000.0
}

/**
 * In-ride screen (PRD P0-7/P0-9/P0-10): the core screen, showing live cadence/resistance/
 * output tiles, the elapsed timer, and pause/end controls, sourced from the existing
 * [RideSessionManager] (timer/aggregates) and [BikeDataSource] (live sensor feed +
 * connectivity).
 */
class InRideViewModel(
    private val rideSessionManager: RideSessionManager,
    private val bikeDataSource: BikeDataSource,
) : ViewModel() {
    val uiState: Flow<InRideUiState> = combine(
        rideSessionManager.elapsedSec,
        rideSessionManager.state,
        bikeDataSource.metrics,
        rideSessionManager.liveAggregates,
        bikeDataSource.connectionState,
    ) { elapsedSec, sessionState, metrics, aggregates, connectionState ->
        InRideUiState(elapsedSec, sessionState, metrics, aggregates, connectionState)
    }.scan(InRideUiState()) { previous, next ->
        // Distance has no dedicated field on BikeMetrics/RideSample (PRD only requires
        // cadence/resistance/power to be persisted) — it's accumulated here purely for
        // display, one elapsedSec tick at a time, and only while the ride is actually
        // advancing (never during Paused/Idle, and never double-counted across ticks).
        val tickedForward = next.elapsedSec > previous.elapsedSec
        val addedMiles = if (tickedForward && next.sessionState == RideSessionState.Active) {
            val deltaSec = next.elapsedSec - previous.elapsedSec
            next.metrics.speedMph / 3600.0 * deltaSec
        } else {
            0.0
        }
        next.copy(distanceMiles = previous.distanceMiles + addedMiles)
    }

    fun pause() = rideSessionManager.pause()

    fun resume() = rideSessionManager.resume()

    /**
     * Stops the ride and persists it. Deliberately does *not* call
     * [RideSessionManager.reset] — that happens when the summary screen this navigates to is
     * dismissed, so [RideSessionState.Finished] survives long enough for that screen (T8) to
     * rely on it if needed.
     */
    suspend fun endRide(): Ride? = rideSessionManager.stop()
}
