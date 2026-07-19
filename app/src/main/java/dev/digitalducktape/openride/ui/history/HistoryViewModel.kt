package dev.digitalducktape.openride.ui.history

import androidx.lifecycle.ViewModel
import dev.digitalducktape.openride.core.data.Ride
import dev.digitalducktape.openride.core.data.RideRepository
import dev.digitalducktape.openride.core.export.CsvExporter
import dev.digitalducktape.openride.core.profile.ActiveProfileHolder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * History tab (PRD P0-5): the active profile's rides, newest first. Queries
 * [RideRepository.observeHistory], which returns aggregate [dev.digitalducktape.openride.core.data.Ride]
 * rows only (no per-second samples) — the "100+ rides without jank" acceptance criterion is
 * satisfied by never touching the sample tables here, not by any paging in this view model.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    activeProfileHolder: ActiveProfileHolder,
    rideRepository: RideRepository,
) : ViewModel() {
    private val ridesFlow: Flow<List<Ride>> = activeProfileHolder.activeProfileId.flatMapLatest { profileId ->
        if (profileId == null) flowOf(emptyList()) else rideRepository.observeHistory(profileId)
    }

    val rows: Flow<List<RideHistoryRow>> = ridesFlow.map { rides -> rides.map(RideHistoryMapper::map) }

    /**
     * CSV export of the active profile's full ride history (PRD P1-2), as it stands at the
     * moment of the call — a one-shot snapshot via [Flow.first], not a live subscription,
     * since exporting is a discrete user action.
     */
    suspend fun historyCsvContent(): String = CsvExporter.exportHistory(ridesFlow.first())
}
