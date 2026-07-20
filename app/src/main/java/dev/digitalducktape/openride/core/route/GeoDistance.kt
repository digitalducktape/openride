package dev.digitalducktape.openride.core.route

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Great-circle (haversine) distance in metres between two WGS-84 coordinates (PRD #21/T21).
 * Good to well within a metre over the segment lengths in a typical ride GPX, and — unlike a
 * flat-earth approximation — correct near the poles and across the antimeridian, without
 * pulling in a geo library.
 */
fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
}

/** Mean Earth radius (metres) — the standard value used for haversine distance. */
private const val EARTH_RADIUS_METERS = 6_371_000.0
