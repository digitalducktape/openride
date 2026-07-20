package dev.digitalducktape.openride.core.update

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the user-configured update manifest URL (PRD #22/T22). The self-updater is **opt-in**:
 * with no URL set, OpenRide never contacts anything and the update screen just says so — there is
 * no default/vendor URL baked in.
 *
 * Same [SharedPreferences]-mirrored-into-a-[StateFlow] shape as
 * [dev.digitalducktape.openride.core.profile.ActiveProfileHolder].
 */
class UpdateSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _manifestUrl = MutableStateFlow(prefs.getString(KEY_MANIFEST_URL, null)?.ifBlank { null })

    /** The configured manifest URL, or `null` when the updater hasn't been set up. */
    val manifestUrl: StateFlow<String?> = _manifestUrl.asStateFlow()

    /** Sets (or, with a blank/null value, clears) the manifest URL. */
    fun setManifestUrl(url: String?) {
        val cleaned = url?.trim()?.ifBlank { null }
        _manifestUrl.value = cleaned
        if (cleaned == null) {
            prefs.edit().remove(KEY_MANIFEST_URL).apply()
        } else {
            prefs.edit().putString(KEY_MANIFEST_URL, cleaned).apply()
        }
    }

    private companion object {
        const val PREFS_NAME = "openride_prefs"
        const val KEY_MANIFEST_URL = "update_manifest_url"
    }
}
