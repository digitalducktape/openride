package dev.digitalducktape.openride.core.ride

/**
 * Running aggregates for the in-progress ride, updated once per sample. Mirrors the
 * aggregate fields persisted on [dev.digitalducktape.openride.core.data.Ride] (which has no
 * `maxResistance` field, so neither does this).
 */
data class LiveAggregates(
    val avgCadence: Int = 0,
    val maxCadence: Int = 0,
    val avgPower: Int = 0,
    val maxPower: Int = 0,
    val avgResistance: Int = 0,
)
