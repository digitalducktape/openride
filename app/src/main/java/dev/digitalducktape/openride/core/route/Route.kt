package dev.digitalducktape.openride.core.route

/**
 * An imported GPX route (PRD #21/T21): an ordered list of [RoutePoint]s plus the cumulative
 * distance to each, so mapping an accumulated ride distance onto a position/grade is a cheap
 * lookup. Immutable; build via [fromPoints] so the cumulative distances are always consistent
 * with the points.
 *
 * @param name the route's `<name>` from the GPX, if any.
 * @param points the route geometry, in order.
 * @param cumulativeMeters `cumulativeMeters[i]` is the along-route distance from the start to
 *   `points[i]`; `cumulativeMeters[0]` is always `0.0`. Same length as [points].
 */
class Route private constructor(
    val name: String?,
    val points: List<RoutePoint>,
    val cumulativeMeters: List<Double>,
) {
    /** Total route length in metres (`0.0` for a route with fewer than two points). */
    val totalDistanceMeters: Double get() = cumulativeMeters.lastOrNull() ?: 0.0

    /** Whether this route has enough geometry to simulate along (at least two points). */
    val isRideable: Boolean get() = points.size >= 2

    /**
     * Maps an accumulated ride distance onto the route, returning the interpolated elevation and
     * the grade of the segment the rider is currently on. [distanceMeters] is clamped to the
     * route bounds, so values past the end pin to the finish (and before the start to 0).
     */
    fun positionAt(distanceMeters: Double): RoutePosition {
        val total = totalDistanceMeters
        if (points.size < 2 || total <= 0.0) {
            return RoutePosition(
                distanceAlongMeters = 0.0,
                totalDistanceMeters = total,
                elevationMeters = points.firstOrNull()?.elevationMeters ?: 0.0,
                gradePercent = 0.0,
            )
        }

        val d = distanceMeters.coerceIn(0.0, total)
        // Find the last point whose cumulative distance is <= d; that starts the current segment.
        // (Linear scan is fine for the point counts in a ride GPX; swap for a binary search if
        // ever needed.)
        var i = 0
        while (i < cumulativeMeters.size - 1 && cumulativeMeters[i + 1] < d) i++
        // Clamp so the segment [i, i+1] is always valid even exactly at the finish.
        if (i >= points.size - 1) i = points.size - 2

        val segStart = cumulativeMeters[i]
        val segLen = cumulativeMeters[i + 1] - segStart
        val t = if (segLen > 0.0) ((d - segStart) / segLen).coerceIn(0.0, 1.0) else 0.0

        val eleStart = points[i].elevationMeters
        val eleEnd = points[i + 1].elevationMeters
        val elevation = eleStart + (eleEnd - eleStart) * t
        // Grade uses the segment's horizontal run (its along-route length) as the denominator.
        val grade = if (segLen > 0.0) (eleEnd - eleStart) / segLen * 100.0 else 0.0

        return RoutePosition(
            distanceAlongMeters = d,
            totalDistanceMeters = total,
            elevationMeters = elevation,
            gradePercent = grade,
        )
    }

    companion object {
        /** Builds a [Route], computing cumulative distances from consecutive-point haversine gaps. */
        fun fromPoints(name: String?, points: List<RoutePoint>): Route {
            val cumulative = ArrayList<Double>(points.size)
            var running = 0.0
            points.forEachIndexed { index, point ->
                if (index > 0) {
                    val prev = points[index - 1]
                    running += haversineMeters(prev.lat, prev.lon, point.lat, point.lon)
                }
                cumulative.add(running)
            }
            return Route(name = name, points = points, cumulativeMeters = cumulative)
        }
    }
}
