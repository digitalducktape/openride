package dev.digitalducktape.openride.ui.sources

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.digitalducktape.openride.core.content.ChannelHandleResolver
import dev.digitalducktape.openride.core.content.ContentCategory
import dev.digitalducktape.openride.core.content.ContentSourceRepository
import dev.digitalducktape.openride.core.content.ContentSourceType
import dev.digitalducktape.openride.core.content.FeedFetcher
import dev.digitalducktape.openride.core.content.HttpStatusException
import dev.digitalducktape.openride.core.data.OpenRideDatabase
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContentSourcesViewModelTest {

    private lateinit var db: OpenRideDatabase
    private lateinit var repository: ContentSourceRepository

    private val channelPage = """
        <html><head><title>Kaleigh Cohen Cycling - YouTube</title></head>
        <body><script>{"externalId":"UChY_9WJx0saa0St48lSdytQ"}</script></body></html>
    """.trimIndent()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OpenRideDatabase::class.java,
        ).build()
        repository = ContentSourceRepository(db.contentSourceDao())
    }

    @After
    fun tearDown() = db.close()

    private fun viewModel(fetcher: FeedFetcher = FeedFetcher { channelPage.byteInputStream() }) =
        ContentSourcesViewModel(repository, ChannelHandleResolver(fetcher))

    @Test
    fun `add state starts idle`() = runTest {
        assertEquals(AddSourceState.Idle, viewModel().addState.value)
    }

    @Test
    fun `resolving a handle exposes the resolved source for confirmation`() = runTest {
        val viewModel = viewModel()

        viewModel.resolve("@kaleigh")

        val resolved = viewModel.addState.value as AddSourceState.Resolved
        assertEquals("UChY_9WJx0saa0St48lSdytQ", resolved.source.youtubeId)
        assertEquals("Kaleigh Cohen Cycling", resolved.source.displayName)
        assertEquals(ContentSourceType.CHANNEL, resolved.source.sourceType)
    }

    @Test
    fun `resolving does not add anything until confirmed`() = runTest {
        val viewModel = viewModel()

        viewModel.resolve("@kaleigh")

        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun `confirmAdd saves the resolved source with the chosen category`() = runTest {
        val viewModel = viewModel()
        viewModel.resolve("@kaleigh")

        viewModel.confirmAdd(ContentCategory.Scenic)

        val saved = repository.observeAll().first().single()
        assertEquals("UChY_9WJx0saa0St48lSdytQ", saved.youtubeId)
        assertEquals(ContentCategory.Scenic, saved.category)
        assertFalse(saved.builtIn)
        assertEquals(AddSourceState.Idle, viewModel.addState.value)
    }

    @Test
    fun `confirmAdd with nothing resolved does nothing`() = runTest {
        val viewModel = viewModel()

        viewModel.confirmAdd(ContentCategory.Workout)

        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun `a failed resolution surfaces a message and saves nothing`() = runTest {
        val viewModel = viewModel(FeedFetcher { throw IOException("offline") })

        viewModel.resolve("@kaleigh")

        val failed = viewModel.addState.value as AddSourceState.Failed
        assertEquals("No connection — try again", failed.message)
        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun `a page without a channel id reports not found`() = runTest {
        val viewModel = viewModel(FeedFetcher { "<html>nothing</html>".byteInputStream() })

        viewModel.resolve("@ghost")

        val failed = viewModel.addState.value as AddSourceState.Failed
        assertEquals("Couldn't find that channel or playlist", failed.message)
    }

    @Test
    fun `a non-2xx HTTP response reports not found, not offline`() = runTest {
        // A 404 for a mistyped handle is not a connectivity failure — it must not be
        // reported as "No connection", which would send the rider retrying a lookup that
        // can never succeed. Regression coverage for that mix-up.
        val viewModel = viewModel(FeedFetcher { throw HttpStatusException(404, "HTTP 404") })

        viewModel.resolve("@typo-handle")

        val failed = viewModel.addState.value as AddSourceState.Failed
        assertEquals("Couldn't find that channel or playlist", failed.message)
    }

    @Test
    fun `cancelAdd clears a pending resolution`() = runTest {
        val viewModel = viewModel()
        viewModel.resolve("@kaleigh")

        viewModel.cancelAdd()

        assertEquals(AddSourceState.Idle, viewModel.addState.value)
    }

    @Test
    fun `hiding and unhiding a source is reflected in the list`() = runTest {
        val viewModel = viewModel()
        repository.seedIfEmpty()
        val source = viewModel.sources.first().first()

        viewModel.setHidden(source.id, true)
        assertTrue(viewModel.sources.first().single { it.id == source.id }.hidden)

        viewModel.setHidden(source.id, false)
        assertFalse(viewModel.sources.first().single { it.id == source.id }.hidden)
    }

    @Test
    fun `deleting a custom source removes it from the list`() = runTest {
        val viewModel = viewModel()
        viewModel.resolve("@kaleigh")
        viewModel.confirmAdd(ContentCategory.Workout)
        val added = viewModel.sources.first().single()

        viewModel.deleteCustom(added.id)

        assertTrue(viewModel.sources.first().isEmpty())
    }
}
