package dev.digitalducktape.openride.core.route

import java.io.InputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the currently-loaded GPX route (PRD #21/T21), if any, so the in-ride screen can overlay
 * grade/progress against it. In-memory only and app-wide (a route is a "ride with this now"
 * choice, not per-profile persisted state) — same lightweight single-holder shape as
 * [dev.digitalducktape.openride.core.profile.ActiveProfileHolder], minus the persistence.
 */
class RouteHolder(private val parser: GpxParser = GpxParser()) {

    private val _activeRoute = MutableStateFlow<Route?>(null)
    val activeRoute: StateFlow<Route?> = _activeRoute.asStateFlow()

    /**
     * Parses [input] as GPX and makes it the active route. Returns the loaded [Route] on
     * success, or `null` if the file had fewer than two usable points (nothing to simulate
     * along) — in which case the active route is left unchanged.
     */
    fun load(input: InputStream): Route? {
        val route = parser.parse(input)
        if (!route.isRideable) return null
        _activeRoute.value = route
        return route
    }

    /** Clears the active route (back to a plain ride with no route overlay). */
    fun clear() {
        _activeRoute.value = null
    }
}
