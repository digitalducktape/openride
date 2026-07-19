package dev.digitalducktape.openride.core.ride

import dev.digitalducktape.openride.core.data.RideSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FtpEstimatorTest {

    private fun sample(t: Int, power: Int) = RideSample(rideId = 1L, tSec = t, cadence = 90, resistance = 50, power = power)

    @Test
    fun `returns null for a ride shorter than 20 minutes`() {
        val samples = (0 until 1199).map { sample(it, power = 200) }

        assertNull(FtpEstimator.estimateFtp(samples))
    }

    @Test
    fun `returns 95pct of a flat power ride's average at exactly 20 minutes`() {
        val samples = (0 until 1200).map { sample(it, power = 200) }

        assertEquals(190, FtpEstimator.estimateFtp(samples)) // 200 * 0.95
    }

    @Test
    fun `picks the best 20-minute window, not the whole ride's average`() {
        // 10 minutes easy at 100W, then 20 minutes hard at 300W, then 10 minutes easy again.
        val easy = (0 until 600).map { sample(it, power = 100) }
        val hard = (600 until 1800).map { sample(it, power = 300) }
        val coolDown = (1800 until 2400).map { sample(it, power = 100) }
        val samples = easy + hard + coolDown

        assertEquals((300 * 0.95).toInt(), FtpEstimator.estimateFtp(samples))
    }

    @Test
    fun `is order-independent (defensively sorts by tSec)`() {
        val samples = (0 until 1200).map { sample(it, power = 200) }.shuffled(kotlin.random.Random(42))

        assertEquals(190, FtpEstimator.estimateFtp(samples))
    }

    @Test
    fun `rounds to the nearest watt`() {
        // Best 20-min avg = 199W -> 95% = 189.05 -> rounds to 189.
        val samples = (0 until 1200).map { sample(it, power = 199) }

        assertEquals(189, FtpEstimator.estimateFtp(samples))
    }
}
