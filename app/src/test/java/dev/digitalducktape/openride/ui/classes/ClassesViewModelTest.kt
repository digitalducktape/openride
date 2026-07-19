package dev.digitalducktape.openride.ui.classes

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.digitalducktape.openride.core.content.ChannelConfig
import dev.digitalducktape.openride.core.content.ContentCache
import dev.digitalducktape.openride.core.content.ContentCategory
import dev.digitalducktape.openride.core.content.FeedFetcher
import dev.digitalducktape.openride.core.content.YouTubeContentRepository
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClassesViewModelTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val testChannel = ChannelConfig.Channel(
        id = "UCTestChannel00000000",
        displayName = "Test Cycling Channel",
        handle = "@test",
        category = ContentCategory.Scenic,
    )

    private fun fixtureXml() =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/sample_atom_feed.xml"))

    @Test
    fun `starts in Loading state before refresh is called`() {
        val repository = YouTubeContentRepository(
            context = context,
            channels = listOf(testChannel),
            fetcher = FeedFetcher { fixtureXml() },
            cache = ContentCache(context),
        )
        val viewModel = ClassesViewModel(repository)

        assertTrue(viewModel.uiState.value is ClassesUiState.Loading)
    }

    @Test
    fun `refresh loads channel sections successfully`() = runTest {
        val repository = YouTubeContentRepository(
            context = context,
            channels = listOf(testChannel),
            fetcher = FeedFetcher { fixtureXml() },
            cache = ContentCache(context),
        )
        val viewModel = ClassesViewModel(repository)

        viewModel.refresh()

        val loaded = viewModel.uiState.value as ClassesUiState.Loaded
        assertFalse(loaded.anyRefreshFailed)
        assertEquals(1, loaded.sections.size)
        assertEquals(3, loaded.sections.single().videos.size)
    }

    @Test
    fun `anyRefreshFailed is true when a channel's fetch fails`() = runTest {
        val repository = YouTubeContentRepository(
            context = context,
            channels = listOf(testChannel),
            fetcher = FeedFetcher { throw IOException("down") },
            cache = ContentCache(context),
        )
        val viewModel = ClassesViewModel(repository)

        viewModel.refresh()

        val loaded = viewModel.uiState.value as ClassesUiState.Loaded
        assertTrue(loaded.anyRefreshFailed)
        assertTrue(loaded.sections.single().videos.isEmpty())
    }

    @Test
    fun `refresh can be called again to reload`() = runTest {
        var callCount = 0
        val repository = YouTubeContentRepository(
            context = context,
            channels = listOf(testChannel),
            fetcher = FeedFetcher {
                callCount++
                fixtureXml()
            },
            cache = ContentCache(context),
        )
        val viewModel = ClassesViewModel(repository)

        viewModel.refresh()
        viewModel.refresh()

        assertEquals(2, callCount)
    }
}
