package dev.digitalducktape.openride.core.export

import dev.digitalducktape.openride.core.data.Ride
import dev.digitalducktape.openride.core.data.RideSample
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

class TcxExporterTest {

    private fun fixtureRide() = Ride(
        id = 42L,
        profileId = 1L,
        startEpochMs = 1_700_000_000_000L,
        durationSec = 3,
        avgCadence = 90,
        maxCadence = 95,
        avgPower = 150,
        maxPower = 200,
        avgResistance = 50,
        outputKj = 0.45,
        calories = 43,
    )

    private fun fixtureSamples() = listOf(
        RideSample(rideId = 42L, tSec = 0, cadence = 85, resistance = 45, power = 120),
        RideSample(rideId = 42L, tSec = 1, cadence = 90, resistance = 50, power = 150),
        RideSample(rideId = 42L, tSec = 2, cadence = 95, resistance = 55, power = 200),
    )

    private fun parse(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newDocumentBuilder().parse(xml.byteInputStream())
    }

    @Test
    fun `produces well-formed XML with the TrainingCenterDatabase root`() {
        val xml = TcxExporter.export(fixtureRide(), fixtureSamples())

        val doc = parse(xml)

        assertEquals("TrainingCenterDatabase", doc.documentElement.localName)
        assertTrue(xml.trimStart().startsWith("<?xml"))
    }

    @Test
    fun `emits one Trackpoint per RideSample`() {
        val xml = TcxExporter.export(fixtureRide(), fixtureSamples())
        val doc = parse(xml)

        val trackpoints = doc.getElementsByTagNameNS("*", "Trackpoint")

        assertEquals(3, trackpoints.length)
    }

    @Test
    fun `Trackpoint Cadence and TPX Watts match the sample series in order`() {
        val xml = TcxExporter.export(fixtureRide(), fixtureSamples())
        val doc = parse(xml)

        val trackpoints = doc.getElementsByTagNameNS("*", "Trackpoint")
        val expectedCadence = listOf(85, 90, 95)
        val expectedWatts = listOf(120, 150, 200)

        for (i in 0 until trackpoints.length) {
            val point = trackpoints.item(i) as Element
            val cadence = point.getElementsByTagNameNS("*", "Cadence").item(0).textContent.toInt()
            val watts = point.getElementsByTagNameNS("*", "Watts").item(0).textContent.toInt()
            assertEquals(expectedCadence[i], cadence)
            assertEquals(expectedWatts[i], watts)
        }
    }

    @Test
    fun `Lap summary fields match the ride's aggregates`() {
        val xml = TcxExporter.export(fixtureRide(), fixtureSamples())
        val doc = parse(xml)

        val lap = doc.getElementsByTagNameNS("*", "Lap").item(0) as Element
        val totalTimeSeconds = lap.getElementsByTagNameNS("*", "TotalTimeSeconds").item(0).textContent
        val calories = lap.getElementsByTagNameNS("*", "Calories").item(0).textContent
        val avgWatts = lap.getElementsByTagNameNS("*", "AvgWatts").item(0).textContent
        val maxWatts = lap.getElementsByTagNameNS("*", "MaxWatts").item(0).textContent

        assertEquals("3", totalTimeSeconds)
        assertEquals("43", calories)
        assertEquals("150", avgWatts)
        assertEquals("200", maxWatts)
    }

    @Test
    fun `distance accumulates monotonically across trackpoints`() {
        val xml = TcxExporter.export(fixtureRide(), fixtureSamples())
        val doc = parse(xml)

        val trackpoints = doc.getElementsByTagNameNS("*", "Trackpoint")
        var previous = -1.0
        for (i in 0 until trackpoints.length) {
            val point = trackpoints.item(i) as Element
            val distance = point.getElementsByTagNameNS("*", "DistanceMeters").item(0).textContent.toDouble()
            assertTrue("distance should be non-decreasing (point $i)", distance >= previous)
            previous = distance
        }
    }

    @Test
    fun `zero-power samples produce zero speed and no distance gain`() {
        val stationarySamples = listOf(
            RideSample(rideId = 42L, tSec = 0, cadence = 0, resistance = 0, power = 0),
            RideSample(rideId = 42L, tSec = 1, cadence = 0, resistance = 0, power = 0),
        )
        val xml = TcxExporter.export(fixtureRide().copy(durationSec = 2), stationarySamples)
        val doc = parse(xml)

        val trackpoints = doc.getElementsByTagNameNS("*", "Trackpoint")
        for (i in 0 until trackpoints.length) {
            val point = trackpoints.item(i) as Element
            val distance = point.getElementsByTagNameNS("*", "DistanceMeters").item(0).textContent.toDouble()
            assertEquals(0.0, distance, 0.0001)
        }
    }

    @Test
    fun `includes HeartRateBpm only for samples that have a reading`() {
        val samples = listOf(
            RideSample(rideId = 42L, tSec = 0, cadence = 85, resistance = 45, power = 120, heartRateBpm = 128),
            RideSample(rideId = 42L, tSec = 1, cadence = 90, resistance = 50, power = 150, heartRateBpm = null),
        )
        val xml = TcxExporter.export(fixtureRide().copy(durationSec = 2), samples)
        val doc = parse(xml)

        val trackpoints = doc.getElementsByTagNameNS("*", "Trackpoint")
        val firstHr = (trackpoints.item(0) as Element).getElementsByTagNameNS("*", "HeartRateBpm")
        val secondHr = (trackpoints.item(1) as Element).getElementsByTagNameNS("*", "HeartRateBpm")

        assertEquals(1, firstHr.length)
        assertEquals("128", (firstHr.item(0) as Element).getElementsByTagNameNS("*", "Value").item(0).textContent)
        assertEquals(0, secondHr.length)
    }

    @Test
    fun `handles an empty sample series without crashing`() {
        val xml = TcxExporter.export(fixtureRide(), emptyList())
        val doc = parse(xml)

        assertEquals(0, doc.getElementsByTagNameNS("*", "Trackpoint").length)
    }

    @Test
    fun `numeric fields never use a locale comma decimal separator`() {
        val xml = TcxExporter.export(fixtureRide(), fixtureSamples())

        assertFalse(xml.contains(Regex("""\d,\d""")))
    }
}
