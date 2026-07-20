package dev.digitalducktape.openride.core.content

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YouTubePageParserTest {

    private val now = 1_700_000_000_000L
    private val parser = YouTubePageParser(nowEpochMs = { now })

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name"))
            .bufferedReader().use { it.readText() }

    @Test
    fun `parses every video tile on a channel videos page`() {
        val videos = parser.parseVideos(fixture("channel_videos_page.html"), "Test Channel")

        assertEquals(listOf("vidLong00001", "vidShort0002", "vidNoDur0003"), videos.map { it.id })
        assertEquals("Quick HIIT Workout | Instant Inferno", videos[0].title)
        assertEquals("Test Channel", videos[0].channelName)
        assertTrue(videos[0].thumbnailUrl.startsWith("https://i.ytimg.com/vi/vidLong00001/"))
    }

    @Test
    fun `reads the duration badge as seconds`() {
        val videos = parser.parseVideos(fixture("channel_videos_page.html"), "Test Channel")

        assertEquals(1103, videos[0].durationSec)
        assertEquals(304, videos[1].durationSec)
    }

    @Test
    fun `leaves duration null when the badge is not a clock`() {
        val videos = parser.parseVideos(fixture("channel_videos_page.html"), "Test Channel")

        assertNull(videos[2].durationSec)
    }

    @Test
    fun `approximates published time from the relative label`() {
        val videos = parser.parseVideos(fixture("channel_videos_page.html"), "Test Channel")

        assertEquals(now - 4L * 30 * 86_400_000L, videos[0].publishedEpochMs)
        assertEquals(now - 2L * 86_400_000L, videos[1].publishedEpochMs)
    }

    @Test
    fun `published time is zero when no relative label is present`() {
        val videos = parser.parseVideos(fixture("channel_videos_page.html"), "Test Channel")

        assertEquals(0L, videos[2].publishedEpochMs)
    }

    @Test
    fun `parses playlist tiles and ignores video tiles`() {
        val playlists = parser.parsePlaylists(fixture("channel_playlists_page.html"))

        assertEquals(listOf("PLtheme000000001", "PLtheme000000002"), playlists.map { it.id })
        assertEquals("Love & Hate Spin Classes", playlists[0].title)
        assertEquals(4, playlists[0].videoCount)
        assertTrue(playlists[0].thumbnailUrl.contains("coverA"))
    }

    @Test
    fun `playlist video count is null when the badge is missing`() {
        val playlists = parser.parsePlaylists(fixture("channel_playlists_page.html"))

        assertNull(playlists[1].videoCount)
    }

    @Test
    fun `parseVideos ignores playlist tiles`() {
        val videos = parser.parseVideos(fixture("channel_playlists_page.html"), "Test Channel")

        assertEquals(listOf("vidNotAList1"), videos.map { it.id })
    }

    @Test
    fun `throws when the page has no ytInitialData`() {
        assertThrows(ContentParseException::class.java) {
            parser.parseVideos("<html><body>signed out wall</body></html>", "Test Channel")
        }
    }

    @Test
    fun `throws when ytInitialData is not valid json`() {
        val html = """<script>var ytInitialData = {"broken":;</script>"""
        assertThrows(ContentParseException::class.java) { parser.parseVideos(html, "Test Channel") }
    }

    @Test
    fun `returns an empty list for a well-formed page with no tiles`() {
        val html = """<script>var ytInitialData = {"contents":{"items":[]}};</script>"""

        assertEquals(emptyList<Video>(), parser.parseVideos(html, "Test Channel"))
    }
}
