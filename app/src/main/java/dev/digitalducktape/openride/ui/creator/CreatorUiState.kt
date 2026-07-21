package dev.digitalducktape.openride.ui.creator

import dev.digitalducktape.openride.core.content.PlaylistSummary
import dev.digitalducktape.openride.core.content.Video

/**
 * One playlist shelf on a creator's page. Playlists load lazily — a creator can have twenty
 * of them and each one is its own page fetch, so opening the creator page fetches none of
 * them up front and each row asks for its own content when it first appears.
 */
data class PlaylistRow(
    val playlist: PlaylistSummary,
    val videos: List<Video> = emptyList(),
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
)

sealed interface CreatorUiState {
    data object Loading : CreatorUiState

    /** @param refreshFailed true when [latest] is stale cached content, same as the Classes tab. */
    data class Loaded(
        val displayName: String,
        val latest: List<Video>,
        val playlistRows: List<PlaylistRow>,
        val refreshFailed: Boolean,
    ) : CreatorUiState

    /** The source was removed (e.g. deleted on the Content Sources screen) while navigating. */
    data object NotFound : CreatorUiState
}
