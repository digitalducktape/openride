package dev.digitalducktape.openride.core.heartrate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Byte fixtures constructed directly from the Bluetooth SIG GATT Heart Rate Measurement
 * (0x2A37) spec's documented wire layout — a public, standard format (see [HeartRateParser]'s
 * doc), not captured off any real strap (T17 has no hardware to capture from; see the ticket).
 */
class HeartRateParserTest {

    @Test
    fun `UINT8 heart rate with no optional fields`() {
        // flags = 0x00: UINT8 format, no contact info, no energy, no RR.
        val bytes = byteArrayOf(0x00, 0x4B) // 75 bpm

        val result = HeartRateParser.parse(bytes)

        assertEquals(75, result.bpm)
        assertNull(result.sensorContactDetected)
        assertNull(result.energyExpendedKj)
        assertEquals(emptyList<Int>(), result.rrIntervalsMs)
    }

    @Test
    fun `sensor contact supported and detected`() {
        // flags = 0x06: bit1 (contact status=detected) + bit2 (contact supported).
        val bytes = byteArrayOf(0x06, 0x58) // 88 bpm

        val result = HeartRateParser.parse(bytes)

        assertEquals(88, result.bpm)
        assertEquals(true, result.sensorContactDetected)
    }

    @Test
    fun `sensor contact supported but not detected`() {
        // flags = 0x04: bit2 (contact supported) only, bit1 clear (not detected).
        val bytes = byteArrayOf(0x04, 0x3C) // 60 bpm

        val result = HeartRateParser.parse(bytes)

        assertEquals(60, result.bpm)
        assertEquals(false, result.sensorContactDetected)
    }

    @Test
    fun `UINT16 heart rate value format`() {
        // flags = 0x01: UINT16 format. 300 bpm = 0x012C, little-endian [0x2C, 0x01].
        val bytes = byteArrayOf(0x01, 0x2C, 0x01)

        val result = HeartRateParser.parse(bytes)

        assertEquals(300, result.bpm)
    }

    @Test
    fun `energy expended present`() {
        // flags = 0x08: energy expended present, UINT8 heart rate.
        // 1234 kJ = 0x04D2, little-endian [0xD2, 0x04].
        val bytes = byteArrayOf(0x08, 0x46, 0xD2.toByte(), 0x04) // 70 bpm

        val result = HeartRateParser.parse(bytes)

        assertEquals(70, result.bpm)
        assertEquals(1234, result.energyExpendedKj)
    }

    @Test
    fun `single RR-interval present`() {
        // flags = 0x10: RR-interval present. 1024 raw units (1/1024 s) = 1000 ms.
        val bytes = byteArrayOf(0x10, 0x41, 0x00, 0x04) // 65 bpm, RR raw 1024 = [0x00, 0x04]

        val result = HeartRateParser.parse(bytes)

        assertEquals(65, result.bpm)
        assertEquals(listOf(1000), result.rrIntervalsMs)
    }

    @Test
    fun `multiple RR-intervals present`() {
        // flags = 0x10. Two RR values: 1024 (-> 1000 ms) and 512 (-> 500 ms).
        val bytes = byteArrayOf(
            0x10, 0x41, // flags, 65 bpm
            0x00, 0x04, // 1024 -> 1000 ms
            0x00, 0x02, // 512 -> 500 ms
        )

        val result = HeartRateParser.parse(bytes)

        assertEquals(listOf(1000, 500), result.rrIntervalsMs)
    }

    @Test
    fun `every optional field present at once`() {
        // flags = 0x1F: UINT16 format + contact detected + contact supported + energy + RR.
        val bytes = byteArrayOf(
            0x1F,
            0xC8.toByte(), 0x00, // 200 bpm (UINT16 LE)
            0xF4.toByte(), 0x01, // 500 kJ (UINT16 LE)
            0x00, 0x04, // RR 1024 -> 1000 ms
        )

        val result = HeartRateParser.parse(bytes)

        assertEquals(200, result.bpm)
        assertEquals(true, result.sensorContactDetected)
        assertEquals(500, result.energyExpendedKj)
        assertEquals(listOf(1000), result.rrIntervalsMs)
    }

    @Test
    fun `empty payload throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            HeartRateParser.parse(ByteArray(0))
        }
    }

    @Test
    fun `truncated UINT16 heart rate value throws rather than reading garbage`() {
        // flags claims UINT16 format but only one byte follows.
        val bytes = byteArrayOf(0x01, 0x2C)

        assertThrows(IllegalArgumentException::class.java) {
            HeartRateParser.parse(bytes)
        }
    }

    @Test
    fun `truncated energy-expended field throws`() {
        // flags claims energy expended present but payload is cut short.
        val bytes = byteArrayOf(0x08, 0x46, 0xD2.toByte())

        assertThrows(IllegalArgumentException::class.java) {
            HeartRateParser.parse(bytes)
        }
    }

    @Test
    fun `a trailing odd byte in the RR-interval tail is ignored, not misread`() {
        // flags = 0x10, one full RR pair, then a single dangling byte that can't form a pair.
        val bytes = byteArrayOf(0x10, 0x41, 0x00, 0x04, 0x7F)

        val result = HeartRateParser.parse(bytes)

        assertEquals(listOf(1000), result.rrIntervalsMs)
    }
}
