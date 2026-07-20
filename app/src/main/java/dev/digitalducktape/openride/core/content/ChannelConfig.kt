package dev.digitalducktape.openride.core.content

/**
 * The curated set of YouTube channels for the Classes browser (PRD P0-6, T9).
 *
 * Each [Channel.id] is a resolved, stable YouTube `channelId` (the `UC...` value), resolved
 * once from the channel's `@handle` by fetching the channel page and reading the
 * `"externalId":"UC..."` value embedded in it (handles themselves aren't usable in the RSS
 * feed URL — only the underlying channel id is: `/feeds/videos.xml?channel_id=<id>`).
 * Verified live against each channel's actual RSS feed at the time this was written
 * (feed `<title>` matched the expected channel name for all twelve).
 */
object ChannelConfig {
    data class Channel(
        val id: String,
        val displayName: String,
        val handle: String,
        val category: ContentCategory,
    )

    val SCENIC = listOf(
        Channel("UCbQiCVLFtWLOjVO0YuUqo7Q", "Virtual Cycling Workouts", "@VirtualCyclingWorkouts", ContentCategory.Scenic),
        Channel("UCVbBtdw-_SCqGDs6-_awaDg", "Indoor Cycling Videos", "@IndoorCyclingVideos", ContentCategory.Scenic),
        Channel("UCWXNvnztTNz99SYgTnyltwg", "Bike the World", "@biketheworld", ContentCategory.Scenic),
    )

    val WORKOUT = listOf(
        Channel("UCbSfwHIgMnaINnkmDaFzqDw", "Ride With Alina", "@ridewithalina", ContentCategory.Workout),
        Channel("UCo0Pbk8bCutBN-Yf_404Kvw", "Gabriella Lynn", "@GabriellaLynnn", ContentCategory.Workout),
        Channel("UChY_9WJx0saa0St48lSdytQ", "Kaleigh Cohen Cycling", "@kaleigh", ContentCategory.Workout),
        Channel("UCyEw-nPmOgo18LPK7wkkdtQ", "Kirsten Allen", "@KirstenAllen", ContentCategory.Workout),
        Channel("UCMEiuHFc7nHTDq2_KzLu0bw", "GCN Training", "@GCNTraining", ContentCategory.Workout),
        Channel("UC0d6o9_OVUZdC4g3ci1_UXA", "The Spin Junkie", "@TheSpinJunkie", ContentCategory.Workout),
        Channel("UCDomKTwMIX0U_cMQzUweggQ", "Kristina Girod", "@KristinaGirod", ContentCategory.Workout),
        Channel("UCH9KY-e1kLOMQ2NcDvXMO3w", "Joe Alvarado", "@JoeAlvarado", ContentCategory.Workout),
        Channel("UCU8VLagwhLbab5RQhjlzY9A", "TaG Cycling", "@TaGCycling1", ContentCategory.Workout),
    )

    /** Seed order for the `content_sources` table: scenic rows first, then workouts. */
    val ALL = SCENIC + WORKOUT
}
