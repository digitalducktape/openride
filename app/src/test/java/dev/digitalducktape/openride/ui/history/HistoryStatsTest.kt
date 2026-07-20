package dev.digitalducktape.openride.ui.history

import dev.digitalducktape.openride.core.data.Ride
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryStatsTest {

    private fun ride(
        id: Long,
        startEpochMs: Long = 0,
        durationSec: Int = 1200,
        avgPower: Int = 150,
        outputKj: Double = 180.0,
    ) = Ride(
        id = id,
        profileId = 1,
        startEpochMs = startEpochMs,
        durationSec = durationSec,
        avgCadence = 70,
        maxCadence = 90,
        avgPower = avgPower,
        maxPower = 300,
        avgResistance = 40,
        outputKj = outputKj,
        calories = null,
    )

    @Test
    fun `empty history has zero totals and no records`() {
        val stats = HistoryStats.from(emptyList())

        assertEquals(0, stats.totalRides)
        assertEquals(0L, stats.totalDurationSec)
        assertEquals(0.0, stats.totalOutputKj, 0.0)
        assertNull(stats.bestOutputKj)
        assertNull(stats.bestAvgPower)
        assertNull(stats.longestRideSec)
    }

    @Test
    fun `totals sum and records take each ride's best`() {
        val stats = HistoryStats.from(
            listOf(
                ride(1, durationSec = 1200, avgPower = 150, outputKj = 180.0),
                ride(2, durationSec = 2700, avgPower = 210, outputKj = 503.0),
                ride(3, durationSec = 600, avgPower = 120, outputKj = 75.5),
            ),
        )

        assertEquals(3, stats.totalRides)
        assertEquals(4500L, stats.totalDurationSec)
        assertEquals(758.5, stats.totalOutputKj, 0.001)
        assertEquals(503.0, stats.bestOutputKj!!, 0.001)
        assertEquals(210, stats.bestAvgPower)
        assertEquals(2700, stats.longestRideSec)
    }

    @Test
    fun `rideDates collapses rides on the same local day`() {
        val zone = ZoneId.of("UTC")
        val day1Morning = 86_400_000L // 1970-01-02 00:00 UTC
        val day1Evening = day1Morning + 12 * 3_600_000L
        val day2 = day1Morning + 86_400_000L

        val dates = HistoryStats.rideDates(
            listOf(ride(1, day1Morning), ride(2, day1Evening), ride(3, day2)),
            zone,
        )

        assertEquals(
            setOf(LocalDate.of(1970, 1, 2), LocalDate.of(1970, 1, 3)),
            dates,
        )
    }
}
