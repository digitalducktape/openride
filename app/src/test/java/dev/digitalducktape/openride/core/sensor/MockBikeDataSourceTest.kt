@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.digitalducktape.openride.core.sensor

import app.cash.turbine.test
import kotlin.random.Random
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockBikeDataSourceTest {

    @Test
    fun `initial metrics are zero before the first tick`() = runTest {
        val source = MockBikeDataSource(scope = backgroundScope, random = Random(1))
        assertEquals(BikeMetrics.ZERO, source.metrics.value)
    }

    @Test
    fun `initial connection state is Connected`() = runTest {
        val source = MockBikeDataSource(scope = backgroundScope, random = Random(1))
        assertEquals(ConnectionState.Connected, source.connectionState.value)
    }

    @Test
    fun `emits one metrics update per second`() = runTest {
        val source = MockBikeDataSource(scope = backgroundScope, random = Random(1))

        source.metrics.test {
            assertEquals(BikeMetrics.ZERO, awaitItem())

            advanceTimeBy(1_000)
            val first = awaitItem()
            assertTrue(first.cadenceRpm in 55..85)

            advanceTimeBy(1_000)
            val second = awaitItem()
            assertTrue(second.cadenceRpm in 55..85)
        }
    }

    @Test
    fun `warmup phase keeps cadence and resistance within plausible ramp-up bounds`() = runTest {
        val source = MockBikeDataSource(scope = backgroundScope, random = Random(42))

        advanceTimeBy(10_000)
        runCurrent()
        val metrics = source.metrics.value

        assertTrue("cadence=${metrics.cadenceRpm}", metrics.cadenceRpm in 55..85)
        assertTrue("resistance=${metrics.resistancePercent}", metrics.resistancePercent in 20..35)
        assertTrue(metrics.powerWatts > 0)
    }

    @Test
    fun `interval phase reaches high-effort cadence and resistance`() = runTest {
        val source = MockBikeDataSource(scope = backgroundScope, random = Random(42))

        // 200s in: past the 180s warmup, into the first high-effort interval segment.
        advanceTimeBy(200_000)
        runCurrent()
        val metrics = source.metrics.value

        assertTrue("cadence=${metrics.cadenceRpm}", metrics.cadenceRpm in 85..110)
        assertTrue("resistance=${metrics.resistancePercent}", metrics.resistancePercent in 40..60)
    }

    @Test
    fun `interval phase drops to recovery cadence and resistance in the recovery segment`() = runTest {
        val source = MockBikeDataSource(scope = backgroundScope, random = Random(42))

        // 250s in: past the 60s high-effort segment (180-240s), into recovery (240-300s).
        advanceTimeBy(250_000)
        runCurrent()
        val metrics = source.metrics.value

        assertTrue("cadence=${metrics.cadenceRpm}", metrics.cadenceRpm in 60..80)
        assertTrue("resistance=${metrics.resistancePercent}", metrics.resistancePercent in 25..40)
    }

    @Test
    fun `cooldown phase settles to a low steady pace`() = runTest {
        val source = MockBikeDataSource(scope = backgroundScope, random = Random(42))

        // Warmup (180s) + intervals (1500s) + well past the 180s cooldown taper.
        advanceTimeBy(1_680_000 + 300_000)
        runCurrent()
        val metrics = source.metrics.value

        assertTrue("cadence=${metrics.cadenceRpm}", metrics.cadenceRpm in 55..70)
        assertTrue("resistance=${metrics.resistancePercent}", metrics.resistancePercent in 20..32)
    }

    @Test
    fun `all readings stay within documented plausible bounds over a long ride`() = runTest {
        val source = MockBikeDataSource(scope = backgroundScope, random = Random(7))

        repeat(2_000) {
            advanceTimeBy(1_000)
            runCurrent()
            val metrics = source.metrics.value
            assertTrue("cadence=${metrics.cadenceRpm}", metrics.cadenceRpm in 60..110)
            assertTrue("resistance=${metrics.resistancePercent}", metrics.resistancePercent in 25..60)
        }
    }

    @Test
    fun `simulateDropout flips connection state and reverts after the given duration`() = runTest {
        val source = MockBikeDataSource(scope = backgroundScope, random = Random(1))

        source.connectionState.test {
            assertEquals(ConnectionState.Connected, awaitItem())

            source.simulateDropout(5)
            assertEquals(ConnectionState.Disconnected, awaitItem())

            advanceTimeBy(5_000)
            assertEquals(ConnectionState.Connected, awaitItem())
        }
    }

    @Test
    fun `simulateDropout does not affect metrics emission`() = runTest {
        val source = MockBikeDataSource(scope = backgroundScope, random = Random(1))

        source.simulateDropout(3)
        advanceTimeBy(3_000)
        runCurrent()

        // Metrics keep ticking regardless of connection state; UI is expected to gate on
        // connectionState, not on metrics going stale/zero (PRD P0-9).
        assertTrue(source.metrics.value.cadenceRpm > 0)
    }
}
