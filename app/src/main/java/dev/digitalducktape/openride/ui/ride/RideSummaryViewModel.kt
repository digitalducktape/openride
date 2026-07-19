package dev.digitalducktape.openride.ui.ride

import androidx.lifecycle.ViewModel
import dev.digitalducktape.openride.core.data.Ride
import dev.digitalducktape.openride.core.data.RideRepository
import dev.digitalducktape.openride.core.data.RideSample
import dev.digitalducktape.openride.core.ride.RideSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Post-ride summary (PRD P0-5), reused unmodified as history's detail view (T8): loads a
 * ride's aggregates *and* its full per-second sample series by [rideId] from Room, rather
 * than being handed the just-stopped [Ride] directly, so a fresh-off-the-bike ride and an
 * older ride tapped from history go through the exact same code path.
 */
class RideSummaryViewModel(
    private val rideRepository: RideRepository,
    private val rideSessionManager: RideSessionManager,
    private val rideId: Long,
) : ViewModel() {
    private val _ride = MutableStateFlow<Ride?>(null)
    val ride: StateFlow<Ride?> = _ride.asStateFlow()

    private val _samples = MutableStateFlow<List<RideSample>>(emptyList())
    val samples: StateFlow<List<RideSample>> = _samples.asStateFlow()

    suspend fun load() {
        _ride.value = rideRepository.getRide(rideId)
        _samples.value = rideRepository.getSamples(rideId)
    }

    /**
     * Returns the session to idle if it was sitting in [dev.digitalducktape.openride.core.ride.RideSessionState.Finished]
     * (the just-stopped-this-ride path) — a no-op otherwise (e.g. viewing an older ride from
     * history), since [RideSessionManager.reset] only acts on `Finished`.
     */
    fun dismiss() {
        rideSessionManager.reset()
    }
}
