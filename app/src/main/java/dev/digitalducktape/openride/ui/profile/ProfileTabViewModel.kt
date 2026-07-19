package dev.digitalducktape.openride.ui.profile

import androidx.lifecycle.ViewModel
import dev.digitalducktape.openride.core.data.Profile
import dev.digitalducktape.openride.core.data.ProfileRepository
import dev.digitalducktape.openride.core.profile.ActiveProfileHolder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Profile nav tab: shows the active rider. "Switch rider" (T6) hangs off this same view
 * model — [ActiveProfileHolder.clear] is exposed here so T6 only needs to wire up the
 * button + navigation, not new state plumbing.
 */
class ProfileTabViewModel(
    private val activeProfileHolder: ActiveProfileHolder,
    profileRepository: ProfileRepository,
) : ViewModel() {
    val activeProfile: Flow<Profile?> =
        combine(activeProfileHolder.activeProfileId, profileRepository.observeProfiles()) { id, profiles ->
            profiles.firstOrNull { it.id == id }
        }
}
