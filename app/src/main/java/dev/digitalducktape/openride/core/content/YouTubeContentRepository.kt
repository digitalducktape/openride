package dev.digitalducktape.openride.core.content

import android.content.Context
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Content layer for the Classes browser (PRD P0-6, T9): fetches each configured channel's
 * YouTube Atom feed, caches the result, and falls back to the last-known cache (flagged via
 * [ChannelSection.refreshFailed]) when a feed can't be reached — so a transient network blip
 * degrades to stale content, never an empty row.
 *
 * Video duration deliberately isn't fetched here: YouTube's RSS feed never includes it, and
 * the only key-free lazy lookup (oEmbed) doesn't reliably return it either, so
 * [Video.durationSec] is left `null` throughout this layer. The Classes UI (T10) simply
 * omits the duration badge when it's `null` rather than blocking content on a lookup that
 * often can't succeed anyway.
 */
class YouTubeContentRepository(
    context: Context,
    private val channels: List<ChannelConfig.Channel> = ChannelConfig.ALL,
    private val fetcher: FeedFetcher = HttpUrlFeedFetcher(),
    private val parser: AtomFeedParser = AtomFeedParser(),
    private val cache: ContentCache = ContentCache(context),
) {
    /** One [ChannelSection] per configured channel, in [channels] order. */
    suspend fun channelSections(): List<ChannelSection> = withContext(Dispatchers.IO) {
        channels.map { channel -> fetchSection(channel) }
    }

    private fun fetchSection(channel: ChannelConfig.Channel): ChannelSection {
        return try {
            val stream = fetcher.fetch(feedUrl(channel.id))
            val videos = stream.use { parser.parse(it, channel.displayName) }
            cache.write(channel.id, videos)
            ChannelSection(
                channelId = channel.id,
                channelName = channel.displayName,
                category = channel.category,
                videos = videos,
                refreshFailed = false,
            )
        } catch (e: IOException) {
            fallback(channel)
        } catch (e: Exception) {
            // Malformed feed XML, etc. — same fallback behavior as a network failure; the
            // rider shouldn't see a crash or an empty screen over one bad feed response.
            fallback(channel)
        }
    }

    private fun fallback(channel: ChannelConfig.Channel): ChannelSection {
        val cached = cache.read(channel.id).orEmpty()
        return ChannelSection(
            channelId = channel.id,
            channelName = channel.displayName,
            category = channel.category,
            videos = cached,
            refreshFailed = true,
        )
    }

    private fun feedUrl(channelId: String) =
        "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
}
