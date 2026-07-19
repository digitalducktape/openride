package dev.digitalducktape.openride.ui.profile

import androidx.lifecycle.ViewModel
import dev.digitalducktape.openride.core.data.Profile
import dev.digitalducktape.openride.core.data.ProfileRepository
import dev.digitalducktape.openride.core.profile.ActiveProfileHolder
import kotlinx.coroutines.flow.Flow

/**
 * Launch destination (PRD P0-3/P0-8): grid of existing riders plus "Add rider." Selecting a
 * profile scopes the rest of the session to it via [ActiveProfileHolder].
 */
class ProfileSelectViewModel(
    private val profileRepository: ProfileRepository,
    private val activeProfileHolder: ActiveProfileHolder,
) : ViewModel() {
    /** Exposed directly (no `stateIn`) — collected lifecycle-aware from the screen. */
    val profiles: Flow<List<Profile>> = profileRepository.observeProfiles()

    fun selectProfile(profileId: Long) {
        activeProfileHolder.setActiveProfile(profileId)
    }
}
