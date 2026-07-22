package dev.digitalducktape.openride.core.update

/**
 * A newer build discovered from the latest GitHub release (PRD #22/T22): the version code
 * parsed from the APK asset filename, a display version name (the release tag), the asset's
 * download URL, and the release notes.
 *
 * @param versionCode the trailing integer of `openride-<variant>-<versionCode>.apk` — the sole
 *   "is this newer?" signal, compared against the installed build's `BuildConfig.VERSION_CODE`.
 * @param apkUrl the asset's `browser_download_url`. Must be `https://` — [UpdateChecker] rejects
 *   anything else rather than downloading an installable binary over cleartext.
 * @param notes the release body, shown before updating; may be null.
 */
data class AvailableUpdate(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String? = null,
)
