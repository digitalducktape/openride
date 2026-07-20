@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.digitalducktape.openride.ui.classes

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.digitalducktape.openride.core.content.ChannelConfig
import dev.digitalducktape.openride.core.content.ContentCache
import dev.digitalducktape.openride.core.content.ContentCategory
import dev.digitalducktape.openride.core.content.FeedFetcher
import dev.digitalducktape.openride.core.content.YouTubeContentRepository
import dev.digitalducktape.openride.core.data.OpenRideDatabase
import dev.digitalducktape.openride.core.data.Profile
import dev.digitalducktape.openride.core.data.Ride
import dev.digitalducktape.openride.core.data.ProfileRepository
import dev.digitalducktape.openride.core.data.RideRepository
import dev.digitalducktape.openride.core.profile.ActiveProfileHolder
import dev.digitalducktape.openride.core.ride.FakeBikeDataSource
import dev.digitalducktape.openride.core.ride.RideSessionManager
import dev.digitalducktape.openride.core.ride.RideSessionState
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class ClassesViewModelTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private lateinit var db: OpenRideDatabase
    private lateinit var profileRepository: ProfileRepository
    private lateinit var rideRepository: RideRepository
    private lateinit var activeProfileHolder: ActiveProfileHolder

    private val testChannel = ChannelConfig.Channel(
        id = "UCTestChannel00000000",
        displayName = "Test Cycling Channel",
        handle = "@test",
        category = ContentCategory.Scenic,
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, OpenRideDatabase::class.java).build()
        profileRepository = ProfileRepository(db.profileDao())
        rideRepository = RideRepository(db, db.rideDao())
        activeProfileHolder = ActiveProfileHolder(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun fixtureXml() =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/sample_atom_feed.xml"))

    private fun repository(fetcher: FeedFetcher = FeedFetcher { fixtureXml() }) =
        YouTubeContentRepository(
            context = context,
            channels = listOf(testChannel),
            fetcher = fetcher,
            cache = ContentCache(context),
        )

    private fun viewModel(
        scope: CoroutineScope,
        fetcher: FeedFetcher = FeedFetcher { fixtureXml() },
    ): Pair<ClassesViewModel, RideSessionManager> {
        val manager = RideSessionManager(FakeBikeDataSource(), rideRepository, scope)
        val viewModel = ClassesViewModel(repository(fetcher), manager, activeProfileHolder, rideRepository)
        return viewModel to manager
    }

    @Test
    fun `starts in Loading state before refresh is called`() = runTest {
        val (viewModel, _) = viewModel(backgroundScope)

        assertTrue(viewModel.uiState.value is ClassesUiState.Loading)
    }

    @Test
    fun `refresh loads channel sections successfully`() = runTest {
        val (viewModel, _) = viewModel(backgroundScope)

        viewModel.refresh()

        val loaded = viewModel.uiState.value as ClassesUiState.Loaded
        assertFalse(loaded.anyRefreshFailed)
        assertEquals(1, loaded.sections.size)
        assertEquals(3, loaded.sections.single().videos.size)
    }

    @Test
    fun `anyRefreshFailed is true when a channel's fetch fails`() = runTest {
        val (viewModel, _) = viewModel(backgroundScope, FeedFetcher { throw IOException("down") })

        viewModel.refresh()

        val loaded = viewModel.uiState.value as ClassesUiState.Loaded
        assertTrue(loaded.anyRefreshFailed)
        assertTrue(loaded.sections.single().videos.isEmpty())
    }

    @Test
    fun `refresh can be called again to reload`() = runTest {
        var callCount = 0
        val manager = RideSessionManager(FakeBikeDataSource(), rideRepository, backgroundScope)
        val viewModel = ClassesViewModel(
            repository(
                FeedFetcher {
                    callCount++
                    fixtureXml()
                },
            ),
            manager,
            activeProfileHolder,
            rideRepository,
        )

        viewModel.refresh()
        viewModel.refresh()

        assertEquals(2, callCount)
    }

    @Test
    fun `startRideForVideo returns false and starts nothing when no profile is active`() = runTest {
        val (viewModel, manager) = viewModel(backgroundScope)

        val started = viewModel.startRideForVideo("videoAbc123")

        assertFalse(started)
        assertEquals(RideSessionState.Idle, manager.state.value)
    }

    @Test
    fun `startRideForVideo starts the session for the active profile`() = runTest {
        val profileId = profileRepository.createProfile(
            Profile(name = "Ed", avatarEmoji = "🚴", avatarColor = 0xFF00AAFF.toInt(), weightKg = null, ftp = null),
        )
        activeProfileHolder.setActiveProfile(profileId)
        val (viewModel, manager) = viewModel(backgroundScope)

        val started = viewModel.startRideForVideo("videoAbc123")

        assertTrue(started)
        assertEquals(RideSessionState.Active, manager.state.value)
    }

    @Test
    fun `takenVideos maps each ridden class to its most recent take`() = runTest {
        val profileId = profileRepository.createProfile(
            Profile(name = "Ed", avatarEmoji = "🚴", avatarColor = 0xFF00AAFF.toInt(), weightKg = null, ftp = null),
        )
        activeProfileHolder.setActiveProfile(profileId)
        val (viewModel, _) = viewModel(backgroundScope)

        suspend fun save(videoId: String?, startEpochMs: Long) {
            rideRepository.saveRide(
                Ride(
                    profileId = profileId,
                    startEpochMs = startEpochMs,
                    durationSec = 60,
                    avgCadence = 70,
                    maxCadence = 80,
                    avgPower = 100,
                    maxPower = 120,
                    avgResistance = 30,
                    outputKj = 6.0,
                    calories = 6,
                    videoId = videoId,
                ),
                emptyList(),
            )
        }
        save(videoId = "classA", startEpochMs = 1_000L)
        save(videoId = "classA", startEpochMs = 2_000L)
        save(videoId = "classB", startEpochMs = 3_000L)
        save(videoId = null, startEpochMs = 4_000L) // quick start, never badged

        assertEquals(
            mapOf("classA" to 2_000L, "classB" to 3_000L),
            viewModel.takenVideos.first(),
        )
    }

    @Test
    fun `takenVideos is empty when no profile is active`() = runTest {
        val (viewModel, _) = viewModel(backgroundScope)

        assertEquals(emptyMap<String, Long>(), viewModel.takenVideos.first())
    }
}
