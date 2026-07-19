package dev.digitalducktape.openride.core.heartrate

import dev.digitalducktape.openride.core.data.ProfileRepository
import dev.digitalducktape.openride.core.profile.ActiveProfileHolder
import dev.digitalducktape.openride.core.sensor.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * Connects/reconnects a [ManagedHeartRateDataSource] to whichever BLE address is paired for
 * the *currently active* profile (PRD P1-4: "remember strap per profile"), and exposes a
 * single live `bpm`/`connectionState` pair the rest of the app (ride screen) can just observe
 * without caring which rider is active or when they switched.
 *
 * This is the piece of the BLE feature that's meaningfully unit-testable without hardware
 * (see `HeartRateManagerTest`): given a fake [connectionFactory], it's straightforward to
 * verify "no pairing -> Unavailable", "pairing appears -> a source gets created and started",
 * and "pairing disappears (e.g. switching to an unpaired rider) -> the old source is stopped
 * and state resets to null/Unavailable" — without touching real `android.bluetooth` APIs.
 *
 * @param connectionFactory builds a (not-yet-started) [ManagedHeartRateDataSource] for a given
 *   BLE address. Production wiring passes `{ address -> BleHeartRateDataSource(context, address) }`;
 *   tests pass a fake.
 */
class HeartRateManager(
    activeProfileHolder: ActiveProfileHolder,
    profileRepository: ProfileRepository,
    private val connectionFactory: (address: String) -> ManagedHeartRateDataSource,
    private val scope: CoroutineScope,
) {
    private val _bpm = MutableStateFlow<Int?>(null)
    val bpm: StateFlow<Int?> = _bpm.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Unavailable)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var currentSource: ManagedHeartRateDataSource? = null
    private var forwardingJob: Job? = null

    private val _connectedAddress = MutableStateFlow<String?>(null)
    /** The BLE address currently connected (or being connected) to. */
    val connectedAddress: StateFlow<String?> = _connectedAddress.asStateFlow()

    init {
        combine(
            activeProfileHolder.activeProfileId,
            // profileRepository.observeProfiles() is backed by a Room @Query Flow, which
            // (unlike activeProfileId, a plain in-memory StateFlow) doesn't necessarily emit
            // its first value synchronously on collection. onStart seeds an empty list
            // immediately so this combine still resolves right away for the extremely common
            // "no rider active yet" / "active rider has no paired strap" cases, rather than
            // silently sitting uninitialized until Room's first real query result lands.
            profileRepository.observeProfiles().onStart { emit(emptyList()) },
        ) { id, profiles ->
            profiles.firstOrNull { it.id == id }?.pairedHrDeviceAddress
        }
            .distinctUntilChanged()
            .onEach { address -> reconnect(address) }
            .launchIn(scope)
    }

    private fun reconnect(address: String?) {
        forwardingJob?.cancel()
        currentSource?.stop()
        currentSource = null
        _connectedAddress.value = null
        _bpm.value = null
        _connectionState.value = ConnectionState.Unavailable

        if (address == null) return

        val source = connectionFactory(address)
        currentSource = source
        _connectedAddress.value = address
        source.start()

        forwardingJob = scope.launch {
            launch { source.bpm.onEach { _bpm.value = it }.launchIn(this) }
            launch { source.connectionState.onEach { _connectionState.value = it }.launchIn(this) }
        }
    }

    /** Stops whatever source is currently connected, e.g. when the app is being torn down. */
    fun stop() {
        forwardingJob?.cancel()
        currentSource?.stop()
        currentSource = null
        _connectedAddress.value = null
    }
}
