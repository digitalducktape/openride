package dev.digitalducktape.openride.core.ride

import dev.digitalducktape.openride.core.data.Ride
import dev.digitalducktape.openride.core.data.RideRepository
import dev.digitalducktape.openride.core.data.RideSample
import dev.digitalducktape.openride.core.sensor.BikeDataSource
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A no-op heart-rate source: no strap paired, so every sample records a `null` bpm. */
private val NO_HEART_RATE: StateFlow<Int?> = MutableStateFlow(null).asStateFlow()

/**
 * Drives a single ride from start to finish: `Idle -> Active <-> Paused -> Finished`.
 *
 * While [RideSessionState.Active], an internal 1 Hz coroutine ticker (using the same
 * "tick via `delay`, driven by an injected [CoroutineScope]" pattern as
 * [dev.digitalducktape.openride.core.sensor.MockBikeDataSource], so tests can drive it with
 * virtual time) both advances [elapsedSec] and samples [BikeDataSource.metrics] into an
 * in-memory buffer. Pausing cancels the ticker so neither the elapsed timer nor the sample
 * buffer advance; resuming starts a fresh ticker that continues exactly where the old one
 * left off — no time or samples are lost or duplicated across a pause/resume cycle.
 *
 * [stop] computes final aggregates from the buffer and persists the ride + full sample
 * series via [RideRepository] in one transaction, then transitions to
 * [RideSessionState.Finished].
 *
 * @param scope coroutine scope the 1 Hz ticker runs on. Pass a `TestScope`'s
 *   `backgroundScope` in tests to drive the simulation with virtual time.
 * @param heartRateBpm live bpm from whichever strap is currently paired (PRD P1-4, T17) —
 *   sampled into each [RideSample.heartRateBpm] alongside cadence/resistance/power. Defaults
 *   to a source that's always `null` (no strap), so every existing call site that doesn't
 *   care about heart rate is unaffected. Production wiring passes
 *   [dev.digitalducktape.openride.core.heartrate.HeartRateManager.bpm]; tests can pass a
 *   plain `MutableStateFlow<Int?>`.
 * @param epochMillisProvider supplies the ride's start wall-clock time; injectable so tests
 *   don't depend on real time.
 */
class RideSessionManager(
    private val bikeDataSource: BikeDataSource,
    private val rideRepository: RideRepository,
    private val scope: CoroutineScope,
    private val heartRateBpm: StateFlow<Int?> = NO_HEART_RATE,
    private val epochMillisProvider: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow<RideSessionState>(RideSessionState.Idle)
    val state: StateFlow<RideSessionState> = _state.asStateFlow()

    private val _elapsedSec = MutableStateFlow(0)
    val elapsedSec: StateFlow<Int> = _elapsedSec.asStateFlow()

    private val _liveAggregates = MutableStateFlow(LiveAggregates())
    val liveAggregates: StateFlow<LiveAggregates> = _liveAggregates.asStateFlow()

    private val _goal = MutableStateFlow<RideGoal>(RideGoal.None)
    /** The rider's pre-ride target (PRD P1-3), if any. Set via [setGoal] before [start]. */
    val goal: StateFlow<RideGoal> = _goal.asStateFlow()

    /**
     * `true` only while [RideSessionState.Active] — a UI-agnostic signal the activity maps
     * to `FLAG_KEEP_SCREEN_ON` (PRD P0-10). Deliberately `false` while
     * [RideSessionState.Paused]: the "screen flag set only while ride active" acceptance
     * criterion is about the Active state specifically.
     */
    private val _isRideActive = MutableStateFlow(false)
    val isRideActive: StateFlow<Boolean> = _isRideActive.asStateFlow()

    private var profileId: Long = 0
    private var rideStartEpochMs: Long = 0
    private var tickerJob: Job? = null
    private val sampleBuffer = mutableListOf<RideSample>()

    private var sumCadence: Long = 0
    private var sumPower: Long = 0
    private var sumResistance: Long = 0
    private var maxCadenceSeen: Int = 0
    private var maxPowerSeen: Int = 0

    /** Starts a new ride for [profileId]. No-op if not currently [RideSessionState.Idle]. */
    fun start(profileId: Long) {
        if (_state.value != RideSessionState.Idle) return

        this.profileId = profileId
        rideStartEpochMs = epochMillisProvider()
        sampleBuffer.clear()
        sumCadence = 0
        sumPower = 0
        sumResistance = 0
        maxCadenceSeen = 0
        maxPowerSeen = 0
        _elapsedSec.value = 0
        _liveAggregates.value = LiveAggregates()

        _state.value = RideSessionState.Active
        _isRideActive.value = true
        startTicker()
    }

    /**
     * Sets the rider's pre-ride goal (PRD P1-3). Only takes effect while
     * [RideSessionState.Idle] — a goal is a pre-ride decision, not something that changes
     * mid-ride — so this is a no-op once [start] has moved past Idle.
     */
    fun setGoal(goal: RideGoal) {
        if (_state.value != RideSessionState.Idle) return
        _goal.value = goal
    }

    /** Pauses timer/sampling. No-op if not currently [RideSessionState.Active]. */
    fun pause() {
        if (_state.value != RideSessionState.Active) return

        tickerJob?.cancel()
        tickerJob = null
        _state.value = RideSessionState.Paused
        _isRideActive.value = false
    }

    /** Resumes timer/sampling from where it left off. No-op unless [RideSessionState.Paused]. */
    fun resume() {
        if (_state.value != RideSessionState.Paused) return

        _state.value = RideSessionState.Active
        _isRideActive.value = true
        startTicker()
    }

    /**
     * Stops the ride, computes final aggregates, and persists the ride + samples in one
     * transaction (via [RideRepository.saveRide]). Returns the persisted [Ride], or `null`
     * if called outside [RideSessionState.Active]/[RideSessionState.Paused].
     */
    suspend fun stop(): Ride? {
        val current = _state.value
        if (current !is RideSessionState.Active && current !is RideSessionState.Paused) return null

        tickerJob?.cancel()
        tickerJob = null
        _isRideActive.value = false

        val aggregates = _liveAggregates.value
        // Each sample represents one second, so power (watts = joules/sec) summed across
        // samples is total joules; /1000 converts to kilojoules.
        val outputKj = sampleBuffer.sumOf { it.power } / 1000.0
        // Common indoor-cycling approximation: kcal ~= kJ x 0.96 (roughly accounting for
        // human mechanical efficiency without needing rider weight for this formula).
        val calories = (outputKj * KCAL_PER_KJ).roundToInt()

        val ride = Ride(
            profileId = profileId,
            startEpochMs = rideStartEpochMs,
            durationSec = _elapsedSec.value,
            avgCadence = aggregates.avgCadence,
            maxCadence = aggregates.maxCadence,
            avgPower = aggregates.avgPower,
            maxPower = aggregates.maxPower,
            avgResistance = aggregates.avgResistance,
            outputKj = outputKj,
            calories = calories,
        )

        val rideId = rideRepository.saveRide(ride, sampleBuffer.toList())
        val savedRide = ride.copy(id = rideId)
        _state.value = RideSessionState.Finished(savedRide)
        return savedRide
    }

    /** Returns to [RideSessionState.Idle], ready for another ride. No-op unless [RideSessionState.Finished]. */
    fun reset() {
        if (_state.value !is RideSessionState.Finished) return

        _elapsedSec.value = 0
        _liveAggregates.value = LiveAggregates()
        sampleBuffer.clear()
        _goal.value = RideGoal.None
        _state.value = RideSessionState.Idle
    }

    private fun startTicker() {
        tickerJob = scope.launch {
            while (isActive) {
                delay(TICK_INTERVAL_MS)

                val newElapsed = _elapsedSec.value + 1
                _elapsedSec.value = newElapsed

                val metrics = bikeDataSource.metrics.value
                sampleBuffer.add(
                    RideSample(
                        rideId = 0L, // resolved to the real generated id by RideRepository.saveRide
                        tSec = newElapsed - 1,
                        cadence = metrics.cadenceRpm,
                        resistance = metrics.resistancePercent,
                        power = metrics.powerWatts,
                        heartRateBpm = heartRateBpm.value,
                    ),
                )

                sumCadence += metrics.cadenceRpm
                sumPower += metrics.powerWatts
                sumResistance += metrics.resistancePercent
                maxCadenceSeen = max(maxCadenceSeen, metrics.cadenceRpm)
                maxPowerSeen = max(maxPowerSeen, metrics.powerWatts)

                val n = sampleBuffer.size
                _liveAggregates.value = LiveAggregates(
                    avgCadence = (sumCadence / n).toInt(),
                    maxCadence = maxCadenceSeen,
                    avgPower = (sumPower / n).toInt(),
                    maxPower = maxPowerSeen,
                    avgResistance = (sumResistance / n).toInt(),
                )
            }
        }
    }

    companion object {
        private const val TICK_INTERVAL_MS = 1000L
        private const val KCAL_PER_KJ = 0.96
    }
}
