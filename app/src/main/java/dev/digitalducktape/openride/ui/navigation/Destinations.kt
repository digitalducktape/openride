package dev.digitalducktape.openride.ui.navigation

/**
 * Route constants for the app's outer [androidx.navigation.NavHost] (see
 * [OpenRideNavHost]). [Main] itself hosts a second, nested `NavHost` for the tabbed
 * Home/Classes/History/Profile destinations (see `ui.main.MainScaffold`) — it is one outer
 * destination so starting/ending a ride and switching riders can cleanly push/pop the whole
 * tabbed section without the side rail bleeding into screens where it doesn't belong.
 */
object Destinations {
    const val ProfileSelect = "profile_select"
    const val ProfileCreate = "profile_create"
    const val Main = "main"
    const val InRide = "in_ride"

    /** BLE heart-rate strap pairing screen (PRD P1-4, T17), reachable from the Profile tab. */
    const val HrPairing = "hr_pairing"

    private const val RideSummaryBase = "ride_summary"
    const val RideIdArg = "rideId"
    const val RideSummary = "$RideSummaryBase/{$RideIdArg}"

    fun rideSummary(rideId: Long) = "$RideSummaryBase/$rideId"
}

/** Nested-tab routes inside [Destinations.Main]'s inner `NavHost`. */
object MainTabs {
    const val Home = "home"
    const val Classes = "classes"
    const val History = "history"
    const val Profile = "profile"
}
