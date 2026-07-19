package dev.digitalducktape.openride.core.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A completed (or in-progress-being-saved) ride's aggregate summary, scoped to the profile
 * that rode it. The full per-second series lives in [RideSample] rows sharing this ride's
 * [id] — required for later FIT/TCX export and ride graphs (PRD P0-5) without having to
 * re-derive it from aggregates alone.
 *
 * @param outputKj total mechanical output in kilojoules (Σ power over the ride / 1000)
 * @param calories estimated calorie burn; null when it can't be estimated
 */
@Entity(
    tableName = "rides",
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId")],
)
data class Ride(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
