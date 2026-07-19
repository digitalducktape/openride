package dev.digitalducktape.openride.ui.ride

import androidx.lifecycle.ViewModel
import dev.digitalducktape.openride.core.data.Ride
import dev.digitalducktape.openride.core.ride.RideSessionManager
import kotlinx.coroutines.flow.StateFlow

/**
 * Minimal T11 placeholder — enough for "Quick Start -> in-ride -> End Ride -> back to Home"
 * to work end to end. Replaced with the full Peloton-style metrics layout (cadence/
 * resistance/output tiles, sensor-failure banner, pause/resume) in T7.
 */
class InRideViewModel(private val rideSessionManager: RideSessionManager) : ViewModel() {
    val elapsedSec: StateFlow<Int> = rideSessionManager.elapsedSec

    /**
     * Stops the ride and immediately resets the session back to idle. T7 will split this
     * into "stop -> show summary" and defer the reset to the summary screen's dismissal.
     */
    suspend fun endRide(): Ride? {
        val ride = rideSessionManager.stop()
        rideSessionManager.reset()
        return ride
    }
}
