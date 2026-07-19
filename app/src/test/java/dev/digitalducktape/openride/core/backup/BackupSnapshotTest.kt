package dev.digitalducktape.openride.core.backup

import dev.digitalducktape.openride.core.data.Profile
import dev.digitalducktape.openride.core.data.Ride
import dev.digitalducktape.openride.core.data.RideSample
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupSnapshotTest {

    // encodeDefaults = true, matching BackupRepository's own Json config — the plain default
    // Json instance omits properties that equal their declared default (version here), which
    // would make version-field assertions pass or fail based on serializer config rather than
    // on what BackupRepository.exportJson() actually produces.
    private val json = Json { encodeDefaults = true }

    @Test
    fun `Profile toBackup and back to entity is lossless`() {
        val profile = Profile(id = 7L, name = "Ed", avatarEmoji = "🚴", avatarColor = 0xFF00AAFF.toInt(), weightKg = 80.5, ftp = 220)

        assertEquals(profile, profile.toBackup().toEntity())
    }

    @Test
    fun `Profile with a paired HR device address round-trips`() {
        val profile = Profile(
            id = 2L,
            name = "Ed",
            avatarEmoji = "🚴",
            avatarColor = 0xFF00AAFF.toInt(),
            weightKg = null,
            ftp = null,
            pairedHrDeviceAddress = "AA:BB:CC:DD:EE:FF",
        )

        assertEquals(profile, profile.toBackup().toEntity())
    }

    @Test
    fun `RideSample with a heart rate reading round-trips`() {
        val sample = RideSample(rideId = 1L, tSec = 5, cadence = 90, resistance = 50, power = 180, heartRateBpm = 142)

        assertEquals(sample, sample.toBackup().toEntity())
    }

    @Test
    fun `Profile with null optional fields round-trips`() {
        val profile = Profile(id = 1L, name = "Kid", avatarEmoji = "🤖", avatarColor = 0xFFAA0000.toInt(), weightKg = null, ftp = null)

        assertEquals(profile, profile.toBackup().toEntity())
    }

    @Test
    fun `Ride toBackup and back to entity is lossless`() {
        val ride = Ride(
            id = 3L,
            profileId = 7L,
            startEpochMs = 1_700_000_000_000L,
            durationSec = 1800,
            avgCadence = 85,
            maxCadence = 100,
            avgPower = 150,
            maxPower = 300,
            avgResistance = 45,
            outputKj = 270.0,
            calories = 260,
        )

        assertEquals(ride, ride.toBackup().toEntity())
    }

    @Test
    fun `RideSample toBackup and back to entity is lossless`() {
        val sample = RideSample(rideId = 3L, tSec = 42, cadence = 90, resistance = 50, power = 180)

        assertEquals(sample, sample.toBackup().toEntity())
    }

    @Test
    fun `BackupSnapshot serializes with an explicit version field`() {
        val encoded = json.encodeToString(
            BackupSnapshot.serializer(),
            BackupSnapshot(exportedAtEpochMs = 123L, profiles = emptyList(), rides = emptyList(), samples = emptyList()),
        )

        assertEquals(true, encoded.contains("\"version\":${BackupSnapshot.CURRENT_VERSION}"))
    }
}
