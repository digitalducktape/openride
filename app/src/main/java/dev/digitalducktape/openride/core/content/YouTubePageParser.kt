package dev.digitalducktape.openride.core.content

import org.json.JSONArray
import org.json.JSONObject

/** A YouTube page couldn't be read as content — treated exactly like a failed fetch. */
class ContentParseException(message: String) : Exception(message)

/**
 * Extracts videos and playlists from a public YouTube page's embedded `ytInitialData` JSON.
 *
 * This is the app's only source of video *duration* and of a public-only listing: the RSS
 * feed the content layer started with carries neither, so the "no classes under 10 minutes"
 * and "no members-only classes" rules are impossible without it (and adding the YouTube Data
 * API would mean an API key, which this app deliberately doesn't use).
 *
 * The one structure this relies on is the `lockupViewModel` tile, which YouTube uses
 * identically on a channel's `/videos` tab, its `/playlists` tab, and a playlist's own page —
 * so [findLockups] walks the whole JSON tree looking for those tiles rather than following
 * the page-specific scaffolding around them, which differs per page type and changes more
 * often. Anything unexpected raises [ContentParseException], which the repository treats as
 * a failed fetch: filtering degrades, the catalog does not disappear.
 */
class YouTubePageParser(private val nowEpochMs: () -> Long = System::currentTimeMillis) {

    fun parseVideos(html: String, channelName: String): List<Video> =
        findLockups(html)
            .filter { it.optString(KEY_CONTENT_TYPE) == TYPE_VIDEO }
            .mapNotNull { lockup -> toVideo(lockup, channelName) }

    fun parsePlaylists(html: String): List<PlaylistSummary> =
        findLockups(html)
            .filter { it.optString(KEY_CONTENT_TYPE) == TYPE_PLAYLIST }
            .mapNotNull { lockup -> toPlaylist(lockup) }

    private fun toVideo(lockup: JSONObject, channelName: String): Video? {
        val id = lockup.optString(KEY_CONTENT_ID).takeIf { it.isNotEmpty() } ?: return null
        val metadata = lockup.optJSONObject(KEY_METADATA)?.optJSONObject(KEY_METADATA_VM)
        val title = metadata?.optJSONObject(KEY_TITLE)?.optString(KEY_CONTENT)
            ?.takeIf { it.isNotEmpty() } ?: return null
        val thumbnail = lockup.optJSONObject(KEY_CONTENT_IMAGE)?.optJSONObject(KEY_THUMBNAIL_VM)

        return Video(
            id = id,
            title = title,
            thumbnailUrl = firstImageUrl(thumbnail) ?: defaultThumbnailUrl(id),
            channelName = channelName,
            durationSec = durationBadges(thumbnail).firstNotNullOfOrNull(DurationText::toSeconds),
            publishedEpochMs = publishedEpochMs(metadata),
        )
    }

    private fun toPlaylist(lockup: JSONObject): PlaylistSummary? {
        val id = lockup.optString(KEY_CONTENT_ID).takeIf { it.isNotEmpty() } ?: return null
        val title = lockup.optJSONObject(KEY_METADATA)
            ?.optJSONObject(KEY_METADATA_VM)
            ?.optJSONObject(KEY_TITLE)
            ?.optString(KEY_CONTENT)
            ?.takeIf { it.isNotEmpty() } ?: return null
        val thumbnail = lockup.optJSONObject(KEY_CONTENT_IMAGE)
            ?.optJSONObject(KEY_COLLECTION_VM)
            ?.optJSONObject(KEY_PRIMARY_THUMBNAIL)
            ?.optJSONObject(KEY_THUMBNAIL_VM)

        return PlaylistSummary(
            id = id,
            title = title,
            thumbnailUrl = firstImageUrl(thumbnail).orEmpty(),
            videoCount = countBadges(thumbnail).firstNotNullOfOrNull(::parseVideoCount),
        )
    }

    /** `"4 videos"` → 4. */
    private fun parseVideoCount(text: String): Int? =
        Regex("""^(\d+)\s+video""").find(text.trim().lowercase())?.groupValues?.get(1)?.toIntOrNull()

    private fun publishedEpochMs(metadata: JSONObject?): Long {
        val rows = metadata?.optJSONObject(KEY_METADATA)
            ?.optJSONObject(KEY_CONTENT_METADATA_VM)
            ?.optJSONArray(KEY_METADATA_ROWS)
            ?: return 0L
        val now = nowEpochMs()
        for (row in rows.objects()) {
            for (part in row.optJSONArray(KEY_METADATA_PARTS).orEmptyArray().objects()) {
                val text = part.optJSONObject(KEY_TEXT)?.optString(KEY_CONTENT).orEmpty()
                RelativeTime.toEpochMs(text, now)?.let { return it }
            }
        }
        return 0L
    }

    private fun firstImageUrl(thumbnail: JSONObject?): String? =
        thumbnail?.optJSONObject(KEY_IMAGE)
            ?.optJSONArray(KEY_SOURCES)
            .orEmptyArray()
            .objects()
            .firstNotNullOfOrNull { it.optString(KEY_URL).takeIf { url -> url.isNotEmpty() } }

    /** Duration text lives in the bottom-overlay badges of a video thumbnail. */
    private fun durationBadges(thumbnail: JSONObject?): List<String> =
        thumbnail?.optJSONArray(KEY_OVERLAYS).orEmptyArray().objects().flatMap { overlay ->
            overlay.optJSONObject(KEY_BOTTOM_OVERLAY_VM)
                ?.optJSONArray(KEY_BADGES)
                .orEmptyArray()
                .objects()
                .mapNotNull { it.optJSONObject(KEY_BADGE_VM)?.optString(KEY_TEXT_FIELD) }
        }

    /** Video-count text lives in the badge overlay of a playlist's collection thumbnail. */
    private fun countBadges(thumbnail: JSONObject?): List<String> =
        thumbnail?.optJSONArray(KEY_OVERLAYS).orEmptyArray().objects().flatMap { overlay ->
            overlay.optJSONObject(KEY_BADGE_OVERLAY_VM)
                ?.optJSONArray(KEY_THUMBNAIL_BADGES)
                .orEmptyArray()
                .objects()
                .mapNotNull { it.optJSONObject(KEY_BADGE_VM)?.optString(KEY_TEXT_FIELD) }
        }

    private fun findLockups(html: String): List<JSONObject> {
        val json = extractInitialData(html)
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw ContentParseException("ytInitialData was not readable JSON")
        }
        val lockups = mutableListOf<JSONObject>()
        collectLockups(root, lockups)
        return lockups
    }

    private fun collectLockups(node: Any?, into: MutableList<JSONObject>) {
        when (node) {
            is JSONObject -> {
                node.optJSONObject(KEY_LOCKUP_VM)?.let { into.add(it) }
                node.keys().forEach { key -> collectLockups(node.opt(key), into) }
            }
            is JSONArray -> (0 until node.length()).forEach { collectLockups(node.opt(it), into) }
        }
    }

    /**
     * Pulls the `var ytInitialData = {...};` object out of the page by brace-matching from the
     * opening `{`, rather than with a regex — the blob is ~1 MB of JSON containing arbitrary
     * `}` and `</script>` sequences inside string values, so a lazy regex match truncates it.
     */
    private fun extractInitialData(html: String): String {
        val marker = MARKERS.firstNotNullOfOrNull { marker ->
            html.indexOf(marker).takeIf { it >= 0 }?.let { it + marker.length }
        } ?: throw ContentParseException("no ytInitialData in page")

        val start = html.indexOf('{', marker)
        if (start < 0) throw ContentParseException("ytInitialData had no object body")

        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until html.length) {
            val char = html[index]
            when {
                escaped -> escaped = false
                char == '\\' && inString -> escaped = true
                char == '"' -> inString = !inString
                inString -> Unit
                char == '{' -> depth++
                char == '}' -> {
                    depth--
                    if (depth == 0) return html.substring(start, index + 1)
                }
            }
        }
        throw ContentParseException("ytInitialData object was never closed")
    }

    private fun JSONArray?.orEmptyArray(): JSONArray = this ?: JSONArray()

    private fun JSONArray.objects(): List<JSONObject> =
        (0 until length()).mapNotNull { optJSONObject(it) }

    private fun defaultThumbnailUrl(videoId: String) =
        "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

    private companion object {
        val MARKERS = listOf("var ytInitialData = ", "window[\"ytInitialData\"] = ")
        const val TYPE_VIDEO = "LOCKUP_CONTENT_TYPE_VIDEO"
        const val TYPE_PLAYLIST = "LOCKUP_CONTENT_TYPE_PLAYLIST"
        const val KEY_LOCKUP_VM = "lockupViewModel"
        const val KEY_CONTENT_ID = "contentId"
        const val KEY_CONTENT_TYPE = "contentType"
        const val KEY_CONTENT_IMAGE = "contentImage"
        const val KEY_THUMBNAIL_VM = "thumbnailViewModel"
        const val KEY_COLLECTION_VM = "collectionThumbnailViewModel"
        const val KEY_PRIMARY_THUMBNAIL = "primaryThumbnail"
        const val KEY_IMAGE = "image"
        const val KEY_SOURCES = "sources"
        const val KEY_URL = "url"
        const val KEY_OVERLAYS = "overlays"
        const val KEY_BOTTOM_OVERLAY_VM = "thumbnailBottomOverlayViewModel"
        const val KEY_BADGE_OVERLAY_VM = "thumbnailOverlayBadgeViewModel"
        const val KEY_THUMBNAIL_BADGES = "thumbnailBadges"
        const val KEY_BADGES = "badges"
        const val KEY_BADGE_VM = "thumbnailBadgeViewModel"
        const val KEY_TEXT_FIELD = "text"
        const val KEY_METADATA = "metadata"
        const val KEY_METADATA_VM = "lockupMetadataViewModel"
        const val KEY_CONTENT_METADATA_VM = "contentMetadataViewModel"
        const val KEY_METADATA_ROWS = "metadataRows"
        const val KEY_METADATA_PARTS = "metadataParts"
        const val KEY_TITLE = "title"
        const val KEY_TEXT = "text"
        const val KEY_CONTENT = "content"
    }
}
