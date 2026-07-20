package dev.digitalducktape.openride.core.content

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.digitalducktape.openride.core.data.OpenRideDatabase
import kotlinx.coroutines.flow.first
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
class ContentSourceRepositoryTest {

    private lateinit var db: OpenRideDatabase
    private lateinit var repository: ContentSourceRepository

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

    @Test
    fun `seedIfEmpty inserts the built-in catalog in order`() = runTest {
        repository.seedIfEmpty()

        val sources = repository.observeAll().first()
        assertEquals(ChannelConfig.ALL.size, sources.size)
        assertEquals(ChannelConfig.ALL.map { it.id }, sources.map { it.youtubeId })
        assertTrue(sources.all { it.builtIn })
        assertTrue(sources.none { it.hidden })
        assertEquals(List(sources.size) { it }, sources.map { it.position })
    }

    @Test
    fun `seedIfEmpty is idempotent`() = runTest {
        repository.seedIfEmpty()
        repository.seedIfEmpty()

        assertEquals(ChannelConfig.ALL.size, repository.observeAll().first().size)
    }

    @Test
    fun `adding a custom source appends it after the built-ins`() = runTest {
        repository.seedIfEmpty()

        val id = repository.add(
            ResolvedSource(ContentSourceType.PLAYLIST, "PLcustom0001", "My Climbs"),
            ContentCategory.Workout,
        )

        val added = repository.getById(id)!!
        assertEquals("PLcustom0001", added.youtubeId)
        assertEquals("My Climbs", added.displayName)
        assertEquals(ContentSourceType.PLAYLIST, added.sourceType)
        assertEquals(ContentCategory.Workout, added.category)
        assertFalse(added.builtIn)
        assertEquals(ChannelConfig.ALL.size, added.position)
    }

    @Test
    fun `hidden sources are excluded from the visible list but kept in the full list`() = runTest {
        repository.seedIfEmpty()
        val first = repository.observeAll().first().first()

        repository.setHidden(first.id, true)

        assertFalse(repository.observeVisible().first().any { it.id == first.id })
        assertTrue(repository.observeAll().first().any { it.id == first.id && it.hidden })
        assertEquals(ChannelConfig.ALL.size - 1, repository.visibleOnce().size)
    }

    @Test
    fun `unhiding restores a source to the visible list`() = runTest {
        repository.seedIfEmpty()
        val first = repository.observeAll().first().first()
        repository.setHidden(first.id, true)

        repository.setHidden(first.id, false)

        assertTrue(repository.observeVisible().first().any { it.id == first.id })
    }

    @Test
    fun `deleteCustom removes a user-added source`() = runTest {
        val id = repository.add(
            ResolvedSource(ContentSourceType.CHANNEL, "UCcustom00001", "Custom"),
            ContentCategory.Scenic,
        )

        repository.deleteCustom(id)

        assertNull(repository.getById(id))
    }

    @Test
    fun `deleteCustom refuses to delete a built-in source`() = runTest {
        repository.seedIfEmpty()
        val builtIn = repository.observeAll().first().first()

        repository.deleteCustom(builtIn.id)

        assertTrue(repository.getById(builtIn.id) != null)
    }

    @Test
    fun `adding a source that already exists does not duplicate it`() = runTest {
        repository.seedIfEmpty()
        val existing = ChannelConfig.ALL.first()

        repository.add(
            ResolvedSource(ContentSourceType.CHANNEL, existing.id, existing.displayName),
            ContentCategory.Scenic,
        )

        assertEquals(
            1,
            repository.observeAll().first().count { it.youtubeId == existing.id },
        )
    }
}
