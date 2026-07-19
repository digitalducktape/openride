package dev.digitalducktape.openride.core.ride

/**
 * A rider's optional pre-ride target (PRD P1-3: "time or cadence/power target"). Set before
 * [RideSessionManager.start] via [RideSessionManager.setGoal]; progress toward it is derived
 * by the UI layer from live ride state, not stored on the goal itself.
 */
sealed interface RideGoal {
    /** No goal set — the default. */
    data object None : RideGoal

    /** Elapsed-time target, in seconds. */
    data class Duration(val targetSec: Int) : RideGoal

    /** Total mechanical output target, in kilojoules (matches [dev.digitalducktape.openride.core.data.Ride.outputKj]). */
    data class Output(val targetKj: Double) : RideGoal
}
