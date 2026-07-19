package dev.digitalducktape.openride.core.heartrate

import dev.digitalducktape.openride.core.sensor.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fully-controllable [ManagedHeartRateDataSource] test double — same reasoning as
 * [dev.digitalducktape.openride.core.ride.FakeBikeDataSource]: lets [HeartRateManagerTest]
 * assert exact reconnect behavior without touching real `android.bluetooth` GATT APIs.
 */
class FakeManagedHeartRateDataSource(private val address: String) : ManagedHeartRateDataSource {
    private val _bpm = MutableStateFlow<Int?>(null)
    override val bpm: StateFlow<Int?> = _bpm.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Unavailable)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    var startCallCount = 0
        private set
    var stopCallCount = 0
        private set

    override fun start() {
        startCallCount++
        _connectionState.value = ConnectionState.Unavailable // real class stays this way until a frame arrives
    }

    override fun stop() {
        stopCallCount++
    }

    fun emitBpm(value: Int) {
        _bpm.value = value
        _connectionState.value = ConnectionState.Connected
    }

    fun emitConnectionState(state: ConnectionState) {
        _connectionState.value = state
    }

    companion object {
        /** Tracks every fake created, keyed by address, so tests can find one after the fact. */
        val created = mutableMapOf<String, FakeManagedHeartRateDataSource>()

        fun factory(): (String) -> ManagedHeartRateDataSource = { address ->
            FakeManagedHeartRateDataSource(address).also { created[address] = it }
        }
    }
}
