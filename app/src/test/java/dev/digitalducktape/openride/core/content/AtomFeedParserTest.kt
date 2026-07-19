package dev.digitalducktape.openride.core.content

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Parses the checked-in fixture feed (`src/test/resources/fixtures/sample_atom_feed.xml`) —
 * a synthetic feed matching the real shape of a fetched YouTube channel Atom feed (verified
 * against a live feed while resolving T9's channel ids), rather than real creator content.
 */
@RunWith(AndroidJUnit4::class)
class AtomFeedParserTest {

    private val parser = AtomFeedParser()

    private fun loadFixture() =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/sample_atom_feed.xml")) {
            "fixture missing"
        }

    @Test
    fun `parses every entry in the feed`() {
        val videos = parser.parse(loadFixture(), fallbackChannelName = "fallback")

        assertEquals(3, videos.size)
    }

    @Test
    fun `extracts id, title and channel name`() {
        val videos = parser.parse(loadFixture(), fallbackChannelName = "fallback")

        val first = videos[0]
        assertEquals("testVideoOne1", first.id)
        assertEquals("Test Ride One - Scenic Loop", first.title)
        assertEquals("Test Cycling Channel", first.channelName)
    }

    @Test
    fun `uses the feed-level title as channel name, not the fallback`() {
        val videos = parser.parse(loadFixture(), fallbackChannelName = "should not appear")

        assertTrue(videos.all { it.channelName == "Test Cycling Channel" })
    }

    @Test
    fun `extracts thumbnail url from media thumbnail element`() {
        val videos = parser.parse(loadFixture(), fallbackChannelName = "fallback")

        assertEquals(
            "https://i1.ytimg.com/vi/testVideoOne1/hqdefault.jpg",
            videos[0].thumbnailUrl,
        )
    }

    @Test
    fun `falls back to a default thumbnail url when the entry has no media thumbnail`() {
        val videos = parser.parse(loadFixture(), fallbackChannelName = "fallback")

        val thirdEntry = videos.first { it.id == "noThumbVideo3" }
        assertEquals("https://i.ytimg.com/vi/noThumbVideo3/hqdefault.jpg", thirdEntry.thumbnailUrl)
    }

    @Test
    fun `parses published timestamp to epoch millis`() {
        val videos = parser.parse(loadFixture(), fallbackChannelName = "fallback")

        // 2024-03-10T11:00:00Z
        assertEquals(1710068400000L, videos[0].publishedEpochMs)
    }

    @Test
    fun `duration is always null since the feed never includes it`() {
        val videos = parser.parse(loadFixture(), fallbackChannelName = "fallback")

        assertTrue(videos.all { it.durationSec == null })
    }

    @Test
    fun `videos are returned in feed order`() {
        val videos = parser.parse(loadFixture(), fallbackChannelName = "fallback")

        assertEquals(listOf("testVideoOne1", "testVideoTwo2", "noThumbVideo3"), videos.map { it.id })
    }

    @Test
    fun `empty feed yields no videos`() {
        val emptyFeed = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
             <title>Empty Channel</title>
            </feed>
        """.trimIndent()

        val videos = parser.parse(emptyFeed.byteInputStream(), fallbackChannelName = "fallback")

        assertTrue(videos.isEmpty())
    }

    @Test
    fun `feed with no title falls back to the provided channel name`() {
        val noTitleFeed = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns="http://www.w3.org/2005/Atom">
             <entry>
              <yt:videoId>abc</yt:videoId>
              <title>Some video</title>
             </entry>
            </feed>
        """.trimIndent()

        val videos = parser.parse(noTitleFeed.byteInputStream(), fallbackChannelName = "Fallback Channel")

        assertEquals("Fallback Channel", videos.single().channelName)
    }

    @Test
    fun `entry missing a video id is skipped rather than crashing`() {
        val missingIdFeed = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns="http://www.w3.org/2005/Atom">
             <title>Channel</title>
             <entry>
              <title>No id here</title>
             </entry>
             <entry>
              <yt:videoId>hasId</yt:videoId>
              <title>Has id</title>
             </entry>
            </feed>
        """.trimIndent()

        val videos = parser.parse(missingIdFeed.byteInputStream(), fallbackChannelName = "fallback")

        assertEquals(1, videos.size)
        assertEquals("hasId", videos.single().id)
    }

    @Test
    fun `malformed published timestamp yields zero instead of throwing`() {
        val badDateFeed = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns="http://www.w3.org/2005/Atom">
             <title>Channel</title>
             <entry>
              <yt:videoId>abc</yt:videoId>
              <title>Video</title>
              <published>not-a-date</published>
             </entry>
            </feed>
        """.trimIndent()

        val videos = parser.parse(badDateFeed.byteInputStream(), fallbackChannelName = "fallback")

        assertEquals(0L, videos.single().publishedEpochMs)
    }
}
