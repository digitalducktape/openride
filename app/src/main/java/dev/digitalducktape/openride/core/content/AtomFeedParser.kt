package dev.digitalducktape.openride.core.content

import android.util.Xml
import java.io.InputStream
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import org.xmlpull.v1.XmlPullParser

/**
 * Parses a YouTube channel's Atom feed (`/feeds/videos.xml?channel_id=...`) into [Video]s,
 * using Android's built-in [XmlPullParser] (via [Xml.newPullParser]) — no XML parsing
 * library dependency (T9).
 *
 * Unlike a raw `XmlPullParserFactory` instance, [Xml.newPullParser] enables namespace
 * processing, so a prefixed element like `<yt:videoId>` reports [XmlPullParser.getName] as
 * just `videoId` (the prefix is split off into a separate namespace URI we don't need here).
 * We match on those bare local names below rather than the on-the-wire prefixed form.
 *
 * Feed shape (per an actual fetched feed, trimmed):
 * ```
 * <feed xmlns:yt="..." xmlns:media="..." xmlns="http://www.w3.org/2005/Atom">
 *   <title>Channel Name</title>            <!-- feed-level: channel name -->
 *   <entry>
 *     <yt:videoId>abc123</yt:videoId>
 *     <title>Video title</title>            <!-- entry-level: video title -->
 *     <published>2024-01-01T00:00:00+00:00</published>
 *     <media:group>
 *       <media:thumbnail url="https://i.ytimg.com/vi/abc123/hqdefault.jpg" .../>
 *     </media:group>
 *   </entry>
 *   ...
 * </feed>
 * ```
 */
class AtomFeedParser {

    /**
     * @param input the feed's raw XML bytes.
     * @param fallbackChannelName used only if the feed has no feed-level `<title>` (shouldn't
     *   happen in practice, but keeps this from ever producing a blank channel name).
     */
    fun parse(input: InputStream, fallbackChannelName: String): List<Video> {
        val parser = Xml.newPullParser()
        parser.setInput(input, null)

        val videos = mutableListOf<Video>()
        var channelName = fallbackChannelName

        var inEntry = false
        var videoId: String? = null
        var title: String? = null
        var thumbnailUrl: String? = null
        var publishedEpochMs: Long = 0L

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        TAG_ENTRY -> {
                            inEntry = true
                            videoId = null
                            title = null
                            thumbnailUrl = null
                            publishedEpochMs = 0L
                        }
                        TAG_VIDEO_ID -> if (inEntry) videoId = parser.nextText().trim()
                        TAG_TITLE -> {
                            val text = parser.nextText()
                            if (inEntry) title = text else channelName = text
                        }
                        TAG_THUMBNAIL -> if (inEntry) {
                            thumbnailUrl = parser.getAttributeValue(null, ATTR_URL)
                        }
                        TAG_PUBLISHED -> if (inEntry) {
                            publishedEpochMs = parsePublished(parser.nextText())
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == TAG_ENTRY) {
                        val id = videoId
                        val entryTitle = title
                        if (id != null && entryTitle != null) {
                            videos.add(
                                Video(
                                    id = id,
                                    title = entryTitle,
                                    thumbnailUrl = thumbnailUrl ?: defaultThumbnailUrl(id),
                                    channelName = channelName,
                                    durationSec = null,
                                    publishedEpochMs = publishedEpochMs,
                                ),
                            )
                        }
                        inEntry = false
                    }
                }
            }
            eventType = parser.next()
        }

        return videos
    }

    private fun defaultThumbnailUrl(videoId: String) = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

    /** Feed timestamps are ISO-8601 with a numeric offset (e.g. `+00:00`), not a literal `Z`. */
    private fun parsePublished(text: String): Long = try {
        OffsetDateTime.parse(text).toInstant().toEpochMilli()
    } catch (e: DateTimeParseException) {
        0L
    }

    private companion object {
        const val TAG_ENTRY = "entry"
        const val TAG_VIDEO_ID = "videoId"
        const val TAG_TITLE = "title"
        const val TAG_THUMBNAIL = "thumbnail"
        const val TAG_PUBLISHED = "published"
        const val ATTR_URL = "url"
    }
}
