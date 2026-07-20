package dev.digitalducktape.openride.core.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Distance → position/grade mapping along a route (PRD #21/T21). Pure JVM — no Android, no
 * XML; [Route] is plain geometry.
 */
class RouteTest {

    /**
     * A three-point route running due north, climbing 20 m then descending 10 m. Expectations
     * are derived from the route's own cumulative distances rather than hardcoded metres, so
     * the assertions test the mapping logic, not the haversine constant.
     */
    private val route = Route.fromPoints(
        name = "Test climb",
        points = listOf(
            RoutePoint(lat = 37.0000, lon = -122.0, elevationMeters = 100.0),
            RoutePoint(lat = 37.0010, lon = -122.0, elevationMeters = 120.0),
            RoutePoint(lat = 37.0020, lon = -122.0, elevationMeters = 110.0),
        ),
    )

    private val seg1 get() = route.cumulativeMeters[1] - route.cumulativeMeters[0]
    private val seg2 get() = route.cumulativeMeters[2] - route.cumulativeMeters[1]

    @Test
    fun `cumulative distances start at zero and increase monotonically`() {
        assertEquals(0.0, route.cumulativeMeters[0], 1e-9)
        assertTrue(route.cumulativeMeters[1] > route.cumulativeMeters[0])
        assertTrue(route.cumulativeMeters[2] > route.cumulativeMeters[1])
        assertEquals(route.cumulativeMeters.last(), route.totalDistanceMeters, 1e-9)
    }

    @Test
    fun `at the start the rider is at the first point with the first segment's grade`() {
        val position = route.positionAt(0.0)

        assertEquals(0.0, position.distanceAlongMeters, 1e-9)
        assertEquals(100.0, position.elevationMeters, 1e-6)
        assertEquals(20.0 / seg1 * 100.0, position.gradePercent, 1e-6)
        assertEquals(0.0, position.progressFraction, 1e-9)
        assertEquals(route.totalDistanceMeters, position.remainingMeters, 1e-9)
        assertFalse(position.isFinished)
    }

    @Test
    fun `halfway along the first segment interpolates elevation`() {
        val position = route.positionAt(seg1 / 2)

        assertEquals(110.0, position.elevationMeters, 1e-6) // midpoint of 100 -> 120
        assertEquals(20.0 / seg1 * 100.0, position.gradePercent, 1e-6)
    }

    @Test
    fun `past the first point the grade switches to the descending second segment`() {
        val position = route.positionAt(seg1 + seg2 / 2)

        assertEquals(115.0, position.elevationMeters, 1e-6) // midpoint of 120 -> 110
        assertTrue("expected a negative grade downhill", position.gradePercent < 0.0)
        assertEquals(-10.0 / seg2 * 100.0, position.gradePercent, 1e-6)
    }

    @Test
    fun `progress and remaining track distance along the route`() {
        val position = route.positionAt(route.totalDistanceMeters / 2)

        assertEquals(0.5, position.progressFraction, 1e-6)
        assertEquals(route.totalDistanceMeters / 2, position.remainingMeters, 1e-6)
    }

    @Test
    fun `distance past the end clamps to the finish`() {
        val position = route.positionAt(route.totalDistanceMeters * 10)

        assertEquals(route.totalDistanceMeters, position.distanceAlongMeters, 1e-9)
        assertEquals(1.0, position.progressFraction, 1e-9)
        assertEquals(0.0, position.remainingMeters, 1e-9)
        assertEquals(110.0, position.elevationMeters, 1e-6) // final point's elevation
        assertTrue(position.isFinished)
    }

    @Test
    fun `negative distance clamps to the start`() {
        val position = route.positionAt(-500.0)

        assertEquals(0.0, position.distanceAlongMeters, 1e-9)
        assertEquals(100.0, position.elevationMeters, 1e-6)
    }

    @Test
    fun `a flat route reports zero grade throughout`() {
        val flat = Route.fromPoints(
            name = null,
            points = listOf(
                RoutePoint(37.0, -122.0, 50.0),
                RoutePoint(37.001, -122.0, 50.0),
            ),
        )

        assertEquals(0.0, flat.positionAt(flat.totalDistanceMeters / 2).gradePercent, 1e-9)
    }

    @Test
    fun `a route with fewer than two points is not rideable and maps everything to the start`() {
        val single = Route.fromPoints("Solo", listOf(RoutePoint(37.0, -122.0, 42.0)))

        assertFalse(single.isRideable)
        assertEquals(0.0, single.totalDistanceMeters, 1e-9)

        val position = single.positionAt(1_000.0)
        assertEquals(0.0, position.distanceAlongMeters, 1e-9)
        assertEquals(42.0, position.elevationMeters, 1e-9)
        assertEquals(0.0, position.gradePercent, 1e-9)
        assertEquals(1.0, position.progressFraction, 1e-9) // zero-length route reads as done
        assertTrue(position.isFinished)
    }

    @Test
    fun `an empty route is handled without crashing`() {
        val empty = Route.fromPoints(null, emptyList())

        assertFalse(empty.isRideable)
        assertEquals(0.0, empty.totalDistanceMeters, 1e-9)
        assertEquals(0.0, empty.positionAt(100.0).elevationMeters, 1e-9)
    }

    @Test
    fun `haversine matches the known length of one degree of latitude`() {
        // One degree of latitude is ~111.19 km on a sphere of mean Earth radius.
        val meters = haversineMeters(37.0, -122.0, 38.0, -122.0)

        assertEquals(111_195.0, meters, 50.0)
    }

    @Test
    fun `haversine is zero for identical points`() {
        assertEquals(0.0, haversineMeters(37.0, -122.0, 37.0, -122.0), 1e-9)
    }
}
