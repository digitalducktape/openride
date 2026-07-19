package dev.digitalducktape.openride.ui.home

import androidx.lifecycle.ViewModel
import dev.digitalducktape.openride.core.data.Profile
import dev.digitalducktape.openride.core.data.ProfileRepository
import dev.digitalducktape.openride.core.profile.ActiveProfileHolder
import dev.digitalducktape.openride.core.ride.RideGoal
import dev.digitalducktape.openride.core.ride.RideSessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

/**
 * Home screen (PRD P0-7): greeting for the active profile plus "Quick Start" and the
 * pre-ride goal picker (PRD P1-3).
 */
class HomeViewModel(
    private val activeProfileHolder: ActiveProfileHolder,
    profileRepository: ProfileRepository,
    private val rideSessionManager: RideSessionManager,
) : ViewModel() {
    /** `null` while no profile is active, or if the active id doesn't (yet) match a loaded row. */
    val activeProfile: Flow<Profile?> =
        combine(activeProfileHolder.activeProfileId, profileRepository.observeProfiles()) { id, profiles ->
            profiles.firstOrNull { it.id == id }
        }

    /** The pre-ride goal (PRD P1-3), settable here before starting a ride. */
    val goal: StateFlow<RideGoal> = rideSessionManager.goal

    /**
     * Sets the rider's pre-ride goal. Delegates to [RideSessionManager.setGoal], which only
     * takes effect while the session is Idle (i.e. before a ride starts).
     */
    fun setGoal(goal: RideGoal) = rideSessionManager.setGoal(goal)

    /**
     * Starts a ride for the active profile. Returns `false` (and starts nothing) if no
     * profile is active — the caller should treat that as "not navigable."
     * [RideSessionManager.start] is itself synchronous and a no-op unless the session is
     * currently idle, so this can be called directly from a click handler.
     */
    fun startQuickRide(): Boolean {
        val profileId = activeProfileHolder.activeProfileId.value ?: return false
        rideSessionManager.start(profileId)
        return true
    }
}
