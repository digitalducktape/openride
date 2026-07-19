package dev.digitalducktape.openride.core.sensor

/**
 * A single snapshot of live bike sensor readings.
 *
 * @param cadenceRpm pedaling cadence in revolutions per minute
 * @param resistancePercent resistance knob position, 0-100
 * @param powerWatts instantaneous output power in watts
 * @param speedMph simulated/derived speed in miles per hour
 */
data class BikeMetrics(
    val cadenceRpm: Int,
    val resistancePercent: Int,
    val powerWatts: Int,
    val speedMph: Double,
) {
    companion object {
        val ZERO = BikeMetrics(cadenceRpm = 0, resistancePercent = 0, powerWatts = 0, speedMph = 0.0)
    }
}
