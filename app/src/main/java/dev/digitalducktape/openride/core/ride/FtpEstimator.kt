package dev.digitalducktape.openride.core.ride

import dev.digitalducktape.openride.core.data.RideSample
import kotlin.math.roundToInt

/**
 * Estimates FTP as 95% of a rider's best 20-minute average power within a ride's per-second
 * sample series — the formula the PRD's power-zone gist reference documents (FTP = 95% of a
 * 20-min avg output). Used to suggest an FTP update from the ride-summary screen once a ride
 * is long enough to contain a full 20-minute window; [PowerZone] then drives live zone
 * display off whatever FTP ends up on the profile.
 */
object FtpEstimator {
    const val WINDOW_SEC = 20 * 60

    /**
     * Returns the suggested FTP in watts, or `null` if [samples] don't span at least
     * [WINDOW_SEC] seconds (not enough data for a valid 20-minute window). [samples] need not
     * be sorted; a defensive sort by [RideSample.tSec] runs first since the rolling-sum
     * window relies on sample order.
     */
    fun estimateFtp(samples: List<RideSample>): Int? {
        if (samples.size < WINDOW_SEC) return null

        val sorted = samples.sortedBy { it.tSec }
        var windowSum = sorted.take(WINDOW_SEC).sumOf { it.power }
        var bestSum = windowSum
        for (i in WINDOW_SEC until sorted.size) {
            windowSum += sorted[i].power - sorted[i - WINDOW_SEC].power
            if (windowSum > bestSum) bestSum = windowSum
        }

        val bestAvgWatts = bestSum.toDouble() / WINDOW_SEC
        return (bestAvgWatts * FTP_FRACTION_OF_20MIN_AVG).roundToInt()
    }

    private const val FTP_FRACTION_OF_20MIN_AVG = 0.95
}
