package dev.digitalducktape.openride.ui.ride

import androidx.lifecycle.ViewModel
import dev.digitalducktape.openride.core.data.ProfileRepository
import dev.digitalducktape.openride.core.data.Ride
import dev.digitalducktape.openride.core.data.RideRepository
import dev.digitalducktape.openride.core.data.RideSample
import dev.digitalducktape.openride.core.ride.FtpEstimator
import dev.digitalducktape.openride.core.ride.RideSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Post-ride summary (PRD P0-5), reused unmodified as history's detail view (T8): loads a
 * ride's aggregates *and* its full per-second sample series by [rideId] from Room, rather
 * than being handed the just-stopped [Ride] directly, so a fresh-off-the-bike ride and an
 * older ride tapped from history go through the exact same code path.
 *
 * Also surfaces an FTP suggestion (PRD P1-3): once the ride is at least 20 minutes long,
 * [FtpEstimator] proposes 95% of the ride's best 20-minute average power as an FTP update for
 * the rider who logged it, applied via [applySuggestedFtp].
 */
class RideSummaryViewModel(
    private val rideRepository: RideRepository,
    private val profileRepository: ProfileRepository,
    private val rideSessionManager: RideSessionManager,
    private val rideId: Long,
) : ViewModel() {
    private val _ride = MutableStateFlow<Ride?>(null)
    val ride: StateFlow<Ride?> = _ride.asStateFlow()

    private val _samples = MutableStateFlow<List<RideSample>>(emptyList())
    val samples: StateFlow<List<RideSample>> = _samples.asStateFlow()

    private val _suggestedFtp = MutableStateFlow<Int?>(null)
    /** Suggested FTP in watts, or `null` if the ride is shorter than 20 minutes. */
    val suggestedFtp: StateFlow<Int?> = _suggestedFtp.asStateFlow()

    private val _ftpApplied = MutableStateFlow(false)
    /** `true` once [applySuggestedFtp] has successfully updated the rider's profile. */
    val ftpApplied: StateFlow<Boolean> = _ftpApplied.asStateFlow()

    suspend fun load() {
        _ride.value = rideRepository.getRide(rideId)
        _samples.value = rideRepository.getSamples(rideId)
        _suggestedFtp.value = FtpEstimator.estimateFtp(_samples.value)
    }

    /**
     * Applies [suggestedFtp] to the profile that logged this ride. No-op if the ride hasn't
     * loaded, there's no suggestion (ride under 20 minutes), or the profile has since been
     * deleted.
     */
    suspend fun applySuggestedFtp() {
        val ride = _ride.value ?: return
        val ftp = _suggestedFtp.value ?: return
        val profile = profileRepository.getProfile(ride.profileId) ?: return
        profileRepository.updateProfile(profile.copy(ftp = ftp))
        _ftpApplied.value = true
    }

    /**
     * Returns to [dev.digitalducktape.openride.core.ride.RideSessionState.Idle] if it was
     * sitting in [dev.digitalducktape.openride.core.ride.RideSessionState.Finished] (the
     * just-stopped-this-ride path) — a no-op otherwise (e.g. viewing an older ride from
     * history), since [RideSessionManager.reset] only acts on `Finished`.
     */
    fun dismiss() {
        rideSessionManager.reset()
    }
}
