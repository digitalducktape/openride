package dev.digitalducktape.openride.ui.classes

import dev.digitalducktape.openride.core.content.ChannelSection

/** UI state for [ClassesScreen] (T10). */
sealed interface ClassesUiState {
    data object Loading : ClassesUiState

    /**
     * @param anyRefreshFailed true if at least one channel's live feed couldn't be fetched
     *   and is showing cached/last-known content instead — drives the "couldn't refresh"
     *   banner (PRD P0-6) without hiding the rows that did succeed.
     */
    data class Loaded(
        val sections: List<ChannelSection>,
        val anyRefreshFailed: Boolean,
    ) : ClassesUiState
}
