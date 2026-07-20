package dev.digitalducktape.openride.ui.update

import androidx.lifecycle.ViewModel
import dev.digitalducktape.openride.core.update.UpdateCheckResult
import dev.digitalducktape.openride.core.update.UpdateManifest
import dev.digitalducktape.openride.core.update.UpdateRepository
import dev.digitalducktape.openride.core.update.UpdateSettings
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Drives the opt-in self-updater screen (PRD #22/T22): configure a manifest URL, check it
 * against the installed build, download the APK, and hand it to the system installer.
 *
 * The check → download → install steps are separate `suspend` functions, each triggered by its
 * own explicit user tap (the screen calls them from a `rememberCoroutineScope`, matching
 * [dev.digitalducktape.openride.ui.profile.ProfileCreateViewModel]'s suspend-rather-than-
 * viewModelScope convention). Nothing chains automatically — that's what keeps the ticket's
 * "no auto-install without user tap" guarantee.
 */
class UpdateViewModel(
    private val updateSettings: UpdateSettings,
    private val updateRepository: UpdateRepository,
    private val currentVersionCode: Int,
    private val currentVersionName: String,
) : ViewModel() {

    data class UiState(
        val manifestUrl: String? = null,
        val urlDraft: String = "",
        val isBusy: Boolean = false,
        val status: String? = null,
        val available: UpdateManifest? = null,
        val downloadedApk: File? = null,
        val currentVersionName: String = "",
        val currentVersionCode: Int = 0,
    )

    private val _uiState = MutableStateFlow(
        UiState(
            manifestUrl = updateSettings.manifestUrl.value,
            urlDraft = updateSettings.manifestUrl.value.orEmpty(),
            currentVersionName = currentVersionName,
            currentVersionCode = currentVersionCode,
        ),
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun onUrlDraftChange(value: String) {
        _uiState.value = _uiState.value.copy(urlDraft = value)
    }

    /** Persists the typed URL, enabling the updater. Clears any prior check result. */
    fun saveUrl() {
        val draft = _uiState.value.urlDraft
        updateSettings.setManifestUrl(draft)
        _uiState.value = _uiState.value.copy(
            manifestUrl = updateSettings.manifestUrl.value,
            status = null,
            available = null,
            downloadedApk = null,
        )
    }

    /** Clears the configured URL, disabling the updater entirely. */
    fun clearUrl() {
        updateSettings.setManifestUrl(null)
        _uiState.value = _uiState.value.copy(
            manifestUrl = null,
            urlDraft = "",
            status = null,
            available = null,
            downloadedApk = null,
        )
    }

    /** Fetches and evaluates the manifest. No-op if no URL is configured. */
    suspend fun checkForUpdate() {
        val url = _uiState.value.manifestUrl ?: return
        _uiState.value = _uiState.value.copy(isBusy = true, status = "Checking…", available = null, downloadedApk = null)

        when (val result = updateRepository.check(url, currentVersionCode)) {
            is UpdateCheckResult.Available -> _uiState.value = _uiState.value.copy(
                isBusy = false,
                available = result.manifest,
                status = "Version ${result.manifest.versionName} is available",
            )
            is UpdateCheckResult.UpToDate -> _uiState.value = _uiState.value.copy(
                isBusy = false,
                available = null,
                status = "You're up to date",
            )
            is UpdateCheckResult.Failed -> _uiState.value = _uiState.value.copy(
                isBusy = false,
                available = null,
                status = result.reason,
            )
        }
    }

    /** Downloads the available APK. Does *not* install it — see [installIntent]. */
    suspend fun downloadUpdate() {
        val manifest = _uiState.value.available ?: return
        _uiState.value = _uiState.value.copy(isBusy = true, status = "Downloading…")

        val file = updateRepository.downloadApk(manifest)
        _uiState.value = if (file == null) {
            _uiState.value.copy(isBusy = false, status = "Download failed", downloadedApk = null)
        } else {
            _uiState.value.copy(
                isBusy = false,
                status = "Downloaded — tap Install to continue",
                downloadedApk = file,
            )
        }
    }

    /** The intent to hand the downloaded APK to the system installer, or `null` if none is ready. */
    fun installIntent() = _uiState.value.downloadedApk?.let { updateRepository.installIntentFor(it) }
}
