package dev.digitalducktape.openride.core.sensor

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device verification of the real Gen 2 sensor binding (T3/#3).
 *
 * Runs on whatever device is connected. It is deliberately portable:
 *
 *  - On any device WITHOUT the Peloton affernet service (emulator, CI, a phone), it asserts
 *    [PelotonBikeDataSource] binds-or-degrades cleanly and never falsely reports Connected.
 *  - On the BIKE tablet (affernet present), it exercises the whole pipeline WITHOUT a rider by
 *    turning on the service's own fake-data mode: bind -> registerCallback -> setFakeDataMode
 *    -> receive onSensorDataChange frames -> decode to BikeMetrics. It asserts the source
 *    reaches [ConnectionState.Connected] and delivers frames, which proves the reconstructed
 *    AIDL transaction codes and BikeData wire layout are correct end-to-end.
 *
 * Live-cadence values still require a person physically pedaling; that final check is manual.
 */
@RunWith(AndroidJUnit4::class)
class SensorBindingInstrumentedTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun bindDegradesCleanlyWhenServiceAbsent() {
        if (affernetInstalled()) {
            Log.i(TAG, "affernet present — skipping absent-service assertion (see fakeDataStreams test)")
            return
        }
        val source = PelotonBikeDataSource(context)
        assertEquals(ConnectionState.Unavailable, source.connectionState.value)
        source.start()
        Thread.sleep(2_000)
        assertEquals(
            "must never fabricate Connected without the service present",
            ConnectionState.Unavailable,
            source.connectionState.value,
        )
        source.stop()
    }

    @Test
    fun fakeDataStreamsFramesOnBike() {
        if (!affernetInstalled()) {
            Log.i(TAG, "affernet NOT present — this device is not a Peloton bike; skipping live test")
            return
        }

        val source = PelotonBikeDataSource(context)
        source.start()

        // Wait for the bind to land: setFakeDataModeForVerification returns true only once the
        // IV1Interface binder is connected.
        val fakeEnabled = waitFor(BIND_TIMEOUT_MS) { source.setFakeDataModeForVerification(true) }
        assertTrue("service never bound / setFakeDataMode not accepted within ${BIND_TIMEOUT_MS}ms", fakeEnabled)
        Log.i(TAG, "fake-data mode enabled; awaiting frames")

        // A frame arriving flips the source to Connected (only real frames do this).
        val connected = waitFor(FRAME_TIMEOUT_MS) {
            source.connectionState.value == ConnectionState.Connected
        }
        assertTrue("no sensor frame arrived within ${FRAME_TIMEOUT_MS}ms of enabling fake data", connected)

        val metrics = source.metrics.value
        Log.i(TAG, "decoded frame: $metrics")
        // Fake-data frames are well-formed BikeData; the decode must land in sane ranges.
        assertTrue("resistance out of range: ${metrics.resistancePercent}", metrics.resistancePercent in 0..100)
        assertTrue("cadence negative: ${metrics.cadenceRpm}", metrics.cadenceRpm >= 0)
        assertTrue("power negative: ${metrics.powerWatts}", metrics.powerWatts >= 0)

        source.setFakeDataModeForVerification(false)
        source.stop()
    }

    private fun affernetInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(AFFERNET_PKG, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private inline fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(POLL_MS)
        }
        return condition()
    }

    private companion object {
        const val TAG = "SensorBindingITest"
        const val AFFERNET_PKG = "com.onepeloton.affernetservice"
        const val BIND_TIMEOUT_MS = 10_000L
        const val FRAME_TIMEOUT_MS = 10_000L
        const val POLL_MS = 200L
    }
}
