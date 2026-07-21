package dev.digitalducktape.openride.core.content

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContentCacheTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val cache = ContentCache(context)

    @Test
    fun `read returns null when nothing has been cached for a channel`() {
        assertNull(cache.read("never-written"))
    }

    @Test
    fun `write then read round-trips the video list`() {
        val videos = listOf(
            Video(
                id = "v1",
                title = "Title One",
                thumbnailUrl = "https://example.com/1.jpg",
                channelName = "Channel",
                durationSec = 1200,
                publishedEpochMs = 1000L,
            ),
            Video(
                id = "v2",
                title = "Title Two",
                thumbnailUrl = "https://example.com/2.jpg",
                channelName = "Channel",
                durationSec = null,
                publishedEpochMs = 2000L,
            ),
        )

        cache.write("channel-1", videos)
        val read = cache.read("channel-1")

        assertEquals(videos, read)
    }

    @Test
    fun `write then read preserves the non-startable flag`() {
        val feedOnly = sampleVideo("feedOnly").copy(startable = false)

        cache.write("channel-startable", listOf(feedOnly))
        val read = cache.read("channel-startable")

        assertEquals(listOf(false), read?.map { it.startable })
    }

    @Test
    fun `writing again for the same channel overwrites the previous cache`() {
        cache.write("channel-2", listOf(sampleVideo("first")))
        cache.write("channel-2", listOf(sampleVideo("second")))

        val read = cache.read("channel-2")

        assertEquals(listOf("second"), read?.map { it.id })
    }

    private fun sampleVideo(id: String) = Video(
        id = id,
        title = "Title",
        thumbnailUrl = "https://example.com/$id.jpg",
        channelName = "Channel",
        durationSec = null,
        publishedEpochMs = 0L,
    )
}
