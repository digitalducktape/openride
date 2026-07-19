package dev.digitalducktape.openride.core.content

/**
 * A single video pulled from a configured channel's YouTube RSS/Atom feed (PRD P0-6).
 *
 * @param id YouTube video id (the `v=` query param / `yt:videoId` in the feed) — also what
 *   [dev.digitalducktape.openride.ui.classes.ClassesScreen] hands to the playback intent.
 * @param title video title as published.
 * @param thumbnailUrl thumbnail image URL from the feed's `media:thumbnail`.
 * @param channelName display name of the channel this video came from.
 * @param durationSec runtime in seconds, when known. The RSS feed never includes duration,
 *   and YouTube's oEmbed endpoint (the only lazy per-video lookup that doesn't need an API
 *   key/quota) does not reliably return it either — so this is `null` far more often than
 *   not. Callers must treat `null` as "don't show a duration badge," never as zero.
 * @param publishedEpochMs publish time, for sorting newest-first.
 */
data class Video(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val channelName: String,
    val durationSec: Int?,
    val publishedEpochMs: Long,
)
