package dev.digitalducktape.openride.core.update

import kotlinx.serialization.json.Json

/** Outcome of evaluating a fetched update manifest against the installed build (PRD #22/T22). */
sealed interface UpdateCheckResult {
    /** The installed build is already at or ahead of the published version. */
    data class UpToDate(val currentVersionCode: Int) : UpdateCheckResult

    /** A newer build is published; [manifest] describes it. Still requires an explicit user tap
     *  to download, and another to install — nothing here installs anything on its own. */
    data class Available(val manifest: UpdateManifest) : UpdateCheckResult

    /** The manifest couldn't be read or failed validation; [reason] is user-facing. */
    data class Failed(val reason: String) : UpdateCheckResult
}

/**
 * Decides whether a fetched manifest represents a newer build (PRD #22/T22). Pure: takes the
 * manifest's raw JSON and the installed version code, does no I/O — so the comparison and
 * validation rules are unit-testable without a network or a real package manager.
 *
 * Validation is deliberately strict about [UpdateManifest.apkUrl]: only `https://` is accepted,
 * because the payload is an APK the user is about to be asked to install. A plaintext or
 * `file://`/`content://` URL is rejected outright rather than downloaded.
 */
class UpdateChecker(private val json: Json = DefaultJson) {

    fun evaluate(currentVersionCode: Int, manifestJson: String): UpdateCheckResult {
        val manifest = try {
            json.decodeFromString(UpdateManifest.serializer(), manifestJson)
        } catch (e: Exception) {
            return UpdateCheckResult.Failed("Couldn't read the update manifest")
        }

        if (!manifest.apkUrl.startsWith(HTTPS_PREFIX, ignoreCase = true)) {
            return UpdateCheckResult.Failed("Update download URL must be https")
        }

        return if (manifest.versionCode > currentVersionCode) {
            UpdateCheckResult.Available(manifest)
        } else {
            UpdateCheckResult.UpToDate(currentVersionCode)
        }
    }

    companion object {
        private const val HTTPS_PREFIX = "https://"

        /** Tolerates extra fields so a manifest can carry keys this build doesn't know about. */
        val DefaultJson: Json = Json { ignoreUnknownKeys = true }
    }
}
