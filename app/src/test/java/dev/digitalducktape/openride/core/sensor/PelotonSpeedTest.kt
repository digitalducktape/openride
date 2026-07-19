package dev.digitalducktape.openride.core.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sanity checks on the power->speed derivation (T3/#3). The Gen 2 sensor service reports no
 * speed field, so OpenRide synthesises it from power via the PeloMon curve, matching stock
 * Peloton behaviour. These assert the curve is well-behaved, not exact bike-calibrated values.
 */
class PelotonSpeedTest {

    @Test
    fun `zero and near-zero power give zero speed`() {
        assertEquals(0.0, pelotonSpeedMphFromPower(0.0), 0.0)
        assertEquals(0.0, pelotonSpeedMphFromPower(0.05), 0.0)
    }

    @Test
    fun `speed never goes negative`() {
        var p = 0.0
        while (p <= 800.0) {
            assertTrue("speed < 0 at ${p}W", pelotonSpeedMphFromPower(p) >= 0.0)
            p += 1.0
        }
    }

    @Test
    fun `speed increases monotonically across the typical riding range`() {
        // Sample above the low-power branch (>26W) up through a hard effort.
        var prev = pelotonSpeedMphFromPower(30.0)
        var p = 35.0
        while (p <= 600.0) {
            val s = pelotonSpeedMphFromPower(p)
            assertTrue("speed should rise with power ($p W -> $s vs $prev)", s >= prev)
            prev = s
            p += 5.0
        }
    }

    @Test
    fun `moderate effort lands in a plausible mph band`() {
        // ~150 W is a typical steady endurance effort; expect a sane double-digit mph.
        val mph = pelotonSpeedMphFromPower(150.0)
        assertTrue("150W -> $mph mph out of expected band", mph in 15.0..30.0)
    }
}
