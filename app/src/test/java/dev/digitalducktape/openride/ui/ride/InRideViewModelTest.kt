@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.digitalducktape.openride.ui.ride

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.digitalducktape.openride.core.data.OpenRideDatabase
import dev.digitalducktape.openride.core.data.Profile
import dev.digitalducktape.openride.core.data.ProfileRepository
import dev.digitalducktape.openride.core.data.RideRepository
import dev.digitalducktape.openride.core.ride.FakeBikeDataSource
import dev.digitalducktape.openride.core.ride.RideSessionManager
import dev.digitalducktape.openride.core.ride.RideSessionState
import dev.digitalducktape.openride.core.sensor.ConnectionState
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InRideViewModelTest {

    private lateinit var db: OpenRideDatabase
    private lateinit var rideRepository: RideRepository
    private lateinit var fakeBikeDataSource: FakeBikeDataSource
    private var profileId: Long = 0L

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, OpenRideDatabase::class.java).build()
        rideRepository = RideRepository(db, db.rideDao())
        profileId = ProfileRepository(db.profileDao()).createProfile(
            Profile(name = "Ed", avatarEmoji = "🚴", avatarColor = 0xFF00AAFF.toInt(), weightKg = null, ftp = null),
        )
        fakeBikeDataSource = FakeBikeDataSource()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `sensorsAvailable is false while the connection is down`() = runTest {
        val manager = RideSessionManager(fakeBikeDataSource, rideRepository, backgroundScope)
        val viewModel = InRideViewModel(manager, fakeBikeDataSource)
        val values = mutableListOf<InRideUiState>()
        backgroundScope.launch { viewModel.uiState.collect { values.add(it) } }
        runCurrent()

        fakeBikeDataSource.setConnectionState(ConnectionState.Disconnected)
        runCurrent()

        assertFalse(values.last().sensorsAvailable)
    }

    @Test
    fun `isPaused reflects the session state`() = runTest {
        val manager = RideSessionManager(fakeBikeDataSource, rideRepository, backgroundScope)
        val viewModel = InRideViewModel(manager, fakeBikeDataSource)
        val values = mutableListOf<InRideUiState>()
        backgroundScope.launch { viewModel.uiState.collect { values.add(it) } }
        runCurrent()

        manager.start(profileId)
        runCurrent()
        assertFalse(values.last().isPaused)

        manager.pause()
        runCurrent()
        assertTrue(values.last().isPaused)
    }

    @Test
    fun `liveOutputKj mirrors avgPower times elapsedSec over 1000`() = runTest {
        val manager = RideSessionManager(fakeBikeDataSource, rideRepository, backgroundScope)
        val viewModel = InRideViewModel(manager, fakeBikeDataSource)
        val values = mutableListOf<InRideUiState>()
        backgroundScope.launch { viewModel.uiState.collect { values.add(it) } }
        runCurrent()

        manager.start(profileId)
        fakeBikeDataSource.setMetrics(cadenceRpm = 90, resistancePercent = 50, powerWatts = 200)
        advanceTimeBy(3_000)
        runCurrent()

        // 3 seconds at 200W avg = 600 joules = 0.6 kJ.
        assertEquals(0.6, values.last().liveOutputKj, 0.0001)
    }

    @Test
    fun `distanceMiles accumulates while Active and freezes while Paused`() = runTest {
        val manager = RideSessionManager(fakeBikeDataSource, rideRepository, backgroundScope)
        val viewModel = InRideViewModel(manager, fakeBikeDataSource)
        val values = mutableListOf<InRideUiState>()
        backgroundScope.launch { viewModel.uiState.collect { values.add(it) } }
        runCurrent()

        manager.start(profileId)
        fakeBikeDataSource.setMetrics(cadenceRpm = 90, resistancePercent = 50, powerWatts = 200, speedMph = 36.0)
        advanceTimeBy(2_000) // 2 ticks at 36 mph = 0.01 mi/tick (36/3600) -> 0.02 mi total
        runCurrent()

        assertEquals(0.02, values.last().distanceMiles, 0.0001)

        manager.pause()
        advanceTimeBy(10_000) // time passes while paused; distance must not grow
        runCurrent()

        assertEquals(0.02, values.last().distanceMiles, 0.0001)
    }

    @Test
    fun `endRide delegates to the session manager and persists the ride`() = runTest {
        val manager = RideSessionManager(fakeBikeDataSource, rideRepository, backgroundScope) { 0L }
        val viewModel = InRideViewModel(manager, fakeBikeDataSource)
        manager.start(profileId)
        advanceTimeBy(2_000)
        runCurrent()

        val ride = viewModel.endRide()

        requireNotNull(ride)
        assertTrue(ride.id > 0)
        assertEquals(RideSessionState.Finished(ride), manager.state.value)
    }
}
