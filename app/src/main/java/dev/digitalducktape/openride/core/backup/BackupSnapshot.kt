package dev.digitalducktape.openride.core.backup

import dev.digitalducktape.openride.core.data.Profile
import dev.digitalducktape.openride.core.data.Ride
import dev.digitalducktape.openride.core.data.RideSample
import kotlinx.serialization.Serializable

/**
 * Serializable mirrors of the Room entities (PRD P1-8), kept as separate types rather than
 * annotating [Profile]/[Ride]/[RideSample] themselves with `@Serializable` — that keeps the
 * backup file's shape decoupled from Room's own annotations, so a future schema change on one
 * side (e.g. a Room migration) doesn't silently change the other.
 */
@Serializable
data class ProfileBackup(
    val id: Long,
    val name: String,
    val avatarEmoji: String,
    val avatarColor: Int,
    val weightKg: Double?,
    val ftp: Int?,
    val pairedHrDeviceAddress: String? = null,
)

@Serializable
data class RideBackup(
    val id: Long,
    val profileId: Long,
    val startEpochMs: Long,
    val durationSec: Int,
    val avgCadence: Int,
    val maxCadence: Int,
    val avgPower: Int,
    val maxPower: Int,
    val avgResistance: Int,
    val outputKj: Double,
    val calories: Int?,
)

@Serializable
data class RideSampleBackup(
    val rideId: Long,
    val tSec: Int,
    val cadence: Int,
    val resistance: Int,
    val power: Int,
    val heartRateBpm: Int? = null,
)

fun Profile.toBackup() = ProfileBackup(id, name, avatarEmoji, avatarColor, weightKg, ftp, pairedHrDeviceAddress)
fun ProfileBackup.toEntity() = Profile(id, name, avatarEmoji, avatarColor, weightKg, ftp, pairedHrDeviceAddress)

fun Ride.toBackup() = RideBackup(
    id, profileId, startEpochMs, durationSec, avgCadence, maxCadence, avgPower, maxPower, avgResistance, outputKj, calories,
)
fun RideBackup.toEntity() = Ride(
    id, profileId, startEpochMs, durationSec, avgCadence, maxCadence, avgPower, maxPower, avgResistance, outputKj, calories,
)

fun RideSample.toBackup() = RideSampleBackup(rideId, tSec, cadence, resistance, power, heartRateBpm)
fun RideSampleBackup.toEntity() = RideSample(rideId, tSec, cadence, resistance, power, heartRateBpm)

/**
 * A full-database snapshot (PRD P1-8): every profile, ride, and per-second sample, plus a
 * [version] field so a future format change can be detected and handled deliberately rather
 * than silently misparsed (PRD's explicit "version field in the format" requirement).
 */
@Serializable
data class BackupSnapshot(
    val version: Int = CURRENT_VERSION,
    val exportedAtEpochMs: Long,
    val profiles: List<ProfileBackup>,
    val rides: List<RideBackup>,
    val samples: List<RideSampleBackup>,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
