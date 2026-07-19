package dev.digitalducktape.openride.ui.common

/** Shared elapsed/duration formatting for the in-ride, summary, and history screens. */
object TimeFormat {
    /** `H:MM:SS` once an hour has elapsed, `MM:SS` otherwise. */
    fun elapsed(totalSec: Int): String {
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }
}
