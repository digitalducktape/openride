@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.digitalducktape.openride.ui.history

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.digitalducktape.openride.core.data.OpenRideDatabase
import dev.digitalducktape.openride.core.data.Profile
import dev.digitalducktape.openride.core.data.ProfileRepository
import dev.digitalducktape.openride.core.data.Ride
import dev.digitalducktape.openride.core.data.RideRepository
import dev.digitalducktape.openride.core.profile.ActiveProfileHolder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryViewModelTest {

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

    private fun ride(profileId: Long, startEpochMs: Long) = Ride(
        profileId = profileId,
        startEpochMs = startEpochMs,
        durationSec = 600,
        avgCadence = 80,
        maxCadence = 95,
        avgPower = 150,
        maxPower = 220,
        avgResistance = 40,
        outputKj = 90.0,
        calories = 90,
    )

    @Test
    fun `rows is empty with no active profile`() = runTest {
        val viewModel = HistoryViewModel(activeProfileHolder, rideRepository)

        val rows = viewModel.rows.first()

        assertTrue(rows.isEmpty())
    }

    @Test
    fun `rows reflects only the active profile's rides, newest first`() = runTest {
        val profileA = profileRepository.createProfile(
            Profile(name = "A", avatarEmoji = "🚴", avatarColor = 0xFF00AAFF.toInt(), weightKg = null, ftp = null),
        )
        val profileB = profileRepository.createProfile(
            Profile(name = "B", avatarEmoji = "🔥", avatarColor = 0xFF00AAFF.toInt(), weightKg = null, ftp = null),
        )
        rideRepository.saveRide(ride(profileA, startEpochMs = 1_000L), emptyList())
        rideRepository.saveRide(ride(profileA, startEpochMs = 3_000L), emptyList())
        rideRepository.saveRide(ride(profileB, startEpochMs = 2_000L), emptyList())
        activeProfileHolder.setActiveProfile(profileA)

        val viewModel = HistoryViewModel(activeProfileHolder, rideRepository)
        val rows = viewModel.rows.first { it.isNotEmpty() }

        assertEquals(2, rows.size)
        // Newest first: the ride at 3_000L should precede the one at 1_000L.
        val ridesNewestFirst = rideRepository.observeHistory(profileA).first()
        assertEquals(ridesNewestFirst.map { it.id }, rows.map { it.rideId })
    }

    @Test
    fun `rows updates when the active profile switches`() = runTest {
        val profileA = profileRepository.createProfile(
            Profile(name = "A", avatarEmoji = "🚴", avatarColor = 0xFF00AAFF.toInt(), weightKg = null, ftp = null),
        )
        val profileB = profileRepository.createProfile(
            Profile(name = "B", avatarEmoji = "🔥", avatarColor = 0xFF00AAFF.toInt(), weightKg = null, ftp = null),
        )
        rideRepository.saveRide(ride(profileB, startEpochMs = 1_000L), emptyList())
        activeProfileHolder.setActiveProfile(profileA)

        val viewModel = HistoryViewModel(activeProfileHolder, rideRepository)
        assertTrue(viewModel.rows.first().isEmpty())

        activeProfileHolder.setActiveProfile(profileB)

        val rows = viewModel.rows.first { it.isNotEmpty() }
        assertEquals(1, rows.size)
    }
}
