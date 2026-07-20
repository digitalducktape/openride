package dev.digitalducktape.openride.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Profile::class, Ride::class, RideSample::class],
    version = 4,
    exportSchema = true,
)
abstract class OpenRideDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun rideDao(): RideDao

    companion object {
        const val DATABASE_NAME = "openride.db"
    }
}

/**
 * Adds BLE heart-rate support (PRD P1-4, T17): a per-rider paired-strap address on
 * [Profile], and a per-second optional heart rate reading on [RideSample]. Both are nullable
 * columns with no default-value backfill needed — every existing row simply reads back
 * `null` (no strap paired / no HR recorded), which is exactly the "not paired yet" state for
 * data that predates this feature.
 */
val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE profiles ADD COLUMN pairedHrDeviceAddress TEXT")
        db.execSQL("ALTER TABLE ride_samples ADD COLUMN heartRateBpm INTEGER")
    }
}

/**
 * Records which class a ride played ([Ride.videoId], v2 in-app player) so the Classes tab
 * can badge already-taken videos. Nullable with no backfill: every pre-existing ride —
 * including v1 rides that launched the YouTube app, where the app never knew when playback
 * ended — reads back `null`, i.e. "no class attached".
 */
val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE rides ADD COLUMN videoId TEXT")
    }
}

/**
 * Adds camera profile photos ([Profile.avatarPhotoPath]): the absolute path of the rider's
 * cropped avatar photo on disk. Nullable with no backfill — every existing profile reads back
 * `null`, i.e. "still using the emoji avatar".
 */
val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE profiles ADD COLUMN avatarPhotoPath TEXT")
    }
}
