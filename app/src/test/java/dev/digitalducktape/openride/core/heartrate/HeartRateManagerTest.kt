@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.digitalducktape.openride.core.heartrate

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.digitalducktape.openride.core.data.OpenRideDatabase
import dev.digitalducktape.openride.core.data.Profile
import dev.digitalducktape.openride.core.data.ProfileRepository
import dev.digitalducktape.openride.core.profile.ActiveProfileHolder
import dev.digitalducktape.openride.core.sensor.ConnectionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeartRateManagerTest {

    private lateinit var db: OpenRideDatabase
    private lateinit var profileRepository: ProfileRepository
    private lateinit var activeProfileHolder: ActiveProfileHolder

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, OpenRideDatabase::class.java).build()
        profileRepository = ProfileRepository(db.profileDao())
        activeProfileHolder = ActiveProfileHolder(context)
        FakeManagedHeartRateDataSource.created.clear()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `stays Unavailable with no active profile`() = runTest {
        val manager = HeartRateManager(
            activeProfileHolder,
            profileRepository,
            FakeManagedHeartRateDataSource.factory(),
            backgroundScope,
        )
        runCurrent()

        assertEquals(ConnectionState.Unavailable, manager.connectionState.value)
        assertNull(manager.bpm.value)
        assertNull(manager.connectedAddress.value)
    }

    @Test
    fun `stays Unavailable when the active profile has no paired strap`() = runTest {
        val profileId = profileRepository.createProfile(
            Profile(name = "Ed", avatarEmoji = "🚴", avatarColor = 0xFF00AAFF.toInt(), weightKg = null, ftp = null),
        )
        activeProfileHolder.setActiveProfile(profileId)
        val manager = HeartRateManager(
            activeProfileHolder,
            profileRepository,
            FakeManagedHeartRateDataSource.factory(),
            backgroundScope,
        )
        runCurrent()

        assertEquals(ConnectionState.Unavailable, manager.connectionState.value)
        assertNull(manager.connectedAddress.value)
    }

    @Test
    fun `connects to and forwards bpm from the active profile's paired strap`() = runTest {
        val profileId = profileRepository.createProfile(
            Profile(
                name = "Ed", avatarEmoji = "🚴", avatarColor = 0xFF00AAFF.toInt(), weightKg = null, ftp = null,
                pairedHrDeviceAddress = "AA:BB:CC:DD:EE:FF",
            ),
        )
        activeProfileHolder.setActiveProfile(profileId)

        val manager = HeartRateManager(
            activeProfileHolder,
            profileRepository,
            FakeManagedHeartRateDataSource.factory(),
            backgroundScope,
        )

        // A genuine suspend await (not runCurrent()) because the paired address comes from a
        // Room @Query Flow (profileRepository.observeProfiles()), whose first real emission
        // (containing this test's freshly-created profile) isn't guaranteed to land within a
        // single runCurrent() window — see HeartRateManager's onStart-seeding doc comment.
        val connectedAddress = manager.connectedAddress.first { it != null }
        assertEquals("AA:BB:CC:DD:EE:FF", connectedAddress)

        val fake = FakeManagedHeartRateDataSource.created.getValue("AA:BB:CC:DD:EE:FF")
        assertEquals(1, fake.startCallCount)

        fake.emitBpm(142)
        runCurrent()

        assertEquals(142, manager.bpm.value)
        assertEquals(ConnectionState.Connected, manager.connectionState.value)
    }

    @Test
    fun `switching to a profile with a different paired strap stops the old source and connects the new one`() = runTest {
        val profileA = profileRepository.createProfile(
            Profile(
                name = "A", avatarEmoji = "🚴", avatarColor = 0xFF00AAFF.toInt(), weightKg = null, ftp = null,
                pairedHrDeviceAddress = "AA:AA:AA:AA:AA:AA",
            ),
        )
        val profileB = profileRepository.createProfile(
            Profile(
                name = "B", avatarEmoji = "🔥", avatarColor = 0xFFAA0000.toInt(), weightKg = null, ftp = null,
                pairedHrDeviceAddress = "BB:BB:BB:BB:BB:BB",
            ),
        )
        activeProfileHolder.setActiveProfile(profileA)
        val manager = HeartRateManager(
            activeProfileHolder,
            profileRepository,
            FakeManagedHeartRateDataSource.factory(),
            backgroundScope,
        )
        manager.connectedAddress.first { it == "AA:AA:AA:AA:AA:AA" }
        val fakeA = FakeManagedHeartRateDataSource.created.getValue("AA:AA:AA:AA:AA:AA")
        fakeA.emitBpm(100)
        runCurrent()
        assertEquals(100, manager.bpm.value)

        activeProfileHolder.setActiveProfile(profileB)
        val newAddress = manager.connectedAddress.first { it == "BB:BB:BB:BB:BB:BB" }

        assertEquals(1, fakeA.stopCallCount)
        assertEquals("BB:BB:BB:BB:BB:BB", newAddress)
        // Stale bpm from the old strap must not leak into the new rider's session.
        assertNull(manager.bpm.value)

        val fakeB = FakeManagedHeartRateDataSource.created.getValue("BB:BB:BB:BB:BB:BB")
        fakeB.emitBpm(160)
        runCurrent()
        assertEquals(160, manager.bpm.value)
    }

    @Test
    fun `switching to a profile with no paired strap resets to Unavailable`() = runTest {
        val pairedProfile = profileRepository.createProfile(
            Profile(
                name = "A", avatarEmoji = "🚴", avatarColor = 0xFF00AAFF.toInt(), weightKg = null, ftp = null,
                pairedHrDeviceAddress = "AA:AA:AA:AA:AA:AA",
            ),
        )
        val unpairedProfile = profileRepository.createProfile(
            Profile(name = "B", avatarEmoji = "🔥", avatarColor = 0xFFAA0000.toInt(), weightKg = null, ftp = null),
        )
        activeProfileHolder.setActiveProfile(pairedProfile)
        val manager = HeartRateManager(
            activeProfileHolder,
            profileRepository,
            FakeManagedHeartRateDataSource.factory(),
            backgroundScope,
        )
        manager.connectedAddress.first { it == "AA:AA:AA:AA:AA:AA" }
        FakeManagedHeartRateDataSource.created.getValue("AA:AA:AA:AA:AA:AA").emitBpm(120)
        runCurrent()

        activeProfileHolder.setActiveProfile(unpairedProfile)
        manager.connectedAddress.first { it == null }

        assertEquals(ConnectionState.Unavailable, manager.connectionState.value)
        assertNull(manager.bpm.value)
    }

    @Test
    fun `stop disconnects the current source`() = runTest {
        val profileId = profileRepository.createProfile(
            Profile(
                name = "Ed", avatarEmoji = "🚴", avatarColor = 0xFF00AAFF.toInt(), weightKg = null, ftp = null,
                pairedHrDeviceAddress = "AA:BB:CC:DD:EE:FF",
            ),
        )
        activeProfileHolder.setActiveProfile(profileId)
        val manager = HeartRateManager(
            activeProfileHolder,
            profileRepository,
            FakeManagedHeartRateDataSource.factory(),
            backgroundScope,
        )
        manager.connectedAddress.first { it == "AA:BB:CC:DD:EE:FF" }
        val fake = FakeManagedHeartRateDataSource.created.getValue("AA:BB:CC:DD:EE:FF")

        manager.stop()

        assertEquals(1, fake.stopCallCount)
        assertNull(manager.connectedAddress.value)
    }
}
