package dev.digitalducktape.openride.ui.history

import dev.digitalducktape.openride.core.data.Ride
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Lifetime aggregates and personal records for the History tab's overview band (v2 metrics
 * spec, after the profile-overview screens): everything is derived from the profile's
 * persisted [Ride] rows — no per-second samples touched, keeping the tab cheap however
 * long the history gets. Pure logic, plain-JUnit testable.
 */
data class HistoryStats(
    val totalRides: Int,
    val totalDurationSec: Long,
    val totalOutputKj: Double,
    /** Personal records; all `null` while there are no rides. */
    val bestOutputKj: Double?,
    val bestAvgPower: Int?,
    val longestRideSec: Int?,
) {
    companion object {
        fun from(rides: List<Ride>): HistoryStats = HistoryStats(
            totalRides = rides.size,
            totalDurationSec = rides.sumOf { it.durationSec.toLong() },
            totalOutputKj = rides.sumOf { it.outputKj },
            bestOutputKj = rides.maxOfOrNull { it.outputKj },
            bestAvgPower = rides.maxOfOrNull { it.avgPower },
            longestRideSec = rides.maxOfOrNull { it.durationSec },
        )

        /** The set of local dates with at least one ride — the calendar's marked days. */
        fun rideDates(rides: List<Ride>, zoneId: ZoneId = ZoneId.systemDefault()): Set<LocalDate> =
            rides.map { Instant.ofEpochMilli(it.startEpochMs).atZone(zoneId).toLocalDate() }.toSet()
    }
}
