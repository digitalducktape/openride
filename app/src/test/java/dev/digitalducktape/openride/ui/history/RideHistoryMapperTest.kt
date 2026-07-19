package dev.digitalducktape.openride.ui.history

import dev.digitalducktape.openride.core.data.Ride
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class RideHistoryMapperTest {

    private val utc = ZoneOffset.UTC

    private fun rideAt(localDate: LocalDate): Ride {
        val epochMs = localDate.atStartOfDay(utc).toInstant().toEpochMilli()
        return Ride(
            id = 1L,
            profileId = 1L,
            startEpochMs = epochMs,
            durationSec = 125,
            avgCadence = 82,
            maxCadence = 101,
            avgPower = 150,
            maxPower = 240,
            avgResistance = 40,
            outputKj = 18.75,
            calories = 20,
        )
    }

    @Test
    fun `maps duration using the shared elapsed formatter`() {
        val row = RideHistoryMapper.map(rideAt(LocalDate.of(2026, 1, 1)), utc)

        assertEquals("02:05", row.durationLabel)
    }

    @Test
    fun `formats the date as month day comma year`() {
        val row = RideHistoryMapper.map(rideAt(LocalDate.of(2026, 3, 14)), utc)

        assertEquals("Mar 14, 2026", row.dateLabel)
    }

    @Test
    fun `formats output kJ to one decimal place`() {
        val row = RideHistoryMapper.map(rideAt(LocalDate.of(2026, 1, 1)), utc)

        assertEquals("18.8 kJ", row.outputKjLabel)
    }

    @Test
    fun `formats avg cadence with an explicit avg label`() {
        val row = RideHistoryMapper.map(rideAt(LocalDate.of(2026, 1, 1)), utc)

        assertEquals("82 rpm avg", row.avgCadenceLabel)
    }

    @Test
    fun `carries the ride id through unchanged`() {
        val ride = rideAt(LocalDate.of(2026, 1, 1)).copy(id = 42L)

        val row = RideHistoryMapper.map(ride, utc)

        assertEquals(42L, row.rideId)
    }

    @Test
    fun `a date near midnight maps using the supplied zone, not UTC implicitly`() {
        // 2026-01-01 23:30 UTC is still 2026-01-01 in a UTC-based zone, but would be
        // 2026-01-02 in a positive-offset zone — this pins the zone parameter actually
        // being used rather than the mapper silently defaulting to the system zone.
        val epochMs = LocalDate.of(2026, 1, 1).atTime(23, 30).toInstant(ZoneOffset.UTC).toEpochMilli()
        val ride = rideAt(LocalDate.of(2026, 1, 1)).copy(startEpochMs = epochMs)

        val rowInUtc = RideHistoryMapper.map(ride, ZoneOffset.UTC)
        val rowAheadOfUtc = RideHistoryMapper.map(ride, ZoneId.of("Asia/Tokyo")) // UTC+9

        assertEquals("Jan 1, 2026", rowInUtc.dateLabel)
        assertEquals("Jan 2, 2026", rowAheadOfUtc.dateLabel)
    }
}
