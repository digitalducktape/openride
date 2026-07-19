package dev.digitalducktape.openride.core.sensor

import kotlin.math.sqrt

/**
 * Derives road speed (mph) from output power (watts) for the Peloton Bike Gen 2.
 *
 * The Gen 2 sensor service reports cadence, resistance and power but NOT speed — speed on the
 * stock Peloton bike is itself a synthetic value computed from power. This reproduces that
 * mapping using the piecewise cubic fit that the PeloMon project reverse-engineered from the
 * stock bike's own power->speed curve (https://ihaque.org/posts/2020/12/25/pelomon-part-ib-computing-speed/),
 * the same curve grupetto uses. Input is instantaneous power in watts; output is mph.
 */
fun pelotonSpeedMphFromPower(powerWatts: Double): Double {
    if (powerWatts < 0.1) return 0.0
    val s = sqrt(powerWatts)
    return if (powerWatts < 26.0) {
        0.057 - 0.172 * s + 0.759 * s * s - 0.079 * s * s * s
    } else {
        -1.635 + 2.325 * s - 0.064 * s * s + 0.001 * s * s * s
    }.coerceAtLeast(0.0)
}
