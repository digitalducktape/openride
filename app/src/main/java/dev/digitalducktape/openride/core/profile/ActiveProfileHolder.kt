package dev.digitalducktape.openride.core.profile

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the session's active rider profile id so the rest of the app (home, ride, history)
 * can scope itself to whichever profile is currently selected (PRD P0-3).
 *
 * The active id is kept in-memory as a [StateFlow] for observers, and mirrored to
 * [SharedPreferences] so the last-used profile survives process death (e.g. the activity
 * being recreated after the app is backgrounded) — note this does *not* change the app's
 * start destination, which is always profile-select per T11/P0-8; it only lets a
 * mid-session process restart resume without silently losing which profile was active.
 */
class ActiveProfileHolder(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _activeProfileId = MutableStateFlow(readPersisted())
    val activeProfileId: StateFlow<Long?> = _activeProfileId.asStateFlow()

    /** Sets [id] as the active profile for this session and persists it as "last used." */
    fun setActiveProfile(id: Long) {
        _activeProfileId.value = id
        prefs.edit().putLong(KEY_LAST_PROFILE_ID, id).apply()
    }

    /** Clears the active profile (e.g. on "switch rider"), returning to no-profile state. */
    fun clear() {
        _activeProfileId.value = null
        prefs.edit().remove(KEY_LAST_PROFILE_ID).apply()
    }

    private fun readPersisted(): Long? {
        val id = prefs.getLong(KEY_LAST_PROFILE_ID, NO_PROFILE)
        return if (id == NO_PROFILE) null else id
    }

    companion object {
        private const val PREFS_NAME = "openride_prefs"
        private const val KEY_LAST_PROFILE_ID = "last_profile_id"
        private const val NO_PROFILE = -1L
    }
}
