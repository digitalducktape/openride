package dev.digitalducktape.openride.core.content

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YouTubeContentRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val testChannel = ChannelConfig.Channel(
        id = "UCTestChannel00000000",
        displayName = "Test Cycling Channel",
        handle = "@test",
        category = ContentCategory.Scenic,
    )

    private fun fixtureStream(): InputStream =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/sample_atom_feed.xml"))

    @Test
    fun `successful fetch returns parsed videos with refreshFailed false`() = runTest {
        val repository = YouTubeContentRepository(
            context = context,
            channels = listOf(testChannel),
            fetcher = FeedFetcher { fixtureStream() },
            cache = ContentCache(context),
        )

        val sections = repository.channelSections()

        assertEquals(1, sections.size)
        val section = sections.single()
        assertFalse(section.refreshFailed)
        assertEquals(3, section.videos.size)
        assertEquals(testChannel.id, section.channelId)
        assertEquals(testChannel.category, section.category)
    }

    @Test
    fun `failed fetch with no prior cache yields empty list and refreshFailed true`() = runTest {
        val repository = YouTubeContentRepository(
            context = context,
            channels = listOf(testChannel),
            fetcher = FeedFetcher { throw IOException("network down") },
            cache = ContentCache(context),
        )

        val section = repository.channelSections().single()

        assertTrue(section.refreshFailed)
        assertTrue(section.videos.isEmpty())
    }

    @Test
    fun `failed fetch after a prior success falls back to the cached videos`() = runTest {
        val cache = ContentCache(context)
        val firstRepository = YouTubeContentRepository(
            context = context,
            channels = listOf(testChannel),
            fetcher = FeedFetcher { fixtureStream() },
            cache = cache,
        )
        firstRepository.channelSections() // primes the cache

        val secondRepository = YouTubeContentRepository(
            context = context,
            channels = listOf(testChannel),
            fetcher = FeedFetcher { throw IOException("network down") },
            cache = cache,
        )
        val section = secondRepository.channelSections().single()

        assertTrue(section.refreshFailed)
        assertEquals(3, section.videos.size)
    }

    @Test
    fun `malformed feed content degrades to fallback instead of throwing`() = runTest {
        val repository = YouTubeContentRepository(
            context = context,
            channels = listOf(testChannel),
            fetcher = FeedFetcher { "not xml at all".byteInputStream() },
            cache = ContentCache(context),
        )

        val section = repository.channelSections().single()

        assertTrue(section.refreshFailed)
    }

    @Test
    fun `channelSections returns one section per configured channel in order`() = runTest {
        val secondChannel = testChannel.copy(id = "UCOther", displayName = "Other Channel")
        val repository = YouTubeContentRepository(
            context = context,
            channels = listOf(testChannel, secondChannel),
            fetcher = FeedFetcher { fixtureStream() },
            cache = ContentCache(context),
        )

        val sections = repository.channelSections()

        assertEquals(listOf(testChannel.id, secondChannel.id), sections.map { it.channelId })
    }
}
