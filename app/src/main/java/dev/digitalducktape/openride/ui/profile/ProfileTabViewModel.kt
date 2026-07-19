package dev.digitalducktape.openride.ui.profile

import androidx.lifecycle.ViewModel
import dev.digitalducktape.openride.core.data.Profile
import dev.digitalducktape.openride.core.data.ProfileRepository
import dev.digitalducktape.openride.core.profile.ActiveProfileHolder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Profile nav tab: shows the active rider and offers "Switch rider" (P0-3: "switch rider"
 * reachable from the Profile tab).
 */
class ProfileTabViewModel(
    private val activeProfileHolder: ActiveProfileHolder,
    profileRepository: ProfileRepository,
) : ViewModel() {
    val activeProfile: Flow<Profile?> =
        combine(activeProfileHolder.activeProfileId, profileRepository.observeProfiles()) { id, profiles ->
            profiles.firstOrNull { it.id == id }
        }

    /** Clears the active profile so the caller can navigate back to profile select. */
    fun switchRider() {
        activeProfileHolder.clear()
    }
}
