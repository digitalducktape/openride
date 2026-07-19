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
}
