package dev.digitalducktape.openride.core.export

import dev.digitalducktape.openride.core.data.Ride
import dev.digitalducktape.openride.core.data.RideSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {

    private fun ride(id: Long, power: Int, calories: Int?) = Ride(
        id = id,
        profileId = 1L,
        startEpochMs = 1_700_000_000_000L,
        durationSec = 1800,
        avgCadence = 90,
        maxCadence = 100,
        avgPower = power,
        maxPower = power + 50,
        avgResistance = 50,
        outputKj = power * 1.8,
        calories = calories,
    )

    // --- exportHistory (PRD P1-2) ------------------------------------------------------------

    @Test
    fun `exportHistory emits a header row even for an empty history`() {
        val csv = CsvExporter.exportHistory(emptyList())

        assertEquals(
            "date,duration_sec,avg_cadence,max_cadence,avg_power,max_power,avg_resistance,output_kj,calories\n",
            csv,
        )
    }

    @Test
    fun `exportHistory emits one row per ride in the given order`() {
        val rides = listOf(ride(id = 1, power = 150, calories = 200), ride(id = 2, power = 180, calories = 250))

        val csv = CsvExporter.exportHistory(rides)
        val lines = csv.trimEnd('\n').split("\n")

        assertEquals(3, lines.size) // header + 2 rows
        assertEquals("1800,90,100,150,200,50,270.00,200", lines[1].substringAfter(","))
        assertEquals("1800,90,100,180,230,50,324.00,250", lines[2].substringAfter(","))
    }

    @Test
    fun `exportHistory renders a null calories field as an empty CSV cell`() {
        val csv = CsvExporter.exportHistory(listOf(ride(id = 1, power = 150, calories = null)))
        val dataRow = csv.trimEnd('\n').split("\n")[1]

        assertTrue(dataRow.endsWith(","))
    }

    @Test
    fun `exportHistory formats output_kj with a decimal point, not a locale comma`() {
        // A comma-decimal locale bug would split output_kj into two CSV cells, throwing off
        // the column count below (9 header fields) rather than just looking "wrong" — a much
        // more reliable check for a CSV than scanning for a stray comma character.
        val csv = CsvExporter.exportHistory(listOf(ride(id = 1, power = 150, calories = 200)))
        val dataRow = csv.trimEnd('\n').split("\n")[1]

        assertEquals(9, dataRow.split(",").size)
        assertTrue(dataRow.contains("270.00"))
    }

    // --- exportRideSamples -------------------------------------------------------------------

    @Test
    fun `exportRideSamples emits a header row even for an empty series`() {
        val csv = CsvExporter.exportRideSamples(emptyList())

        assertEquals("t_sec,cadence,resistance,power\n", csv)
    }

    @Test
    fun `exportRideSamples emits samples sorted by tSec regardless of input order`() {
        val samples = listOf(
            RideSample(rideId = 1L, tSec = 2, cadence = 95, resistance = 55, power = 200),
            RideSample(rideId = 1L, tSec = 0, cadence = 85, resistance = 45, power = 120),
            RideSample(rideId = 1L, tSec = 1, cadence = 90, resistance = 50, power = 150),
        )

        val csv = CsvExporter.exportRideSamples(samples)
        val lines = csv.trimEnd('\n').split("\n").drop(1)

        assertEquals(
            listOf("0,85,45,120", "1,90,50,150", "2,95,55,200"),
            lines,
        )
    }
}
