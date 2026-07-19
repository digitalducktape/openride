package dev.digitalducktape.openride.core.heartrate

/**
 * Decodes the standard Bluetooth GATT **Heart Rate Measurement** characteristic (`0x2A37`,
 * under the Heart Rate Service `0x180D`) — reimplemented directly from the public Bluetooth
 * SIG GATT specification's byte layout (not copied from any third-party project; this is a
 * standard, freely reimplementable wire format used by essentially every BLE chest strap/
 * watch on the market — see PRD P1-4, T17).
 *
 * Wire format (little-endian throughout):
 *
 * | Byte(s) | Field | Present when |
 * |---|---|---|
 * | 0 | Flags | always |
 * | 1 (or 1-2) | Heart Rate Value (UINT8 or UINT16, per flags bit 0) | always |
 * | next 2 | Energy Expended (UINT16, kJ) | flags bit 3 set |
 * | remaining, 2 each | RR-Interval(s) (UINT16, units of 1/1024 s) | flags bit 4 set |
 *
 * Flags byte bit layout:
 * - bit 0: Heart Rate Value Format (0 = UINT8, 1 = UINT16)
 * - bit 1: Sensor Contact Status (only meaningful if bit 2 is set)
 * - bit 2: Sensor Contact Feature Supported
 * - bit 3: Energy Expended Status present
 * - bit 4: RR-Interval present
 * - bits 5-7: reserved
 */
object HeartRateParser {
    private const val FLAG_VALUE_FORMAT_UINT16 = 0x01
    private const val FLAG_SENSOR_CONTACT_STATUS = 0x02
    private const val FLAG_SENSOR_CONTACT_SUPPORTED = 0x04
    private const val FLAG_ENERGY_EXPENDED_PRESENT = 0x08
    private const val FLAG_RR_INTERVAL_PRESENT = 0x10

    /** RR-interval units are 1/1024 second; this converts a raw value to whole milliseconds. */
    private const val RR_INTERVAL_UNITS_PER_SECOND = 1024L

    /**
     * Parses a raw Heart Rate Measurement characteristic value. Throws
     * [IllegalArgumentException] if [bytes] is empty (no flags byte) or too short for the
     * fields its own flags claim are present — a malformed/truncated notification should be
     * dropped by the caller, not silently misread as a wrong heart rate.
     */
    fun parse(bytes: ByteArray): HeartRateMeasurement {
        require(bytes.isNotEmpty()) { "Heart Rate Measurement payload must include at least a flags byte" }

        val flags = bytes[0].toInt() and 0xFF
        val isUint16Value = (flags and FLAG_VALUE_FORMAT_UINT16) != 0
        val contactSupported = (flags and FLAG_SENSOR_CONTACT_SUPPORTED) != 0
        val contactDetected = if (contactSupported) (flags and FLAG_SENSOR_CONTACT_STATUS) != 0 else null
        val energyExpendedPresent = (flags and FLAG_ENERGY_EXPENDED_PRESENT) != 0
        val rrIntervalPresent = (flags and FLAG_RR_INTERVAL_PRESENT) != 0

        var offset = 1
        val bpm: Int
        if (isUint16Value) {
            requireBytes(bytes, offset, 2)
            bpm = readUInt16LE(bytes, offset)
            offset += 2
        } else {
            requireBytes(bytes, offset, 1)
            bpm = bytes[offset].toInt() and 0xFF
            offset += 1
        }

        val energyExpendedKj = if (energyExpendedPresent) {
            requireBytes(bytes, offset, 2)
            readUInt16LE(bytes, offset).also { offset += 2 }
        } else {
            null
        }

        val rrIntervalsMs = mutableListOf<Int>()
        if (rrIntervalPresent) {
            while (offset + 1 < bytes.size) {
                val rawUnits = readUInt16LE(bytes, offset)
                rrIntervalsMs.add((rawUnits * 1000L / RR_INTERVAL_UNITS_PER_SECOND).toInt())
                offset += 2
            }
        }

        return HeartRateMeasurement(
            bpm = bpm,
            sensorContactDetected = contactDetected,
            energyExpendedKj = energyExpendedKj,
            rrIntervalsMs = rrIntervalsMs,
        )
    }

    private fun requireBytes(bytes: ByteArray, offset: Int, count: Int) {
        require(offset + count <= bytes.size) {
            "Heart Rate Measurement payload too short: need $count more byte(s) at offset $offset, have ${bytes.size}"
        }
    }

    private fun readUInt16LE(bytes: ByteArray, offset: Int): Int {
        val lo = bytes[offset].toInt() and 0xFF
        val hi = bytes[offset + 1].toInt() and 0xFF
        return (hi shl 8) or lo
    }
}
