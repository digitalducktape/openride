package dev.digitalducktape.openride

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import dev.digitalducktape.openride.core.content.YouTubeContentRepository
import dev.digitalducktape.openride.core.data.OpenRideDatabase
import dev.digitalducktape.openride.core.data.ProfileRepository
import dev.digitalducktape.openride.core.data.RideRepository
import dev.digitalducktape.openride.core.profile.ActiveProfileHolder
import dev.digitalducktape.openride.core.ride.RideSessionManager
import dev.digitalducktape.openride.core.sensor.BikeDataSource
import dev.digitalducktape.openride.core.sensor.MockBikeDataSource
import dev.digitalducktape.openride.core.sensor.PelotonBikeDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Simple hand-rolled dependency container (no Hilt/DI framework per project scope), so
 * [MainActivity] and Compose screens can pull their dependencies from one place via
 * constructor injection.
 *
 * [bikeDataSource] is [MockBikeDataSource] by default; [BuildConfig.USE_REAL_BIKE_SENSOR]
 * (default `false`) switches it to [PelotonBikeDataSource] — the real Gen 2 system-service
 * binding from T3/#3, which is unverified on actual hardware (see that class's doc). Every
 * other layer only depends on the [BikeDataSource] interface, so this toggle is the one
 * place the choice is made.
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
        if (BuildConfig.USE_REAL_BIKE_SENSOR) {
            PelotonBikeDataSource(applicationContext).also { it.start() }
        } else {
            MockBikeDataSource(scope = containerScope)
        }
    }

    val rideSessionManager: RideSessionManager by lazy {
        RideSessionManager(
            bikeDataSource = bikeDataSource,
            rideRepository = rideRepository,
            scope = containerScope,
        )
    }

    /** Scopes the session to whichever rider is currently selected (PRD P0-3). */
    val activeProfileHolder: ActiveProfileHolder by lazy {
        ActiveProfileHolder(applicationContext)
    }

    /** Curated YouTube-channel content for the Classes browser (PRD P0-6, T9/T10). */
    val contentRepository: YouTubeContentRepository by lazy {
        YouTubeContentRepository(applicationContext)
    }
}

/**
 * Builds a [ViewModelProvider.Factory] from a plain lambda, so screens can construct their
 * view models straight from [AppContainer] dependencies (manual DI, no Hilt) while still
 * getting normal [ViewModel] lifecycle/state-retention behavior from Compose Navigation's
 * per-destination [androidx.lifecycle.ViewModelStoreOwner].
 */
fun <T : ViewModel> viewModelFactory(create: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
    }
