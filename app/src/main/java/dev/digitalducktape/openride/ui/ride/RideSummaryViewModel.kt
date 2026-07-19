package dev.digitalducktape.openride.ui.ride

import androidx.lifecycle.ViewModel
import dev.digitalducktape.openride.core.data.Ride
import dev.digitalducktape.openride.core.data.RideRepository
import dev.digitalducktape.openride.core.ride.RideSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Post-ride summary (T7 minimal version — duration/aggregates only; the sample graph and
 * reuse as the history detail view land in T8). Loads by [rideId] from Room rather than
 * being handed the just-stopped [Ride] directly, so the exact same screen/view-model
 * doubles as history's detail view once T8 wires that up.
 */
class RideSummaryViewModel(
    private val rideRepository: RideRepository,
    private val rideSessionManager: RideSessionManager,
    private val rideId: Long,
) : ViewModel() {
    private val _ride = MutableStateFlow<Ride?>(null)
    val ride: StateFlow<Ride?> = _ride.asStateFlow()

    suspend fun load() {
        _ride.value = rideRepository.getRide(rideId)
    }

    /**
     * Returns the session to idle if it was sitting in [dev.digitalducktape.openride.core.ride.RideSessionState.Finished]
     * (the just-stopped-this-ride path) — a no-op otherwise (e.g. viewing an older ride from
     * history in T8), since [RideSessionManager.reset] only acts on `Finished`.
     */
    fun dismiss() {
        rideSessionManager.reset()
    }
}
