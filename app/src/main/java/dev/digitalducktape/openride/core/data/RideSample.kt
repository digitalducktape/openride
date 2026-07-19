package dev.digitalducktape.openride.core.data

import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * One second of a ride's live metrics. The composite primary key (rideId, tSec) both
 * uniquely identifies a sample and doubles as the index used by the per-ride, time-ordered
 * history query — no separate index is needed since `rideId` is the leading PK column.
 *
 * @param tSec seconds elapsed since the ride started (0-based)
 * @param heartRateBpm optional live heart rate at this second (PRD P1-4, T17), from a paired
 *   BLE strap. Added in schema version 2 ([dev.digitalducktape.openride.core.data.MIGRATION_1_2]);
 *   `null` both for samples recorded before T17 and for any ride where no strap was paired.
 */
@Entity(
    tableName = "ride_samples",
    primaryKeys = ["rideId", "tSec"],
    foreignKeys = [
        ForeignKey(
            entity = Ride::class,
            parentColumns = ["id"],
            childColumns = ["rideId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RideSample(
    val rideId: Long,
    val tSec: Int,
    val cadence: Int,
    val resistance: Int,
    val power: Int,
    val heartRateBpm: Int? = null,
)
