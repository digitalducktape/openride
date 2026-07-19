package dev.digitalducktape.openride.ui.classes

import androidx.lifecycle.ViewModel
import dev.digitalducktape.openride.core.content.YouTubeContentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Classes tab (PRD P0-6, T10): loads each configured channel's videos via
 * [YouTubeContentRepository] and exposes them as one row per channel.
 *
 * [refresh] is a plain `suspend fun` driven from the screen's `LaunchedEffect`/
 * `rememberCoroutineScope`, rather than something this class launches on `viewModelScope`
 * itself — the same pattern
 * [dev.digitalducktape.openride.ui.profile.ProfileCreateViewModel.save] uses, so the load
 * can be driven directly from `runTest` in tests without depending on `viewModelScope`'s
 * Main-dispatcher wiring.
 */
class ClassesViewModel(private val contentRepository: YouTubeContentRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<ClassesUiState>(ClassesUiState.Loading)
    val uiState: StateFlow<ClassesUiState> = _uiState.asStateFlow()

    /** Fetches (or re-fetches) all configured channels. Safe to call repeatedly. */
    suspend fun refresh() {
        _uiState.value = ClassesUiState.Loading
        val sections = contentRepository.channelSections()
        _uiState.value = ClassesUiState.Loaded(
            sections = sections,
            anyRefreshFailed = sections.any { it.refreshFailed },
        )
    }
}
