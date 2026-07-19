package dev.digitalducktape.openride.core.sensor

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * MANUAL live-pedal capture for T3/#3. Not a pass/fail assertion — a data recorder.
 *
 * Binds the real affernet sensor source and records one decoded frame per second for
 * [DURATION_MS], to both logcat (tag [TAG]) and the app sandbox file `t3_pedal.txt`, so a
 * human can pedal / turn the resistance knob during the window and we can read back whether
 * cadence and power track effort. Skipped automatically on any device without the service.
 */
@RunWith(AndroidJUnit4::class)
class LivePedalStreamTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun recordLivePedalStream() {
        assumeTrue("not a Peloton bike — no affernet service", affernetInstalled())

        val source = PelotonBikeDataSource(context)
        source.start()

        val out = StringBuilder("t3_pedal capture — one line per second\n")
        val startedBound = waitForBind(source)
        Log.i(TAG, "bind=$startedBound — begin ${DURATION_MS / 1000}s capture; PEDAL NOW")
        out.appendLine("bind=$startedBound state=${source.connectionState.value}")

        val end = System.currentTimeMillis() + DURATION_MS
        var t = 0
        while (System.currentTimeMillis() < end) {
            val m = source.metrics.value
            val line = "t=${t}s cad=${m.cadenceRpm}rpm res=${m.resistancePercent}% " +
                "pow=${m.powerWatts}W spd=${"%.1f".format(m.speedMph)}mph " +
                "frames=${source.framesReceived} ${source.connectionState.value}"
            Log.i(TAG, line)
            out.appendLine(line)
            // flush progressively so a mid-run pull still shows data
            runCatching { context.filesDir.resolve("t3_pedal.txt").writeText(out.toString()) }
            Thread.sleep(1_000)
            t++
        }
        source.stop()
        Log.i(TAG, "capture done — ${source.framesReceived} frames total")
    }

    private fun waitForBind(source: PelotonBikeDataSource): Boolean {
        val deadline = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < deadline) {
            if (source.isServiceBound) return true
            Thread.sleep(100)
        }
        return source.isServiceBound
    }

    private fun affernetInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo("com.onepeloton.affernetservice", 0)
        true
    }.getOrDefault(false)

    private companion object {
        const val TAG = "OpenRideT3Pedal"
        const val DURATION_MS = 90_000L
    }
}
