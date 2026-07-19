@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.digitalducktape.openride.ui.home

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.digitalducktape.openride.core.data.OpenRideDatabase
import dev.digitalducktape.openride.core.data.Profile
import dev.digitalducktape.openride.core.data.ProfileRepository
import dev.digitalducktape.openride.core.data.RideRepository
import dev.digitalducktape.openride.core.profile.ActiveProfileHolder
import dev.digitalducktape.openride.core.ride.FakeBikeDataSource
import dev.digitalducktape.openride.core.ride.RideSessionManager
import dev.digitalducktape.openride.core.ride.RideSessionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeViewModelTest {

    private lateinit var db: OpenRideDatabase
    private lateinit var profileRepository: ProfileRepository
    private lateinit var rideRepository: RideRepository
    private lateinit var activeProfileHolder: ActiveProfileHolder

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, OpenRideDatabase::class.java).build()
        profileRepository = ProfileRepository(db.profileDao())
        rideRepository = RideRepository(db, db.rideDao())
        activeProfileHolder = ActiveProfileHolder(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `startQuickRide returns false and starts nothing when no profile is active`() = runTest {
        val manager = RideSessionManager(FakeBikeDataSource(), rideRepository, backgroundScope)
        val viewModel = HomeViewModel(activeProfileHolder, profileRepository, manager)

        val started = viewModel.startQuickRide()

        assertTrue(!started)
        assertEquals(RideSessionState.Idle, manager.state.value)
    }

    @Test
    fun `startQuickRide starts the session for the active profile`() = runTest {
        val profileId = profileRepository.createProfile(
            Profile(name = "Ed", avatarEmoji = "🚴", avatarColor = 0xFF00AAFF.toInt(), weightKg = null, ftp = null),
        )
        activeProfileHolder.setActiveProfile(profileId)
        val manager = RideSessionManager(FakeBikeDataSource(), rideRepository, backgroundScope)
        val viewModel = HomeViewModel(activeProfileHolder, profileRepository, manager)

        val started = viewModel.startQuickRide()

        assertTrue(started)
        assertEquals(RideSessionState.Active, manager.state.value)
    }

    @Test
    fun `activeProfile is null until a profile is selected`() = runTest {
        val manager = RideSessionManager(FakeBikeDataSource(), rideRepository, backgroundScope)
        val viewModel = HomeViewModel(activeProfileHolder, profileRepository, manager)

        val first = viewModel.activeProfile.first()

        assertNull(first)
    }

    @Test
    fun `activeProfile resolves the selected profile's row`() = runTest {
        val profileId = profileRepository.createProfile(
            Profile(name = "Ed", avatarEmoji = "🚴", avatarColor = 0xFF00AAFF.toInt(), weightKg = null, ftp = null),
        )
        activeProfileHolder.setActiveProfile(profileId)
        val manager = RideSessionManager(FakeBikeDataSource(), rideRepository, backgroundScope)
        val viewModel = HomeViewModel(activeProfileHolder, profileRepository, manager)

        val active = viewModel.activeProfile.first { it != null }

        assertEquals("Ed", active?.name)
    }
}
