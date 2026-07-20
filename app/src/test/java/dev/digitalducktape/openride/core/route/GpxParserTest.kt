package dev.digitalducktape.openride.core.route

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * GPX parsing (PRD #21/T21). Runs under Robolectric because [GpxParser] uses Android's
 * [android.util.Xml] pull parser, same as the Atom feed parser.
 */
@RunWith(AndroidJUnit4::class)
class GpxParserTest {

    private val parser = GpxParser()

    private fun parse(xml: String): Route = parser.parse(xml.byteInputStream())

    @Test
    fun `parses track points with elevation and the route name`() {
        val route = parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
              <metadata><name>Hawk Hill</name></metadata>
              <trk>
                <trkseg>
                  <trkpt lat="37.8320" lon="-122.4795"><ele>10.0</ele></trkpt>
                  <trkpt lat="37.8330" lon="-122.4795"><ele>30.0</ele></trkpt>
                  <trkpt lat="37.8340" lon="-122.4795"><ele>60.0</ele></trkpt>
                </trkseg>
              </trk>
            </gpx>
            """.trimIndent(),
        )

        assertEquals("Hawk Hill", route.name)
        assertEquals(3, route.points.size)
        assertEquals(37.8320, route.points[0].lat, 1e-9)
        assertEquals(-122.4795, route.points[0].lon, 1e-9)
        assertEquals(10.0, route.points[0].elevationMeters, 1e-9)
        assertEquals(60.0, route.points[2].elevationMeters, 1e-9)
        assertTrue(route.isRideable)
        // ~111 m per 0.001 deg of latitude; two such steps.
        assertEquals(222.0, route.totalDistanceMeters, 3.0)
    }

    @Test
    fun `points without an ele element read as zero elevation`() {
        val route = parse(
            """
            <gpx><trk><trkseg>
              <trkpt lat="37.0" lon="-122.0"/>
              <trkpt lat="37.001" lon="-122.0"/>
            </trkseg></trk></gpx>
            """.trimIndent(),
        )

        assertEquals(2, route.points.size)
        assertEquals(0.0, route.points[0].elevationMeters, 1e-9)
        assertEquals(0.0, route.positionAt(50.0).gradePercent, 1e-9)
    }

    @Test
    fun `falls back to route points when there is no track`() {
        val route = parse(
            """
            <gpx>
              <rte>
                <name>Planned loop</name>
                <rtept lat="37.0" lon="-122.0"><ele>5.0</ele></rtept>
                <rtept lat="37.001" lon="-122.0"><ele>15.0</ele></rtept>
              </rte>
            </gpx>
            """.trimIndent(),
        )

        assertEquals("Planned loop", route.name)
        assertEquals(2, route.points.size)
        assertEquals(15.0, route.points[1].elevationMeters, 1e-9)
    }

    @Test
    fun `falls back to waypoints when there is neither track nor route`() {
        val route = parse(
            """
            <gpx>
              <wpt lat="37.0" lon="-122.0"><ele>1.0</ele></wpt>
              <wpt lat="37.001" lon="-122.0"><ele>2.0</ele></wpt>
            </gpx>
            """.trimIndent(),
        )

        assertEquals(2, route.points.size)
    }

    @Test
    fun `prefers track points over route points when both are present`() {
        val route = parse(
            """
            <gpx>
              <rte>
                <rtept lat="10.0" lon="10.0"/>
                <rtept lat="10.001" lon="10.0"/>
              </rte>
              <trk><trkseg>
                <trkpt lat="37.0" lon="-122.0"/>
                <trkpt lat="37.001" lon="-122.0"/>
                <trkpt lat="37.002" lon="-122.0"/>
              </trkseg></trk>
            </gpx>
            """.trimIndent(),
        )

        assertEquals(3, route.points.size)
        assertEquals(37.0, route.points[0].lat, 1e-9)
    }

    @Test
    fun `a GPX with no points yields an unrideable route`() {
        val route = parse("""<gpx><metadata><name>Empty</name></metadata></gpx>""")

        assertTrue(route.points.isEmpty())
        assertFalse(route.isRideable)
        assertEquals(0.0, route.totalDistanceMeters, 1e-9)
    }

    @Test
    fun `a single-point GPX is not rideable`() {
        val route = parse("""<gpx><trk><trkseg><trkpt lat="37.0" lon="-122.0"/></trkseg></trk></gpx>""")

        assertEquals(1, route.points.size)
        assertFalse(route.isRideable)
    }

    @Test
    fun `a GPX with no name leaves the route name null`() {
        val route = parse(
            """
            <gpx><trk><trkseg>
              <trkpt lat="37.0" lon="-122.0"/>
              <trkpt lat="37.001" lon="-122.0"/>
            </trkseg></trk></gpx>
            """.trimIndent(),
        )

        assertNull(route.name)
    }
}
