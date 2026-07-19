package dev.digitalducktape.openride.core.heartrate

/**
 * A decoded reading from the standard Bluetooth GATT **Heart Rate Measurement** characteristic
 * (`0x2A37`, under the Heart Rate Service `0x180D`) — see [HeartRateParser].
 *
 * @param bpm heart rate in beats per minute
 * @param sensorContactDetected `true`/`false` if the strap reports contact status, `null` if
 *   the strap doesn't support the sensor-contact feature at all (distinct from "false" —
 *   conflating the two would misreport "definitely not touching skin" for a strap that simply
 *   can't tell)
 * @param energyExpendedKj cumulative energy expended in kJ since the strap's last reset, if
 *   the characteristic includes it
 * @param rrIntervalsMs zero or more RR-intervals (beat-to-beat times) in milliseconds, if the
 *   characteristic includes them — OpenRide doesn't currently use these (no HRV feature), but
 *   they're decoded so a frame with them present doesn't desync the parser for the fields that
 *   follow them
 */
data class HeartRateMeasurement(
    val bpm: Int,
    val sensorContactDetected: Boolean?,
    val energyExpendedKj: Int?,
    val rrIntervalsMs: List<Int>,
)
