package dev.digitalducktape.openride.core.content

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * On-disk last-known-good cache for each channel's video list, so an unreachable feed still
 * has something to show (PRD P0-6: "a cached/last-known list ... rather than disappearing").
 *
 * Stored as one small JSON file per channel under `filesDir/content_cache/`. Deliberately
 * hand-rolled with [org.json] (bundled with Android, no new dependency) rather than
 * DataStore/a serialization library — the data is a handful of small lists, not worth the
 * extra dependency weight.
 */
class ContentCache(context: Context) {
    private val cacheDir: File = File(context.filesDir, CACHE_DIR_NAME).apply { mkdirs() }

    fun write(channelId: String, videos: List<Video>) {
        val array = JSONArray()
        videos.forEach { video ->
            array.put(
                JSONObject().apply {
                    put(KEY_ID, video.id)
                    put(KEY_TITLE, video.title)
                    put(KEY_THUMBNAIL_URL, video.thumbnailUrl)
                    put(KEY_CHANNEL_NAME, video.channelName)
                    put(KEY_DURATION_SEC, video.durationSec ?: JSONObject.NULL)
                    put(KEY_PUBLISHED_EPOCH_MS, video.publishedEpochMs)
                },
            )
        }
        file(channelId).writeText(array.toString())
    }

    /** Returns `null` if there's no cache yet for [channelId] (never successfully fetched). */
    fun read(channelId: String): List<Video>? {
        val file = file(channelId)
        if (!file.exists()) return null
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                Video(
                    id = obj.getString(KEY_ID),
                    title = obj.getString(KEY_TITLE),
                    thumbnailUrl = obj.getString(KEY_THUMBNAIL_URL),
                    channelName = obj.getString(KEY_CHANNEL_NAME),
                    durationSec = if (obj.isNull(KEY_DURATION_SEC)) null else obj.getInt(KEY_DURATION_SEC),
                    publishedEpochMs = obj.getLong(KEY_PUBLISHED_EPOCH_MS),
                )
            }
        } catch (e: Exception) {
            // Corrupt/partial cache file — treat exactly like "no cache," never crash the
            // Classes screen over a bad local file.
            null
        }
    }

    private fun file(channelId: String) = File(cacheDir, "$channelId.json")

    private companion object {
        const val CACHE_DIR_NAME = "content_cache"
        const val KEY_ID = "id"
        const val KEY_TITLE = "title"
        const val KEY_THUMBNAIL_URL = "thumbnailUrl"
        const val KEY_CHANNEL_NAME = "channelName"
        const val KEY_DURATION_SEC = "durationSec"
        const val KEY_PUBLISHED_EPOCH_MS = "publishedEpochMs"
    }
}
