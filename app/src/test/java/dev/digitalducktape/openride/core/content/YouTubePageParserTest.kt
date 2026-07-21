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
    fun `parseVideos throws when a well-formed page has no video tiles`() {
        // A page we can still parse as JSON but that yields zero video lockups is
        // indistinguishable from a page whose markup changed underneath us (Finding 1): both
        // look like "zero videos." Treating it as unreadable — same as malformed JSON — means
        // the repository's existing null-on-failure fallback kicks in, instead of a healthy
        // fetch quietly reporting an empty catalog and wiping the on-disk cache to match.
        val html = """<script>var ytInitialData = {"contents":{"items":[]}};</script>"""

        assertThrows(ContentParseException::class.java) { parser.parseVideos(html, "Test Channel") }
    }

    @Test
    fun `parsePlaylists still returns an empty list when a creator genuinely has none`() {
        // Unlike parseVideos above, zero playlists is a normal, common state for a creator —
        // it must not be conflated with an unreadable page.
        val html = """<script>var ytInitialData = {"contents":{"items":[]}};</script>"""

        assertEquals(emptyList<PlaylistSummary>(), parser.parsePlaylists(html))
    }

    @Test
    fun `a lockup buried past the depth guard is not found`() {
        // Proves collectLockups actually stops descending rather than walking arbitrarily deep
        // trees: a real, well-formed video lockup planted 300 levels down (past the 200-level
        // guard) must not be found, whereas the same lockup a handful of levels down is found
        // fine (see the top-level tests above). This can't be tested by chasing an actual
        // StackOverflowError — org.json's own JSONObject(String) parser overflows on pathological
        // input well before collectLockups' own (lighter) recursion would, at a depth that
        // varies with the JVM's stack size, so a literal crash-reproduction test would be
        // flaky across environments. Testing the guard's mechanics directly is deterministic.
        val depth = 300
        val lockup = """
            {"lockupViewModel":{"contentId":"vidDeep0001","contentType":"LOCKUP_CONTENT_TYPE_VIDEO",
            "metadata":{"lockupMetadataViewModel":{"title":{"content":"Deep Video"}}}}}
        """.trimIndent()
        val nested = buildString {
            repeat(depth) { append("{\"wrap\":") }
            append(lockup)
            repeat(depth) { append('}') }
        }
        val html = "<script>var ytInitialData = $nested;</script>"

        // Buried past the guard: findable as valid JSON, but never reached, so this is the
        // same "no video lockups found" case as a genuinely broken page (Finding 1a).
        assertThrows(ContentParseException::class.java) { parser.parseVideos(html, "Test Channel") }
    }
}
