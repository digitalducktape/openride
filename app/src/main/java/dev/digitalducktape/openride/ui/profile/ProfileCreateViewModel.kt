package dev.digitalducktape.openride.ui.profile

import androidx.lifecycle.ViewModel
import dev.digitalducktape.openride.core.data.Profile
import dev.digitalducktape.openride.core.data.ProfileRepository
import dev.digitalducktape.openride.core.profile.ActiveProfileHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Create-profile form state. Weight/FTP fields and the avatar picker land in T6 — this T11
 * version covers just the name-required flow so "Add rider" is wired end-to-end.
 */
data class ProfileCreateUiState(
    val name: String = "",
    val nameError: String? = null,
    val avatarEmoji: String = AvatarOptions.defaultEmoji,
    val avatarColor: Int = AvatarOptions.defaultColor,
    val saved: Boolean = false,
)

class ProfileCreateViewModel(
    private val profileRepository: ProfileRepository,
    private val activeProfileHolder: ActiveProfileHolder,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileCreateUiState())
    val uiState: StateFlow<ProfileCreateUiState> = _uiState.asStateFlow()

    fun onNameChange(name: String) {
        _uiState.value = _uiState.value.copy(name = name, nameError = null)
    }

    /**
     * Validates the name (required, non-blank) and, if valid, persists the new profile and
     * sets it active, returning `true`. Returns `false` (surfacing
     * [ProfileCreateUiState.nameError]) on invalid input without touching the repository.
     *
     * A plain `suspend fun` rather than an internally-launched coroutine, so it can be
     * driven directly from `runTest` in tests and from `rememberCoroutineScope()` in the
     * screen — the same pattern [dev.digitalducktape.openride.core.ride.RideSessionManager.stop]
     * uses, keeping this view model's async work testable without depending on
     * `viewModelScope`'s Main-dispatcher wiring.
     */
    suspend fun save(): Boolean {
        val current = _uiState.value
        val trimmedName = current.name.trim()
        if (trimmedName.isBlank()) {
            _uiState.value = current.copy(nameError = "Name is required")
            return false
        }

        val id = profileRepository.createProfile(
            Profile(
                name = trimmedName,
                avatarEmoji = current.avatarEmoji,
                avatarColor = current.avatarColor,
                weightKg = null,
                ftp = null,
            ),
        )
        activeProfileHolder.setActiveProfile(id)
        _uiState.value = _uiState.value.copy(saved = true)
        return true
    }
}
