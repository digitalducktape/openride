package dev.digitalducktape.openride.core.ride

import dev.digitalducktape.openride.core.data.Ride

/**
 * State machine for a single ride session: `Idle -> Active <-> Paused -> Finished`.
 *
 * `Finished` carries the persisted [Ride] (with its generated id) so the ride-summary UI
 * (P0-5) has everything it needs without a separate lookup. Calling
 * [RideSessionManager.reset] from `Finished` returns to `Idle`, ready for another ride.
 */
sealed interface RideSessionState {
    data object Idle : RideSessionState
    data object Active : RideSessionState
    data object Paused : RideSessionState
    data class Finished(val ride: Ride) : RideSessionState
}
