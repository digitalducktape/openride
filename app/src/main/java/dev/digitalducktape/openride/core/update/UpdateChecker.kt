package dev.digitalducktape.openride.core.update

import kotlinx.serialization.json.Json

/** Outcome of evaluating the latest GitHub release against the installed build (PRD #22/T22). */
sealed interface UpdateCheckResult {
    /** The installed build is already at or ahead of the published version. */
    data class UpToDate(val currentVersionCode: Int) : UpdateCheckResult

    /** A newer build is published; [update] describes it. Still requires an explicit user tap
     *  to download, and another to install — nothing here installs anything on its own. */
    data class Available(val update: AvailableUpdate) : UpdateCheckResult

    /** The release couldn't be read or had no usable build; [reason] is user-facing. */
    data class Failed(val reason: String) : UpdateCheckResult
}

/**
 * Decides whether the latest GitHub release is a newer build than what's installed (PRD #22/T22).
 * Pure: takes the raw `/releases/latest` JSON, the installed version code, and this build's asset
 * infix, does no I/O — so selection, parsing, and validation are unit-testable without a network.
 *
 * The version code is read from the APK asset filename (`openride-<infix>-<versionCode>.apk`)
 * rather than any field GitHub provides, because a GitHub release has no notion of an Android
 * version code. The [assetInfix] (`real` for the bike build, `mock` for the dev build) keeps a
 * build from ever offering to install the wrong variant over itself.
 *
 * Validation is deliberately strict about the download URL: only `https://` is accepted, because
 * the payload is an APK the user is about to be asked to install.
 */
class UpdateChecker(private val json: Json = DefaultJson) {

    fun evaluate(currentVersionCode: Int, assetInfix: String, releaseJson: String): UpdateCheckResult {
        val release = try {
            json.decodeFromString(GitHubRelease.serializer(), releaseJson)
        } catch (e: Exception) {
            return UpdateCheckResult.Failed("Couldn't read the latest release")
        }

        val pattern = Regex("""^openride-${Regex.escape(assetInfix)}-(\d+)\.apk$""")
        val match = release.assets.firstNotNullOfOrNull { asset ->
            pattern.matchEntire(asset.name)?.let { m -> asset to m.groupValues[1].toInt() }
        } ?: return UpdateCheckResult.Failed("No matching build in the latest release")

        val (asset, versionCode) = match

        if (!asset.browserDownloadUrl.startsWith(HTTPS_PREFIX, ignoreCase = true)) {
            return UpdateCheckResult.Failed("Update download URL must be https")
        }

        return if (versionCode > currentVersionCode) {
            UpdateCheckResult.Available(
                AvailableUpdate(
                    versionCode = versionCode,
                    versionName = displayVersionName(release, versionCode),
                    apkUrl = asset.browserDownloadUrl,
                    notes = release.body?.ifBlank { null },
                ),
            )
        } else {
            UpdateCheckResult.UpToDate(currentVersionCode)
        }
    }

    /** The tag (a leading `v` stripped) if present, else the release name, else the build number. */
    private fun displayVersionName(release: GitHubRelease, versionCode: Int): String =
        release.tagName?.removePrefix("v")?.ifBlank { null }
            ?: release.name?.ifBlank { null }
            ?: "build $versionCode"

    companion object {
        private const val HTTPS_PREFIX = "https://"

        /** Tolerates extra fields — GitHub's release payload carries far more than we read. */
        val DefaultJson: Json = Json { ignoreUnknownKeys = true }
    }
}
