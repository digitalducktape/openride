package dev.digitalducktape.openride.ui.creator

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
 * A creator's own page: their recent classes plus each playlist they've curated.
 *
 * Playlists are how creators organize themed sets of classes — a single row on the Classes
 * tab can only ever show recent uploads, so this is the way to reach the rest of what a
 * creator has published without an API key or a full back-catalog crawl.
 *
 * Like [dev.digitalducktape.openride.ui.classes.ClassesViewModel], the loads here are plain
 * `suspend fun`s driven from the screen rather than launched on `viewModelScope`, so tests
 * can drive them directly from `runTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CreatorViewModel(
    private val contentRepository: YouTubeContentRepository,
    private val rideSessionManager: RideSessionManager,
    private val activeProfileHolder: ActiveProfileHolder,
    rideRepository: RideRepository,
    private val sourceId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CreatorUiState>(CreatorUiState.Loading)
    val uiState: StateFlow<CreatorUiState> = _uiState.asStateFlow()

    /** Same "already ridden" badges the Classes tab shows, so they don't vanish on this screen. */
    val takenVideos: Flow<Map<String, Long>> =
        activeProfileHolder.activeProfileId.flatMapLatest { profileId ->
            if (profileId == null) {
                flowOf(emptyMap())
            } else {
                rideRepository.observeTakenVideos(profileId)
                    .map { taken -> taken.associate { it.videoId to it.lastTakenEpochMs } }
            }
        }

    suspend fun load() {
        _uiState.value = CreatorUiState.Loading
        val content = contentRepository.creatorContent(sourceId)
        _uiState.value = if (content == null) {
            CreatorUiState.NotFound
        } else {
            CreatorUiState.Loaded(
                displayName = content.displayName,
                latest = content.latest,
                playlistRows = content.playlists.map { PlaylistRow(playlist = it) },
                refreshFailed = content.refreshFailed,
            )
        }
    }

    /** Fetches one playlist's videos. Safe to call for a playlist that isn't on this page. */
    suspend fun loadPlaylist(playlistId: String) {
        val loaded = _uiState.value as? CreatorUiState.Loaded ?: return
        val row = loaded.playlistRows.firstOrNull { it.playlist.id == playlistId } ?: return

        updateRow(playlistId) { it.copy(isLoading = true, loadFailed = false) }
        val videos = contentRepository.playlistVideos(playlistId, row.playlist.title)
        updateRow(playlistId) {
            it.copy(videos = videos, isLoading = false, loadFailed = videos.isEmpty())
        }
    }

    private fun updateRow(playlistId: String, transform: (PlaylistRow) -> PlaylistRow) {
        val loaded = _uiState.value as? CreatorUiState.Loaded ?: return
        _uiState.value = loaded.copy(
            playlistRows = loaded.playlistRows.map { row ->
                if (row.playlist.id == playlistId) transform(row) else row
            },
        )
    }

    /** Same contract as [dev.digitalducktape.openride.ui.classes.ClassesViewModel.startRideForVideo]. */
    fun startRideForVideo(videoId: String): Boolean {
        val profileId = activeProfileHolder.activeProfileId.value ?: return false
        rideSessionManager.start(profileId, videoId)
        return true
    }
}
