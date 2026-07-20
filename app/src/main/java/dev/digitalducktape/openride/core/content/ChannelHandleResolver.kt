package dev.digitalducktape.openride.core.content

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns whatever the rider pasted into the Content Sources screen — an `@handle`, a channel
 * URL, a `/c/` vanity URL, or a playlist URL — into a stable id the feeds accept.
 *
 * Channel handles can't be used in the RSS feed URL, only the underlying `UC…` channel id
 * can, and the only key-free way to learn that id is to fetch the channel page and read the
 * `"externalId"` value embedded in it. That's the same technique used to resolve the built-in
 * catalog in [ChannelConfig].
 */
class ChannelHandleResolver(private val fetcher: FeedFetcher = HttpUrlFeedFetcher()) {

    suspend fun resolve(input: String): Result<ResolvedSource> = withContext(Dispatchers.IO) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Enter a channel or playlist link"))
        }

        playlistId(trimmed)?.let { playlistId ->
            return@withContext runCatching {
                val html = retryingOnce { fetcher.fetchText("https://www.youtube.com/playlist?list=$playlistId") }
                // A playlist that doesn't exist (or a bare handle that merely looked like a
                // playlist id) still comes back as a normal 200 YouTube page, just one with no
                // mention of this playlist anywhere in it — no canonical link, no embedded JSON
                // referencing it. Requiring the id to appear somewhere in the response is the
                // cheapest signal available (no extra request, no new dependency) that we
                // actually landed on that playlist rather than an error/redirect page.
                if (playlistId !in html) {
                    throw ContentParseException("Couldn't find that playlist")
                }
                ResolvedSource(
                    sourceType = ContentSourceType.PLAYLIST,
                    youtubeId = playlistId,
                    displayName = pageTitle(html) ?: "Playlist",
                )
            }
        }

        val url = channelUrl(trimmed)
        runCatching {
            val html = retryingOnce { fetcher.fetchText(url) }
            val channelId = EXTERNAL_ID.find(html)?.groupValues?.get(1)
                ?: throw ContentParseException("Couldn't find that channel")
            ResolvedSource(
                sourceType = ContentSourceType.CHANNEL,
                youtubeId = channelId,
                displayName = pageTitle(html) ?: channelId,
            )
        }
    }

    /**
     * The `list=` id of a playlist URL, or `null` if [input] isn't one.
     *
     * A bare (non-URL) input only counts as a playlist id if it's actually shaped like one:
     * real YouTube playlist ids are "PL" followed by a long run of id characters (32 in the
     * common case), never as short as a typical handle. Without the length floor, a rider
     * pasting a bare handle like `PLcyclist` would get silently misrouted into the playlist
     * branch instead of resolving as their channel. A `list=` query parameter is unambiguous
     * regardless of length, so it's exempt from the floor.
     */
    private fun playlistId(input: String): String? =
        PLAYLIST_ID.find(input)?.groupValues?.get(1)
            ?: input.takeIf {
                it.startsWith("PL") &&
                    !it.contains('/') &&
                    !it.contains(' ') &&
                    it.length >= MIN_BARE_PLAYLIST_ID_LENGTH
            }

    /**
     * The page to fetch for [input]. A full youtube.com URL is fetched as given (minus any
     * tab suffix); anything else is treated as a handle.
     */
    private fun channelUrl(input: String): String = when {
        input.contains("youtube.com/") -> {
            val withoutScheme = input.substringAfter("youtube.com/").trim('/')
            val path = withoutScheme.substringBefore('?')
                .split('/')
                .let { segments ->
                    if (segments.size > 1 && segments[0] in setOf("c", "channel", "user")) {
                        segments.take(2)
                    } else {
                        segments.take(1)
                    }
                }
                .joinToString("/")
            "https://www.youtube.com/$path"
        }
        input.startsWith("@") -> "https://www.youtube.com/$input"
        else -> "https://www.youtube.com/@$input"
    }

    /** `<title>Kaleigh Cohen Cycling - YouTube</title>` → `Kaleigh Cohen Cycling`. */
    private fun pageTitle(html: String): String? =
        TITLE.find(html)?.groupValues?.get(1)
            ?.removeSuffix(" - YouTube")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private companion object {
        val EXTERNAL_ID = Regex(""""externalId":"(UC[\w-]+)"""")
        val PLAYLIST_ID = Regex("""[?&]list=(PL[\w-]+)""")
        val TITLE = Regex("""<title>([^<]*)</title>""")

        /** "PL" + at least 14 more chars = 16 total; real playlist ids run to 32+. */
        const val MIN_BARE_PLAYLIST_ID_LENGTH = 16
    }
}
