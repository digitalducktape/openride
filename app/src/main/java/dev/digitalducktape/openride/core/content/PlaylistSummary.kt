package dev.digitalducktape.openride.core.content

/**
 * One of a creator's curated playlists, as shown on their creator page.
 *
 * @param id the `PL…` playlist id — also what fetches the playlist's own videos.
 * @param videoCount how many videos the playlist tile claims, when the badge is present.
 *   Display-only; the fetched list is the source of truth for what the rider can actually ride.
 */
data class PlaylistSummary(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val videoCount: Int?,
)
