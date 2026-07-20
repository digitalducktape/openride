package dev.digitalducktape.openride.core.route

/**
 * Where the rider is along the route right now (PRD #21/T21), derived purely from how far
 * they've ridden — display-only, no GPS. Gen 2 has no auto-resistance, so this drives the
 * on-screen grade/progress readout and nothing else.
 *
 * @param distanceAlongMeters accumulated ride distance mapped onto the route, clamped to
 *   `[0, totalDistanceMeters]`.
 * @param totalDistanceMeters full route length.
 * @param elevationMeters interpolated elevation at the current position.
 * @param gradePercent slope of the route segment the rider is currently on, in percent
 *   (rise/run × 100); positive uphill, negative downhill.
 */
data class RoutePosition(
    val distanceAlongMeters: Double,
    val totalDistanceMeters: Double,
    val elevationMeters: Double,
    val gradePercent: Double,
) {
    /** Distance still to go, never negative. */
    val remainingMeters: Double get() = (totalDistanceMeters - distanceAlongMeters).coerceAtLeast(0.0)

    /** Fractional progress `0.0..1.0`; `1.0` for a zero-length route (already "done"). */
    val progressFraction: Double
        get() = if (totalDistanceMeters > 0.0) {
            (distanceAlongMeters / totalDistanceMeters).coerceIn(0.0, 1.0)
        } else {
            1.0
        }

    /** Whether the rider has covered the whole route. */
    val isFinished: Boolean get() = totalDistanceMeters <= 0.0 || distanceAlongMeters >= totalDistanceMeters
}
