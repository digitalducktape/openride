package dev.digitalducktape.openride.core.route

/**
 * One point along an imported GPX route (PRD #21/T21).
 *
 * @param lat WGS-84 latitude, degrees.
 * @param lon WGS-84 longitude, degrees.
 * @param elevationMeters point elevation, metres. Defaults to `0.0` when the GPX has no
 *   `<ele>` for the point — a route with no elevation data then simply reads as flat (0% grade
 *   throughout), which is the honest display rather than an invented profile.
 */
data class RoutePoint(
    val lat: Double,
    val lon: Double,
    val elevationMeters: Double = 0.0,
)
