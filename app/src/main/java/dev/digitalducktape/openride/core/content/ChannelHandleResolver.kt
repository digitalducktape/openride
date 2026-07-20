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
class ChannelHandleResolver(
    private val fetcher: FeedFetcher = HttpUrlFeedFetcher(),
    private val pageParser: YouTubePageParser = YouTubePageParser(),
) {

    suspend fun resolve(input: String): Result<ResolvedSource> = withContext(Dispatchers.IO) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Enter a channel or playlist link"))
        }

        playlistId(trimmed)?.let { playlistId ->
            return@withContext runCatching {
                val html = retryingOnce { fetcher.fetchText("https://www.youtube.com/playlist?list=$playlistId") }
                // YouTube answers HTTP 200 for a playlist id that doesn't exist at all, and the
                // response *echoes the requested id back* in several places that are templated
                // straight from the request URL — a canonical link, an og:url meta tag, an
                // embedded "ytUrl"/"originalUrl" pair — so the id appearing in the HTML proves
                // nothing about whether the playlist is real. What a nonexistent (or emptied)
                // playlist page never has is any `lockupViewModel` video tile. Reusing
                // YouTubePageParser to look for at least one actual video is the discriminating
                // signal: a page with real content parses to a non-empty video list, a
                // nonexistent one parses to an empty one (or fails to parse at all, if it lacks
                // ytInitialData entirely). Note this also rejects a playlist that is real but
                // genuinely empty — that's intended, not an oversight: an empty playlist has no
                // rides to add, so there's nothing useful to save either way.
                val displayName = pageTitle(html) ?: "Playlist"
                val hasVideos = try {
                    pageParser.parseVideos(html, displayName).isNotEmpty()
                } catch (e: ContentParseException) {
                    false
                }
                if (!hasVideos) {
                    throw ContentParseException("Couldn't find that playlist")
                }
                ResolvedSource(
                    sourceType = ContentSourceType.PLAYLIST,
                    youtubeId = playlistId,
                    displayName = displayName,
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
