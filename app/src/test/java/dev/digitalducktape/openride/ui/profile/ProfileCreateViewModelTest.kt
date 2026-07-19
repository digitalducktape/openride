package dev.digitalducktape.openride.ui.profile

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.digitalducktape.openride.core.data.OpenRideDatabase
import dev.digitalducktape.openride.core.data.ProfileRepository
import dev.digitalducktape.openride.core.profile.ActiveProfileHolder
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileCreateViewModelTest {

    private lateinit var db: OpenRideDatabase
    private lateinit var profileRepository: ProfileRepository
    private lateinit var activeProfileHolder: ActiveProfileHolder
    private lateinit var viewModel: ProfileCreateViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, OpenRideDatabase::class.java).build()
        profileRepository = ProfileRepository(db.profileDao())
        activeProfileHolder = ActiveProfileHolder(context)
        viewModel = ProfileCreateViewModel(profileRepository, activeProfileHolder)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `blank name is rejected with an error and nothing is saved`() = runTest {
        viewModel.onNameChange("   ")

        val result = viewModel.save()

        assertTrue(!result)
        assertNotNull(viewModel.uiState.value.nameError)
        assertTrue(!viewModel.uiState.value.saved)
        assertNull(activeProfileHolder.activeProfileId.value)
    }

    @Test
    fun `empty name is rejected with an error`() = runTest {
        val result = viewModel.save()

        assertTrue(!result)
        assertNotNull(viewModel.uiState.value.nameError)
    }

    @Test
    fun `changing the name clears a previous error`() = runTest {
        viewModel.save() // triggers the error
        assertNotNull(viewModel.uiState.value.nameError)

        viewModel.onNameChange("Ed")

        assertNull(viewModel.uiState.value.nameError)
    }

    @Test
    fun `valid name is trimmed, saved, and set active`() = runTest {
        viewModel.onNameChange("  Ed  ")

        val result = viewModel.save()

        assertTrue(result)
        assertTrue(viewModel.uiState.value.saved)
        val activeId = activeProfileHolder.activeProfileId.value
        assertNotNull(activeId)
        val saved = profileRepository.getProfile(activeId!!)
        assertNotNull(saved)
        assertEquals("Ed", saved!!.name)
    }

    @Test
    fun `weight and FTP are optional and default to null when left blank`() = runTest {
        viewModel.onNameChange("Ed")

        viewModel.save()

        val saved = profileRepository.getProfile(activeProfileHolder.activeProfileId.value!!)
        assertNull(saved!!.weightKg)
        assertNull(saved.ftp)
    }

    @Test
    fun `a non-numeric weight is rejected with an error and nothing is saved`() = runTest {
        viewModel.onNameChange("Ed")
        viewModel.onWeightChange("not a number")

        val result = viewModel.save()

        assertTrue(!result)
        assertNotNull(viewModel.uiState.value.weightError)
        assertNull(activeProfileHolder.activeProfileId.value)
    }

    @Test
    fun `a zero or negative weight is rejected with an error`() = runTest {
        viewModel.onNameChange("Ed")
        viewModel.onWeightChange("0")

        val result = viewModel.save()

        assertTrue(!result)
        assertNotNull(viewModel.uiState.value.weightError)
    }

    @Test
    fun `a non-numeric FTP is rejected with an error and nothing is saved`() = runTest {
        viewModel.onNameChange("Ed")
        viewModel.onFtpChange("lots")

        val result = viewModel.save()

        assertTrue(!result)
        assertNotNull(viewModel.uiState.value.ftpError)
        assertNull(activeProfileHolder.activeProfileId.value)
    }

    @Test
    fun `valid weight and FTP are parsed and saved`() = runTest {
        viewModel.onNameChange("Ed")
        viewModel.onWeightChange("80.5")
        viewModel.onFtpChange("220")

        val result = viewModel.save()

        assertTrue(result)
        val saved = profileRepository.getProfile(activeProfileHolder.activeProfileId.value!!)
        assertEquals(80.5, saved!!.weightKg!!, 0.0001)
        assertEquals(220, saved.ftp)
    }

    @Test
    fun `changing the weight clears a previous weight error`() = runTest {
        viewModel.onNameChange("Ed")
        viewModel.onWeightChange("bad")
        viewModel.save()
        assertNotNull(viewModel.uiState.value.weightError)

        viewModel.onWeightChange("70")

        assertNull(viewModel.uiState.value.weightError)
    }

    @Test
    fun `selecting an avatar color and emoji persists them on the saved profile`() = runTest {
        viewModel.onNameChange("Ed")
        viewModel.onAvatarColorChange(AvatarOptions.colors[2])
        viewModel.onAvatarEmojiChange(AvatarOptions.emojis[3])

        viewModel.save()

        val saved = profileRepository.getProfile(activeProfileHolder.activeProfileId.value!!)
        assertEquals(AvatarOptions.colors[2], saved!!.avatarColor)
        assertEquals(AvatarOptions.emojis[3], saved.avatarEmoji)
    }
}
