package dev.digitalducktape.openride.ui.profile

import androidx.lifecycle.ViewModel
import dev.digitalducktape.openride.core.data.Profile
import dev.digitalducktape.openride.core.data.ProfileRepository
import dev.digitalducktape.openride.core.profile.ActiveProfileHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Create-profile form state (PRD P0-3: name + avatar required, weight/FTP optional — used
 * for calorie estimates, power zones P1-3, and export accuracy P1-1).
 */
data class ProfileCreateUiState(
    val name: String = "",
    val nameError: String? = null,
    val avatarEmoji: String = AvatarOptions.defaultEmoji,
    val avatarColor: Int = AvatarOptions.defaultColor,
    val weightKgInput: String = "",
    val weightError: String? = null,
    val ftpInput: String = "",
    val ftpError: String? = null,
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

    fun onAvatarEmojiChange(emoji: String) {
        _uiState.value = _uiState.value.copy(avatarEmoji = emoji)
    }

    fun onAvatarColorChange(color: Int) {
        _uiState.value = _uiState.value.copy(avatarColor = color)
    }

    fun onWeightChange(weightKg: String) {
        _uiState.value = _uiState.value.copy(weightKgInput = weightKg, weightError = null)
    }

    fun onFtpChange(ftp: String) {
        _uiState.value = _uiState.value.copy(ftpInput = ftp, ftpError = null)
    }

    /**
     * Validates the form and, if valid, persists the new profile and sets it active,
     * returning `true`. Name is required (non-blank); weight (kg, positive decimal) and FTP
     * (watts, positive whole number) are optional but must be well-formed if provided.
     * Returns `false` on the first invalid field, surfacing the relevant `*Error` in
     * [ProfileCreateUiState] without touching the repository.
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

        var next = current
        var valid = true

        if (trimmedName.isBlank()) {
            next = next.copy(nameError = "Name is required")
            valid = false
        }

        val weightKg: Double? = when {
            current.weightKgInput.isBlank() -> null
            else -> current.weightKgInput.toDoubleOrNull()?.takeIf { it > 0.0 } ?: run {
                next = next.copy(weightError = "Enter a weight in kg greater than 0")
                valid = false
                null
            }
        }

        val ftp: Int? = when {
            current.ftpInput.isBlank() -> null
            else -> current.ftpInput.toIntOrNull()?.takeIf { it > 0 } ?: run {
                next = next.copy(ftpError = "Enter an FTP in watts greater than 0")
                valid = false
                null
            }
        }

        if (!valid) {
            _uiState.value = next
            return false
        }

        val id = profileRepository.createProfile(
            Profile(
                name = trimmedName,
                avatarEmoji = current.avatarEmoji,
                avatarColor = current.avatarColor,
                weightKg = weightKg,
                ftp = ftp,
            ),
        )
        activeProfileHolder.setActiveProfile(id)
        _uiState.value = _uiState.value.copy(saved = true)
        return true
    }
}
