package dev.digitalducktape.openride.core.sensor

import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Simulated [BikeDataSource] driving a plausible, ever-progressing solo ride so that all
 * UI/logic development can happen in a standard Android emulator without the physical
 * bike (see PRD risk: "Development requires physical access to the bike").
 *
 * The simulated ride has three phases, keyed off seconds elapsed since this instance was
 * created:
 *  1. **Warmup** (0..[WARMUP_END_SEC]s) — cadence/resistance ramp up gently from resting.
 *  2. **Intervals** ([WARMUP_END_SEC]..[COOLDOWN_START_SEC]s) — repeating high-effort /
 *     recovery cycles ([INTERVAL_HIGH_SEC]s hard, [INTERVAL_RECOVERY_SEC]s easy).
 *  3. **Cooldown** ([COOLDOWN_START_SEC]s onward) — tapers back down over
 *     [COOLDOWN_TAPER_SEC]s and then holds at a steady easy pace for as long as the ride
 *     continues (real ride length is controlled by the rider via [dev.digitalducktape.openride.core.ride.RideSessionManager], not by this class).
 *
 * A new [BikeMetrics] value is published once per second (1 Hz), matching the PRD's "update
 * within 1 second" acceptance criterion for live metrics (P0-2).
 *
 * @param scope coroutine scope the internal 1 Hz ticker (and dropout timers) run on. Pass a
 *   `TestScope`'s `backgroundScope` (or any scope backed by a [kotlinx.coroutines.test.TestDispatcher])
 *   in tests to drive the simulation with virtual time.
 * @param random source of the small jitter applied to cadence/resistance each tick, injectable
 *   for deterministic tests.
 */
class MockBikeDataSource(
    private val scope: CoroutineScope,
    private val random: Random = Random.Default,
) : BikeDataSource {

    private val _metrics = MutableStateFlow(BikeMetrics.ZERO)
    override val metrics: StateFlow<BikeMetrics> = _metrics.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var elapsedSec = 0
    private var dropoutJob: Job? = null

    private val tickJob: Job = scope.launch {
        while (isActive) {
            delay(TICK_INTERVAL_MS)
            elapsedSec++
            _metrics.value = computeMetrics(elapsedSec)
        }
    }

    /**
     * Simulates the sensor feed dropping out for [seconds]: [connectionState] flips to
     * [ConnectionState.Disconnected] immediately and reverts to [ConnectionState.Connected]
     * after [seconds] elapse. Drives the sensor-failure UI state (PRD P0-9). Calling this
     * again while a dropout is already in progress restarts the dropout window.
     */
    fun simulateDropout(seconds: Int) {
        dropoutJob?.cancel()
        _connectionState.value = ConnectionState.Disconnected
        dropoutJob = scope.launch {
            delay(seconds * 1000L)
            _connectionState.value = ConnectionState.Connected
        }
    }

    /** Stops the internal ticker and any pending dropout timer. */
    fun stop() {
        tickJob.cancel()
        dropoutJob?.cancel()
    }

    private fun computeMetrics(t: Int): BikeMetrics {
        val (baseCadence, baseResistance) = when {
            t <= WARMUP_END_SEC -> warmupProfile(t)
            t <= COOLDOWN_START_SEC -> intervalProfile(t)
            else -> cooldownProfile(t)
        }
        val cadence = (baseCadence + random.nextInt(-JITTER, JITTER + 1))
            .coerceIn(CADENCE_MIN, CADENCE_MAX)
        val resistance = (baseResistance + random.nextInt(-JITTER, JITTER + 1))
            .coerceIn(RESISTANCE_MIN, RESISTANCE_MAX)
        return BikeMetrics(
            cadenceRpm = cadence,
            resistancePercent = resistance,
            powerWatts = derivePower(cadence, resistance),
            speedMph = deriveSpeed(cadence, resistance),
        )
    }

    /** Cadence 60->80 rpm, resistance 25->32%, ramping linearly across the warmup window. */
    private fun warmupProfile(t: Int): Pair<Int, Int> {
        val fraction = t.toDouble() / WARMUP_END_SEC
        val cadence = lerp(60.0, 80.0, fraction).roundToInt()
        val resistance = lerp(25.0, 32.0, fraction).roundToInt()
        return cadence to resistance
    }

    /** Alternating high-effort / recovery segments, repeating until [COOLDOWN_START_SEC]. */
    private fun intervalProfile(t: Int): Pair<Int, Int> {
        val cycleLength = INTERVAL_HIGH_SEC + INTERVAL_RECOVERY_SEC
        val phase = (t - WARMUP_END_SEC) % cycleLength
        return if (phase < INTERVAL_HIGH_SEC) {
            95 to 50 // high effort
        } else {
            70 to 30 // recovery
        }
    }

    /** Tapers down from the interval recovery baseline to a steady easy pace and holds. */
    private fun cooldownProfile(t: Int): Pair<Int, Int> {
        val sinceCooldownStart = (t - COOLDOWN_START_SEC).coerceIn(0, COOLDOWN_TAPER_SEC)
        val fraction = sinceCooldownStart.toDouble() / COOLDOWN_TAPER_SEC
        val cadence = lerp(70.0, 62.0, fraction).roundToInt()
        val resistance = lerp(30.0, 25.0, fraction).roundToInt()
        return cadence to resistance
    }

    private fun lerp(from: Double, to: Double, fraction: Double): Double =
        from + (to - from) * fraction.coerceIn(0.0, 1.0)

    /**
     * Power isn't sensor-calibrated (there's no real bike here) — this is a plausible
     * stand-in derived from cadence x resistance, scaled to land in a realistic indoor
     * cycling range (roughly 60-250W across the simulated ride's phases).
     */
    private fun derivePower(cadence: Int, resistance: Int): Int =
        ((cadence * resistance) / 25.0).roundToInt()

    /**
     * Speed has no real calibration either (Peloton's own speed model is proprietary) —
     * approximated as mostly cadence-driven with a smaller resistance contribution.
     */
    private fun deriveSpeed(cadence: Int, resistance: Int): Double {
        val raw = cadence * 0.18 + resistance * 0.12
        return (raw * 10.0).roundToInt() / 10.0
    }

    companion object {
        private const val TICK_INTERVAL_MS = 1000L

        private const val WARMUP_END_SEC = 180
        private const val INTERVAL_HIGH_SEC = 60
        private const val INTERVAL_RECOVERY_SEC = 60
        private const val INTERVALS_DURATION_SEC = 1500
        private const val COOLDOWN_START_SEC = WARMUP_END_SEC + INTERVALS_DURATION_SEC
        private const val COOLDOWN_TAPER_SEC = 180

        private const val CADENCE_MIN = 60
        private const val CADENCE_MAX = 110
        private const val RESISTANCE_MIN = 25
        private const val RESISTANCE_MAX = 60
        private const val JITTER = 2
    }
}
