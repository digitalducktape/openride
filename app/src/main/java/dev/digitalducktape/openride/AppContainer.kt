package dev.digitalducktape.openride

import android.content.Context

/**
 * Simple hand-rolled dependency container (no Hilt/DI framework per project scope).
 *
 * Later tickets will extend this to construct and expose the real singletons the app
 * needs (Room database, repositories, BikeDataSource, RideSessionManager, etc.) so
 * that [MainActivity] and Compose screens can pull their dependencies from one place
 * via constructor injection.
 */
class AppContainer(private val applicationContext: Context) {
    // Populated by later tickets (T2 sensor abstraction, T4 Room data layer, T5 ride engine).
}
