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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RideSummaryViewModelTest {

    private lateinit var db: OpenRideDatabase
    private lateinit var rideRepository: RideRepository
    private var profileId: Long = 0L

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, OpenRideDatabase::class.java).build()
        rideRepository = RideRepository(db, db.rideDao())
        profileId = ProfileRepository(db.profileDao()).createProfile(
            Profile(name = "Ed", avatarEmoji = "🚴", avatarColor = 0xFF00AAFF.toInt(), weightKg = null, ftp = null),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `load fetches the ride by id`() = runTest {
        val manager = RideSessionManager(FakeBikeDataSource(), rideRepository, backgroundScope) { 0L }
        manager.start(profileId)
        advanceTimeBy(2_000)
        runCurrent()
        val ride = manager.stop()
        requireNotNull(ride)

        val viewModel = RideSummaryViewModel(rideRepository, manager, ride.id)
        viewModel.load()

        assertNotNull(viewModel.ride.value)
        assertEquals(ride.id, viewModel.ride.value?.id)
    }

    @Test
    fun `dismiss resets the session when it was Finished`() = runTest {
        val manager = RideSessionManager(FakeBikeDataSource(), rideRepository, backgroundScope) { 0L }
        manager.start(profileId)
        advanceTimeBy(1_000)
        runCurrent()
        val ride = manager.stop()
        requireNotNull(ride)

        val viewModel = RideSummaryViewModel(rideRepository, manager, ride.id)
        viewModel.dismiss()

        assertEquals(RideSessionState.Idle, manager.state.value)
    }

    @Test
    fun `dismiss is a no-op when the session isn't Finished (history detail case)`() = runTest {
        val manager = RideSessionManager(FakeBikeDataSource(), rideRepository, backgroundScope) { 0L }
        manager.start(profileId)
        advanceTimeBy(1_000)
        runCurrent()
        val ride = manager.stop()
        requireNotNull(ride)
        manager.reset() // back to Idle, simulating an unrelated older ride being viewed later

        val viewModel = RideSummaryViewModel(rideRepository, manager, ride.id)
        viewModel.dismiss()

        assertEquals(RideSessionState.Idle, manager.state.value)
    }
}
