package dev.digitalducktape.openride.core.sensor

import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over the bike's live sensor feed (cadence/resistance/power/speed).
 *
 * Kept as a narrow interface so the real implementation (binding the Gen 2 tablet's
 * undocumented system service, per the grupetto technique — see T3) and
 * [MockBikeDataSource] (used for all UI/logic development in a standard emulator, per
 * the PRD's risk mitigation) are interchangeable everywhere else in the app.
 */
interface BikeDataSource {
    /** Latest sensor reading. Consumers sample this at whatever cadence they need. */
    val metrics: StateFlow<BikeMetrics>

    /** Whether the sensor feed is currently reachable. */
    val connectionState: StateFlow<ConnectionState>
}
