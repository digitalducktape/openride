package dev.digitalducktape.openride.ui.profile

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.digitalducktape.openride.core.backup.BackupRepository
import dev.digitalducktape.openride.core.data.OpenRideDatabase
import dev.digitalducktape.openride.core.data.Profile
import dev.digitalducktape.openride.core.data.ProfileRepository
import dev.digitalducktape.openride.core.profile.ActiveProfileHolder
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
class ProfileTabViewModelTest {

    private lateinit var db: OpenRideDatabase
    private lateinit var profileRepository: ProfileRepository
    private lateinit var activeProfileHolder: ActiveProfileHolder
    private lateinit var backupRepository: BackupRepository
    private lateinit var viewModel: ProfileTabViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, OpenRideDatabase::class.java).build()
        profileRepository = ProfileRepository(db.profileDao())
        activeProfileHolder = ActiveProfileHolder(context)
        backupRepository = BackupRepository(db, db.profileDao(), db.rideDao())
        viewModel = ProfileTabViewModel(activeProfileHolder, profileRepository, backupRepository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `activeProfile resolves the currently active profile`() = runTest {
        val id = profileRepository.createProfile(
            Profile(name = "Ed", avatarEmoji = "🚴", avatarColor = 0xFF00AAFF.toInt(), weightKg = null, ftp = null),
        )
        activeProfileHolder.setActiveProfile(id)

        val active = viewModel.activeProfile.first { it != null }

        assertEquals("Ed", active?.name)
    }

    @Test
    fun `switchRider clears the active profile`() = runTest {
        val id = profileRepository.createProfile(
            Profile(name = "Ed", avatarEmoji = "🚴", avatarColor = 0xFF00AAFF.toInt(), weightKg = null, ftp = null),
        )
        activeProfileHolder.setActiveProfile(id)

        viewModel.switchRider()

        assertNull(activeProfileHolder.activeProfileId.value)
    }

    // --- Backup & restore (T15/#15) -----------------------------------------------------------

    @Test
    fun `createBackupContent produces parseable JSON containing the current profiles`() = runTest {
        profileRepository.createProfile(
            Profile(name = "Ed", avatarEmoji = "🚴", avatarColor = 0xFF00AAFF.toInt(), weightKg = null, ftp = null),
        )

        val json = viewModel.createBackupContent()
        val snapshot = backupRepository.parse(json)

        assertEquals(1, snapshot.profiles.size)
        assertEquals("Ed", snapshot.profiles.single().name)
    }

    @Test
    fun `restoreFromContent replaces the database and clears the active profile`() = runTest {
        val originalId = profileRepository.createProfile(
            Profile(name = "Original", avatarEmoji = "🚴", avatarColor = 0xFF00AAFF.toInt(), weightKg = null, ftp = null),
        )
        activeProfileHolder.setActiveProfile(originalId)

        // A backup created from a separate, throwaway DB — simulating "restore a file from
        // somewhere else" rather than round-tripping this same database.
        val otherDb = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OpenRideDatabase::class.java,
        ).build()
        val otherProfileRepo = ProfileRepository(otherDb.profileDao())
        val otherBackupRepo = BackupRepository(otherDb, otherDb.profileDao(), otherDb.rideDao())
        otherProfileRepo.createProfile(
            Profile(name = "Restored", avatarEmoji = "🔥", avatarColor = 0xFFAA0000.toInt(), weightKg = null, ftp = null),
        )
        val backupJson = otherBackupRepo.exportJson()
        otherDb.close()

        val result = viewModel.restoreFromContent(backupJson)

        assertTrue(result.isSuccess)
        assertNull(activeProfileHolder.activeProfileId.value)
        val profiles = profileRepository.observeProfiles().first()
        assertEquals(1, profiles.size)
        assertEquals("Restored", profiles.single().name)
    }

    @Test
    fun `restoreFromContent returns failure for malformed content and leaves the database untouched`() = runTest {
        val id = profileRepository.createProfile(
            Profile(name = "Ed", avatarEmoji = "🚴", avatarColor = 0xFF00AAFF.toInt(), weightKg = null, ftp = null),
        )
        activeProfileHolder.setActiveProfile(id)

        val result = viewModel.restoreFromContent("not valid json")

        assertTrue(result.isFailure)
        assertEquals(id, activeProfileHolder.activeProfileId.value)
        assertEquals(1, profileRepository.observeProfiles().first().size)
    }
}
