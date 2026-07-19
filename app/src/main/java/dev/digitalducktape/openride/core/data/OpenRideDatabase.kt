package dev.digitalducktape.openride.core.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Profile::class, Ride::class, RideSample::class],
    version = 1,
    exportSchema = true,
)
abstract class OpenRideDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun rideDao(): RideDao

    companion object {
        const val DATABASE_NAME = "openride.db"
    }
}
