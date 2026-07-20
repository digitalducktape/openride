package dev.digitalducktape.openride.ui.classes

import androidx.lifecycle.ViewModel
import dev.digitalducktape.openride.core.content.YouTubeContentRepository
import dev.digitalducktape.openride.core.data.RideRepository
import dev.digitalducktape.openride.core.profile.ActiveProfileHolder
import dev.digitalducktape.openride.core.ride.RideSessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Classes tab (PRD P0-6, T10): loads each configured channel's videos via
 * [YouTubeContentRepository] and exposes them as one row per channel.
 *
 * [refresh] is a plain `suspend fun` driven from the screen's `LaunchedEffect`/
 * `rememberCoroutineScope`, rather than something this class launches on `viewModelScope`
 * itself — the same pattern
 * [dev.digitalducktape.openride.ui.profile.ProfileCreateViewModel.save] uses, so the load
 * can be driven directly from `runTest` in tests without depending on `viewModelScope`'s
 * Main-dispatcher wiring.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClassesViewModel(
    private val contentRepository: YouTubeContentRepository,
    private val rideSessionManager: RideSessionManager,
    private val activeProfileHolder: ActiveProfileHolder,
    rideRepository: RideRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ClassesUiState>(ClassesUiState.Loading)
    val uiState: StateFlow<ClassesUiState> = _uiState.asStateFlow()

    /**
     * When the active profile last rode each class, keyed by video id (v2 "taken" badges).
     * Live Room flow, so finishing a video ride badges its card as soon as the rider is
     * back on this tab.
     */
    val takenVideos: Flow<Map<String, Long>> =
        activeProfileHolder.activeProfileId.flatMapLatest { profileId ->
            if (profileId == null) {
                flowOf(emptyMap())
            } else {
                rideRepository.observeTakenVideos(profileId)
                    .map { taken -> taken.associate { it.videoId to it.lastTakenEpochMs } }
            }
        }

    /** Fetches (or re-fetches) all configured channels. Safe to call repeatedly. */
    suspend fun refresh() {
        _uiState.value = ClassesUiState.Loading
        val sections = contentRepository.channelSections()
        _uiState.value = ClassesUiState.Loaded(
            sections = sections,
            anyRefreshFailed = sections.any { it.refreshFailed },
        )
    }

    /**
     * Starts a ride session for the active profile ahead of navigating to the in-app video
     * player (v2 spec) — same semantics as
     * [dev.digitalducktape.openride.ui.home.HomeViewModel.startQuickRide]: returns `false`
     * (and starts nothing) when no profile is active, and the caller treats that as
     * "not navigable." [videoId] is recorded on the persisted ride for the "taken" badges.
     */
    fun startRideForVideo(videoId: String): Boolean {
        val profileId = activeProfileHolder.activeProfileId.value ?: return false
        rideSessionManager.start(profileId, videoId)
        return true
    }
}
