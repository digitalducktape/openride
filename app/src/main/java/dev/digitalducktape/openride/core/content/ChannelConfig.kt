package dev.digitalducktape.openride.core.content

/**
 * The curated set of YouTube channels for the Classes browser (PRD P0-6, T9).
 *
 * Each [Channel.id] is a resolved, stable YouTube `channelId` (the `UC...` value), resolved
 * once from the channel's `@handle` by fetching the channel page and reading the
 * `"externalId":"UC..."` value embedded in it (handles themselves aren't usable in the RSS
 * feed URL — only the underlying channel id is: `/feeds/videos.xml?channel_id=<id>`).
 * Verified live against each channel's actual RSS feed at the time this was written
 * (feed `<title>` matched the expected channel name for all four).
 */
object ChannelConfig {
    data class Channel(
        val id: String,
        val displayName: String,
        val handle: String,
        val category: ContentCategory,
    )

    val SCENIC = listOf(
        Channel(
            id = "UCbQiCVLFtWLOjVO0YuUqo7Q",
            displayName = "Virtual Cycling Workouts",
            handle = "@VirtualCyclingWorkouts",
            category = ContentCategory.Scenic,
        ),
        Channel(
            id = "UCVbBtdw-_SCqGDs6-_awaDg",
            displayName = "Indoor Cycling Videos",
            handle = "@IndoorCyclingVideos",
            category = ContentCategory.Scenic,
        ),
    )

    val RIDES = listOf(
        Channel(
            id = "UCbSfwHIgMnaINnkmDaFzqDw",
            displayName = "Ride With Alina",
            handle = "@ridewithalina",
            category = ContentCategory.Rides,
        ),
        Channel(
            id = "UCo0Pbk8bCutBN-Yf_404Kvw",
            displayName = "Gabriella Lynn",
            handle = "@GabriellaLynnn",
            category = ContentCategory.Rides,
        ),
    )

    val ALL = SCENIC + RIDES
}
