package dev.digitalducktape.openride

import android.content.Context
import androidx.room.Room
import dev.digitalducktape.openride.core.data.OpenRideDatabase
import dev.digitalducktape.openride.core.data.ProfileRepository
import dev.digitalducktape.openride.core.data.RideRepository
import dev.digitalducktape.openride.core.ride.RideSessionManager
import dev.digitalducktape.openride.core.sensor.BikeDataSource
import dev.digitalducktape.openride.core.sensor.MockBikeDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Simple hand-rolled dependency container (no Hilt/DI framework per project scope), so
 * [MainActivity] and Compose screens can pull their dependencies from one place via
 * constructor injection.
 *
 * [bikeDataSource] is the [MockBikeDataSource] until T3 (binding the real Gen 2 system
 * service) lands — everything above the [BikeDataSource] interface is already wired
 * against the abstraction, so swapping it in later is a one-line change here.
 */
class AppContainer(private val applicationContext: Context) {
    /** Long-lived scope for singletons that need to run coroutines outside any one screen's lifecycle. */
    private val containerScope = CoroutineScope(SupervisorJob())

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

    val bikeDataSource: BikeDataSource by lazy {
        MockBikeDataSource(scope = containerScope)
    }

    val rideSessionManager: RideSessionManager by lazy {
        RideSessionManager(
            bikeDataSource = bikeDataSource,
            rideRepository = rideRepository,
            scope = containerScope,
        )
    }
}
