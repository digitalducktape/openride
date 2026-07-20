package dev.digitalducktape.openride.core.content

/**
 * Which curated grouping a source belongs to. These are the two browsable class categories
 * on the Classes tab — "Just Ride" is the Home tab's quick start and "Random" is a button,
 * so neither is a category here.
 */
enum class ContentCategory {
    Scenic,
    Workout,
}

/**
 * One channel's row of videos for the Classes browse UI (T10) — one [ChannelSection] per
 * configured channel, rendered as one horizontal row.
 *
 * @param refreshFailed true when the live feed fetch failed and [videos] is a last-known
 *   cached list instead (or empty, if there was never a successful fetch) — PRD P0-6's
 *   "couldn't refresh" requirement. UI should show a non-blocking banner, not hide the row.
 */
data class ChannelSection(
    val channelId: String,
    val channelName: String,
    val category: ContentCategory,
    val videos: List<Video>,
    val refreshFailed: Boolean,
)
