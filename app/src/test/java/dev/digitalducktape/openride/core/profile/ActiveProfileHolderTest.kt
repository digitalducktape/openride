package dev.digitalducktape.openride.core.profile

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActiveProfileHolderTest {

    private fun newHolder() = ActiveProfileHolder(ApplicationProvider.getApplicationContext())

    @Test
    fun `starts with no active profile when nothing persisted`() {
        val holder = newHolder()

        assertNull(holder.activeProfileId.value)
    }

    @Test
    fun `setActiveProfile updates the in-memory flow`() {
        val holder = newHolder()

        holder.setActiveProfile(42L)

        assertEquals(42L, holder.activeProfileId.value)
    }

    @Test
    fun `setActiveProfile persists across a new holder instance`() {
        newHolder().setActiveProfile(7L)

        val secondHolder = newHolder()

        assertEquals(7L, secondHolder.activeProfileId.value)
    }

    @Test
    fun `clear resets the active profile and the persisted value`() {
        val holder = newHolder()
        holder.setActiveProfile(5L)

        holder.clear()

        assertNull(holder.activeProfileId.value)
        assertNull(newHolder().activeProfileId.value)
    }
}
