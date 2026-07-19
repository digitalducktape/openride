package dev.digitalducktape.openride.core.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PowerZoneTest {

    @Test
    fun `forPower returns null when ftp is null`() {
        assertNull(PowerZone.forPower(powerWatts = 150, ftpWatts = null))
    }

    @Test
    fun `forPower returns null when ftp is zero or negative`() {
        assertNull(PowerZone.forPower(powerWatts = 150, ftpWatts = 0))
        assertNull(PowerZone.forPower(powerWatts = 150, ftpWatts = -10))
    }

    @Test
    fun `forPower maps each standard pct-FTP boundary to the expected zone`() {
        val ftp = 200
        // <55%
        assertEquals(PowerZone.ACTIVE_RECOVERY, PowerZone.forPower(powerWatts = 100, ftpWatts = ftp))
        // 55-75%
        assertEquals(PowerZone.ENDURANCE, PowerZone.forPower(powerWatts = 120, ftpWatts = ftp))
        // 76-90%
        assertEquals(PowerZone.TEMPO, PowerZone.forPower(powerWatts = 160, ftpWatts = ftp))
        // 91-105%
        assertEquals(PowerZone.THRESHOLD, PowerZone.forPower(powerWatts = 190, ftpWatts = ftp))
        // 106-120%
        assertEquals(PowerZone.VO2_MAX, PowerZone.forPower(powerWatts = 220, ftpWatts = ftp))
        // 121-150%
        assertEquals(PowerZone.ANAEROBIC, PowerZone.forPower(powerWatts = 260, ftpWatts = ftp))
        // >150%
        assertEquals(PowerZone.NEUROMUSCULAR, PowerZone.forPower(powerWatts = 400, ftpWatts = ftp))
    }

    @Test
    fun `forPower handles the exact zone boundary edges`() {
        val ftp = 200
        // Exactly 55% -> still Active Recovery is < boundary, 55% itself rolls into Endurance
        // since the boundary is the *upper* edge of Active Recovery (fraction < 0.55).
        assertEquals(PowerZone.ENDURANCE, PowerZone.forPower(powerWatts = 110, ftpWatts = ftp)) // exactly 55%
        assertEquals(PowerZone.ACTIVE_RECOVERY, PowerZone.forPower(powerWatts = 109, ftpWatts = ftp)) // just under
    }

    @Test
    fun `zone numbers run 1 through 7 in order`() {
        assertEquals((1..7).toList(), PowerZone.entries.map { it.number })
    }
}
