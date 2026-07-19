package dev.digitalducktape.openride.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A household rider. Every [Ride] is scoped to exactly one profile (PRD P0-3: multi-user
 * profiles are a first-class requirement, not an afterthought).
 *
 * @param weightKg optional, used for calorie estimation / export accuracy (P1-1, P1-3)
 * @param ftp optional functional threshold power in watts, used for power-zone display (P1-3)
 */
@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val avatarEmoji: String,
    val avatarColor: Int,
    val weightKg: Double?,
    val ftp: Int?,
)
