package dev.digitalducktape.openride.core.content

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.digitalducktape.openride.core.data.OpenRideDatabase
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YouTubeContentRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private lateinit var db: OpenRideDatabase
    private lateinit var sources: ContentSourceRepository

    /** Matches the ids in `fixtures/channel_videos_page.html`: long / short / no-duration. */
    private fun pageHtml() = readFixture("channel_videos_page.html")
    private fun playlistsHtml() = readFixture("channel_playlists_page.html")

    private fun readFixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name"))
            .bufferedReader().use { it.readText() }

    private fun feedXml(): InputStream =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/sample_atom_feed.xml"))

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(context, OpenRideDatabase::class.java).build()
        sources = ContentSourceRepository(db.contentSourceDao())
        sources.add(
            ResolvedSource(ContentSourceType.CHANNEL, "UCTestChannel00000000", "Test Cycling Channel"),
            ContentCategory.Scenic,
        )
        context.filesDir.resolve("content_cache").deleteRecursively()
    }

    @After
    fun tearDown() = db.close()

    /** Serves page HTML for `/videos` and `/playlists` URLs, and the Atom fixture for feeds. */
    private fun fetcher(
        page: (() -> String)? = { pageHtml() },
        feed: (() -> InputStream)? = { feedXml() },
        playlists: () -> String = { playlistsHtml() },
    ) = FeedFetcher { url ->
        when {
            url.contains("/playlists") -> playlists().byteInputStream()
            url.contains("feeds/videos.xml") ->
                feed?.invoke() ?: throw IOException("feed down")
            else -> (page?.invoke() ?: throw IOException("page down")).byteInputStream()
        }
    }

    private fun repository(fetcher: FeedFetcher) = YouTubeContentRepository(
        context = context,
        sourceRepository = sources,
        fetcher = fetcher,
        cache = ContentCache(context),
    )

    @Test
    fun `page videos carry durations and drop anything under ten minutes`() = runTest {
        val section = repository(fetcher()).channelSections().single()

        assertFalse(section.refreshFailed)
        // vidShort0002 is 5:04 and must be filtered out; the LIVE tile has no known duration
        // and is kept.
        assertEquals(listOf("vidLong00001", "vidNoDur0003"), section.videos.map { it.id })
        assertEquals(1103, section.videos.first().durationSec)
    }

    @Test
    fun `section reports the source row it came from`() = runTest {
        val source = sources.visibleOnce().single()

        val section = repository(fetcher()).channelSections().single()

        assertEquals(source.id, section.sourceId)
        assertEquals("UCTestChannel00000000", section.channelId)
        assertEquals("Test Cycling Channel", section.channelName)
        assertEquals(ContentCategory.Scenic, section.category)
    }

    @Test
    fun `videos absent from the page listing are dropped as non-public`() = runTest {
        // The Atom fixture's ids are not present in the page fixture, so a successful page
        // fetch means none of them survive — that's the members-only rule.
        val section = repository(fetcher()).channelSections().single()

        assertTrue(section.videos.none { it.id.startsWith("video") })
    }

    @Test
    fun `a failed page fetch degrades to the feed with unknown durations`() = runTest {
        val section = repository(fetcher(page = null)).channelSections().single()

        assertFalse(section.refreshFailed)
        assertEquals(3, section.videos.size)
        assertTrue(section.videos.all { it.durationSec == null })
    }

    @Test
    fun `both fetches failing with no cache yields an empty section flagged as failed`() = runTest {
        val section = repository(fetcher(page = null, feed = null)).channelSections().single()

        assertTrue(section.refreshFailed)
        assertTrue(section.videos.isEmpty())
    }

    @Test
    fun `both fetches failing falls back to the cached list`() = runTest {
        repository(fetcher()).channelSections() // primes the cache

        val section = repository(fetcher(page = null, feed = null)).channelSections().single()

        assertTrue(section.refreshFailed)
        assertEquals(listOf("vidLong00001", "vidNoDur0003"), section.videos.map { it.id })
    }

    @Test
    fun `a transient failure is retried once before falling back`() = runTest {
        var attempts = 0
        val flaky = FeedFetcher { url ->
            if (url.contains("feeds/videos.xml")) feedXml()
            else {
                attempts++
                if (attempts == 1) throw IOException("HTTP 500") else pageHtml().byteInputStream()
            }
        }

        val section = repository(flaky).channelSections().single()

        assertEquals(2, attempts)
        assertFalse(section.refreshFailed)
        assertEquals(1103, section.videos.first().durationSec)
    }

    @Test
    fun `hidden sources are not fetched`() = runTest {
        val source = sources.visibleOnce().single()
        sources.setHidden(source.id, true)

        assertTrue(repository(fetcher()).channelSections().isEmpty())
    }

    @Test
    fun `creatorContent returns the latest videos and the creator's playlists`() = runTest {
        val source = sources.visibleOnce().single()

        val content = repository(fetcher()).creatorContent(source.id)!!

        assertEquals("Test Cycling Channel", content.displayName)
        assertEquals(listOf("vidLong00001", "vidNoDur0003"), content.latest.map { it.id })
        assertEquals(listOf("PLtheme000000001", "PLtheme000000002"), content.playlists.map { it.id })
        assertFalse(content.refreshFailed)
    }

    @Test
    fun `creatorContent returns null for an unknown source id`() = runTest {
        assertNull(repository(fetcher()).creatorContent(9999L))
    }

    @Test
    fun `creatorContent still lists videos when the playlists tab fails`() = runTest {
        val fetcher = FeedFetcher { url ->
            when {
                url.contains("/playlists") -> throw IOException("playlists down")
                url.contains("feeds/videos.xml") -> feedXml()
                else -> pageHtml().byteInputStream()
            }
        }
        val source = sources.visibleOnce().single()

        val content = repository(fetcher).creatorContent(source.id)!!

        assertEquals(2, content.latest.size)
        assertTrue(content.playlists.isEmpty())
    }

    @Test
    fun `playlistVideos applies the same duration filter`() = runTest {
        val videos = repository(fetcher()).playlistVideos("PLtheme000000001", "Themed Rides")

        assertEquals(listOf("vidLong00001", "vidNoDur0003"), videos.map { it.id })
        assertEquals("Themed Rides", videos.first().channelName)
    }

    @Test
    fun `a playlist source is fetched from its playlist page`() = runTest {
        val playlistId = sources.add(
            ResolvedSource(ContentSourceType.PLAYLIST, "PLtheme000000001", "Themed Rides"),
            ContentCategory.Workout,
        )
        var requestedPlaylistPage = false
        val fetcher = FeedFetcher { url ->
            if (url.contains("playlist?list=PLtheme000000001")) requestedPlaylistPage = true
            when {
                url.contains("feeds/videos.xml") -> feedXml()
                else -> pageHtml().byteInputStream()
            }
        }

        val sections = repository(fetcher).channelSections()

        assertTrue(requestedPlaylistPage)
        assertEquals(2, sections.size)
        assertEquals(playlistId, sections.last().sourceId)
    }
}
