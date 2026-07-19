package dev.digitalducktape.openride.ui.ride

import androidx.lifecycle.ViewModel
import dev.digitalducktape.openride.core.data.ProfileRepository
import dev.digitalducktape.openride.core.data.Ride
import dev.digitalducktape.openride.core.heartrate.HeartRateManager
import dev.digitalducktape.openride.core.profile.ActiveProfileHolder
import dev.digitalducktape.openride.core.ride.LiveAggregates
import dev.digitalducktape.openride.core.ride.PowerZone
import dev.digitalducktape.openride.core.ride.RideGoal
import dev.digitalducktape.openride.core.ride.RideSessionManager
import dev.digitalducktape.openride.core.ride.RideSessionState
import dev.digitalducktape.openride.core.sensor.BikeDataSource
import dev.digitalducktape.openride.core.sensor.BikeMetrics
import dev.digitalducktape.openride.core.sensor.ConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan

/**
 * Combined snapshot the in-ride screen renders from. Kept as one immutable value (rather
 * than the screen collecting five separate flows) so a single [combine] is the only place
 * that has to reason about how the pieces line up.
 *
 * @param connectionState anything other than [ConnectionState.Connected] means the screen
 *   must show the "Sensors not detected" banner and blank tiles (PRD P0-9) — never a fake
 *   zero reading.
 * @param goal the rider's pre-ride target (PRD P1-3), or [RideGoal.None].
 * @param ftp the active rider's FTP in watts, or `null` if not set — drives [currentZone].
 * @param heartRateBpm live bpm from the active profile's paired strap (PRD P1-4, T17), or
 *   `null` if either no strap is paired or it hasn't delivered a reading yet.
 * @param heartRatePaired whether the active profile has a strap paired at all — distinct
 *   from [heartRateBpm] being non-null, since a paired-but-not-yet-connected strap should
 *   still show its tile (as "--"), not hide it entirely.
 * @param heartRateConnectionState the paired strap's live connection state, `Unavailable` if
 *   none is paired.
 */
data class InRideUiState(
    val elapsedSec: Int = 0,
    val sessionState: RideSessionState = RideSessionState.Idle,
    val metrics: BikeMetrics = BikeMetrics.ZERO,
    val aggregates: LiveAggregates = LiveAggregates(),
    val connectionState: ConnectionState = ConnectionState.Connected,
    val distanceMiles: Double = 0.0,
    val goal: RideGoal = RideGoal.None,
    val ftp: Int? = null,
    val heartRateBpm: Int? = null,
    val heartRatePaired: Boolean = false,
    val heartRateConnectionState: ConnectionState = ConnectionState.Unavailable,
    val autoPaused: Boolean = false,
) {
    val sensorsAvailable: Boolean get() = connectionState == ConnectionState.Connected
    val isPaused: Boolean get() = sessionState == RideSessionState.Paused

    /** Whether the BPM tile should be shown at all (PRD P1-4, T17) — only once a strap is
     *  paired for the active rider, regardless of whether it's currently connected. */
    val heartRateTileVisible: Boolean get() = heartRatePaired

    /** Whether the paired strap is actually live right now (vs. paired but not yet connected). */
    val heartRateConnected: Boolean get() = heartRateConnectionState == ConnectionState.Connected

    /**
     * Live running output in kilojoules. `avgPower * elapsedSec` reconstructs the summed
     * joules exactly (avgPower is already `sum / elapsedSec`), matching
     * [RideSessionManager.stop]'s own `outputKj` math without needing a second running sum
     * threaded through the manager.
     */
    val liveOutputKj: Double get() = aggregates.avgPower * elapsedSec / 1000.0

    /** The live power zone (PRD P1-3), or `null` while sensors are down or FTP is unset. */
    val currentZone: PowerZone? get() = if (sensorsAvailable) PowerZone.forPower(metrics.powerWatts, ftp) else null

    /**
     * Fractional progress (0.0-1.0, clamped) toward [goal], or `null` when no goal is set or
     * the goal's target isn't a usable positive value.
     */
    val goalProgress: Double?
        get() = when (val g = goal) {
            RideGoal.None -> null
            is RideGoal.Duration -> (g.targetSec.takeIf { it > 0 })
                ?.let { (elapsedSec.toDouble() / it).coerceIn(0.0, 1.0) }
            is RideGoal.Output -> (g.targetKj.takeIf { it > 0.0 })
                ?.let { (liveOutputKj / it).coerceIn(0.0, 1.0) }
        }
}

/**
 * In-ride screen (PRD P0-7/P0-9/P0-10/P1-3): the core screen, showing live cadence/resistance/
 * output tiles, the elapsed timer, live power-zone and goal progress, and pause/end controls,
 * sourced from [RideSessionManager] (timer/aggregates/goal), [BikeDataSource] (live sensor
 * feed + connectivity), and the active rider's FTP (for power-zone display).
 */
class InRideViewModel(
    private val rideSessionManager: RideSessionManager,
    private val bikeDataSource: BikeDataSource,
    profileRepository: ProfileRepository,
    activeProfileHolder: ActiveProfileHolder,
    private val heartRateManager: HeartRateManager? = null,
) : ViewModel() {
    // profileRepository.observeProfiles() is backed by a Room @Query Flow, which (unlike the
    // rest of this view model's plain in-memory StateFlows) doesn't necessarily emit its first
    // value synchronously on collection. onStart seeds an empty list immediately so the
    // combine below still emits right away (ftp = null until the real query result lands),
    // matching every other input to this screen's uiState combine.
    private val ftpFlow: Flow<Int?> = combine(
        activeProfileHolder.activeProfileId,
        profileRepository.observeProfiles().onStart { emit(emptyList()) },
    ) { id, profiles -> profiles.firstOrNull { it.id == id }?.ftp }

    private val coreFlow: Flow<CoreRideFlow> = combine(
        rideSessionManager.elapsedSec,
        rideSessionManager.state,
        bikeDataSource.metrics,
        rideSessionManager.liveAggregates,
        bikeDataSource.connectionState,
    ) { elapsedSec, sessionState, metrics, aggregates, connectionState ->
        CoreRideFlow(elapsedSec, sessionState, metrics, aggregates, connectionState)
    }

    // Live heart rate from the active profile's paired strap (PRD P1-4, T17). When no manager
    // is wired (tests, or a build without the BLE layer), this is a single static "no strap"
    // snapshot so the combine below still emits and the HR tile simply stays hidden.
    private val heartRateFlow: Flow<HeartRateSnapshot> =
        if (heartRateManager == null) {
            flowOf(HeartRateSnapshot(bpm = null, paired = false, state = ConnectionState.Unavailable))
        } else {
            combine(
                heartRateManager.bpm,
                heartRateManager.connectionState,
                heartRateManager.connectedAddress,
            ) { bpm, state, address ->
                HeartRateSnapshot(bpm = bpm, paired = address != null, state = state)
            }
        }

    val uiState: Flow<InRideUiState> = combine(
        coreFlow,
        rideSessionManager.goal,
        ftpFlow,
        heartRateFlow,
        rideSessionManager.autoPaused,
    ) { core, goal, ftp, hr, autoPaused ->
        InRideUiState(
            elapsedSec = core.elapsedSec,
            sessionState = core.sessionState,
            metrics = core.metrics,
            aggregates = core.aggregates,
            connectionState = core.connectionState,
            goal = goal,
            ftp = ftp,
            heartRateBpm = hr.bpm,
            heartRatePaired = hr.paired,
            heartRateConnectionState = hr.state,
            autoPaused = autoPaused,
        )
    }.scan(InRideUiState()) { previous, next ->
        // Distance has no dedicated field on BikeMetrics/RideSample (PRD only requires
        // cadence/resistance/power to be persisted) — it's accumulated here purely for
        // display, one elapsedSec tick at a time, and only while the ride is actually
        // advancing (never during Paused/Idle, and never double-counted across ticks).
        val tickedForward = next.elapsedSec > previous.elapsedSec
        val addedMiles = if (tickedForward && next.sessionState == RideSessionState.Active) {
            val deltaSec = next.elapsedSec - previous.elapsedSec
            next.metrics.speedMph / 3600.0 * deltaSec
        } else {
            0.0
        }
        next.copy(distanceMiles = previous.distanceMiles + addedMiles)
    }

    fun pause() = rideSessionManager.pause()

    fun resume() = rideSessionManager.resume()

    /**
     * Stops the ride and persists it. Deliberately does *not* call
     * [RideSessionManager.reset] — that happens when the summary screen this navigates to is
     * dismissed, so [RideSessionState.Finished] survives long enough for that screen (T8) to
     * rely on it if needed.
     */
    suspend fun endRide(): Ride? = rideSessionManager.stop()

    private data class CoreRideFlow(
        val elapsedSec: Int,
        val sessionState: RideSessionState,
        val metrics: BikeMetrics,
        val aggregates: LiveAggregates,
        val connectionState: ConnectionState,
    )

    private data class HeartRateSnapshot(
        val bpm: Int?,
        val paired: Boolean,
        val state: ConnectionState,
    )
}
