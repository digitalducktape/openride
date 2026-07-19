package dev.digitalducktape.openride

import android.content.Context
import androidx.room.Room
import dev.digitalducktape.openride.core.data.OpenRideDatabase
import dev.digitalducktape.openride.core.data.ProfileRepository
import dev.digitalducktape.openride.core.data.RideRepository

/**
 * Simple hand-rolled dependency container (no Hilt/DI framework per project scope).
 *
 * Later tickets will extend this to construct and expose the real singletons the app
 * needs (BikeDataSource, RideSessionManager, etc.) so that [MainActivity] and Compose
 * screens can pull their dependencies from one place via constructor injection.
 */
class AppContainer(private val applicationContext: Context) {
    private val database: OpenRideDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            OpenRideDatabase::class.java,
            OpenRideDatabase.DATABASE_NAME,
        ).build()
    }

    val profileRepository: ProfileRepository by lazy {
        ProfileRepository(database.profileDao())
    }

    val rideRepository: RideRepository by lazy {
        RideRepository(database, database.rideDao())
    }

    // Populated by later ticket (T5 ride engine).
}
