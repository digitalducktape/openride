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

    /**
     * Throws [ContentParseException] rather than returning an empty list when zero video
     * lockups are found. A page we can parse as JSON but that has no video tiles at all is
     * indistinguishable from a page whose markup shape changed underneath [findLockups] (the
     * `lockupViewModel` key it looks for is itself a recent rename of `gridVideoRenderer`, so
     * this will happen again) — both look identical from here. Treating "found nothing" as
     * unreadable, same as malformed JSON, routes it through the repository's existing
     * failed-fetch handling (degrade to feed/cache) instead of a "successful" fetch that
     * quietly reports an empty catalog and overwrites the on-disk cache with nothing. Contrast
     * with [parsePlaylists] below, which must NOT do this: a creator legitimately having zero
     * playlists is common and correct, not a signal anything broke.
     */
    fun parseVideos(html: String, channelName: String): List<Video> {
        val videos = findLockups(html)
            .filter { it.optString(KEY_CONTENT_TYPE) == TYPE_VIDEO }
            .mapNotNull { lockup -> toVideo(lockup, channelName) }
        if (videos.isEmpty()) {
            throw ContentParseException("no video lockups found on page")
        }
        return videos
    }

    fun parsePlaylists(html: String): List<PlaylistSummary> =
        findLockups(html)
            .filter { it.optString(KEY_CONTENT_TYPE) == TYPE_PLAYLIST }
            .mapNotNull { lockup -> toPlaylist(lockup) }

    private fun toVideo(lockup: JSONObject, channelName: String): Video? {
        val id = lockup.optString(KEY_CONTENT_ID).takeIf { it.isNotEmpty() } ?: return null
        val metadata = lockup.optJSONObject(KEY_METADATA)?.optJSONObject(KEY_METADATA_VM)
        // Members-only videos appear here exactly like public ones — same lockup shape, a real
        // duration badge — so nothing above filters them out. They're unplayable without a paid
        // channel membership, so letting one into the catalog strands the rider on YouTube's
        // "Join this channel" wall the moment they start it. Their one distinguishing mark is a
        // BADGE_MEMBERS_ONLY badge in the metadata rows; drop the tile when it's present. This is
        // the "no members-only classes" rule (see the class KDoc) — the page is the only source
        // that carries the signal, so it has to happen right here.
        if (isMembersOnly(metadata)) return null
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

    /**
     * True when this tile's metadata rows carry a [STYLE_MEMBERS_ONLY] badge. Matches on the
     * badge *style* rather than its `"Members only"` text because the text is localized (a
     * German channel says `"Nur für Mitglieder"`) while the style enum is stable across
     * locales.
     */
    private fun isMembersOnly(metadata: JSONObject?): Boolean {
        val rows = metadata?.optJSONObject(KEY_METADATA)
            ?.optJSONObject(KEY_CONTENT_METADATA_VM)
            ?.optJSONArray(KEY_METADATA_ROWS)
            ?: return false
        return rows.objects().any { row ->
            row.optJSONArray(KEY_BADGES).orEmptyArray().objects().any { badge ->
                badge.optJSONObject(KEY_BADGE_VIEW_MODEL)?.optString(KEY_BADGE_STYLE) == STYLE_MEMBERS_ONLY
            }
        }
    }

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

    /**
     * @param depth how many levels deep [node] is. [MAX_DEPTH] is far past anything a real
     *   YouTube page nests (a few dozen levels at most), so hitting it means pathological or
     *   adversarial input, not a legitimate page shape. Stopping there — rather than recursing
     *   unbounded — keeps this a "no lockups found" result (a clean [ContentParseException] via
     *   [parseVideos], same as any other unreadable page) instead of a [StackOverflowError],
     *   which is an [Error] rather than an [Exception] and would sail straight past every catch
     *   clause in the content layer and crash the Classes tab instead of degrading.
     */
    private fun collectLockups(node: Any?, into: MutableList<JSONObject>, depth: Int = 0) {
        if (depth > MAX_DEPTH) return
        when (node) {
            is JSONObject -> {
                node.optJSONObject(KEY_LOCKUP_VM)?.let { into.add(it) }
                node.keys().forEach { key -> collectLockups(node.opt(key), into, depth + 1) }
            }
            is JSONArray -> (0 until node.length()).forEach { collectLockups(node.opt(it), into, depth + 1) }
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
        /** See [collectLockups]'s KDoc for why this exists and why 200 is safely past real pages. */
        const val MAX_DEPTH = 200
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
        const val KEY_BADGE_VIEW_MODEL = "badgeViewModel"
        const val KEY_BADGE_STYLE = "badgeStyle"
        const val STYLE_MEMBERS_ONLY = "BADGE_MEMBERS_ONLY"
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
