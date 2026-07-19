package dev.digitalducktape.openride.core.sensor

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [PelotonBikeDataSource] can't be meaningfully tested without the physical bike (see its
 * class doc / issue #3) — these tests only cover what's verifiable in Robolectric: that it
 * never crashes when the real Peloton service isn't present (true of every dev/CI
 * environment) and degrades to [ConnectionState.Unavailable] rather than fabricating a
 * connected/live state.
 */
@RunWith(AndroidJUnit4::class)
class PelotonBikeDataSourceTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `starts in Unavailable state before start() is called`() {
        val dataSource = PelotonBikeDataSource(context)

        assertEquals(ConnectionState.Unavailable, dataSource.connectionState.value)
        assertEquals(BikeMetrics.ZERO, dataSource.metrics.value)
    }

    @Test
    fun `start does not throw when the Peloton service package is absent`() {
        val dataSource = PelotonBikeDataSource(context)

        dataSource.start()

        // No real Peloton service exists in this (or any non-bike) environment, so this
        // must never resolve to Connected — Unavailable is the only honest state here.
        assertEquals(ConnectionState.Unavailable, dataSource.connectionState.value)
    }

    @Test
    fun `stop is a no-op when start was never called`() {
        val dataSource = PelotonBikeDataSource(context)

        dataSource.stop()

        assertEquals(ConnectionState.Unavailable, dataSource.connectionState.value)
    }

    @Test
    fun `stop after start does not throw`() {
        val dataSource = PelotonBikeDataSource(context)

        dataSource.start()
        dataSource.stop()

        assertEquals(ConnectionState.Unavailable, dataSource.connectionState.value)
    }
}
