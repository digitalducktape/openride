package dev.digitalducktape.openride.core.update

import kotlinx.serialization.Serializable

/**
 * The update descriptor OpenRide fetches from a user-configured URL (PRD #22/T22) — e.g. a
 * small JSON file published alongside a GitHub release:
 *
 * ```json
 * {
 *   "versionCode": 2,
 *   "versionName": "0.2.0",
 *   "apkUrl": "https://example.com/openride-0.2.0.apk",
 *   "notes": "Adds GPX routes"
 * }
 * ```
 *
 * @param versionCode compared against the installed build's version code — the sole "is this
 *   newer?" signal (version *names* are for humans and aren't ordered reliably).
 * @param apkUrl where to download the APK. Must be `https://` — see [UpdateChecker], which
 *   rejects anything else rather than downloading an installable binary over cleartext.
 * @param notes optional human-readable release notes to show before updating.
 */
@Serializable
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String? = null,
)
