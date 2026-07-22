package dev.digitalducktape.openride.core.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The subset of GitHub's `/repos/{owner}/{repo}/releases/latest` response OpenRide reads
 * (PRD #22/T22). Now that the repository is public the app queries the Releases API directly
 * instead of a hand-maintained manifest — the newest build is discovered from the release's
 * APK asset, whose filename (`openride-<variant>-<versionCode>.apk`) carries the authoritative
 * version code. The tag and body are display-only.
 *
 * `@SerialName` maps GitHub's snake_case keys onto idiomatic Kotlin names; unknown keys are
 * ignored (see [UpdateChecker.DefaultJson]) so the rest of GitHub's large payload is skipped.
 */
@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String? = null,
    val name: String? = null,
    val body: String? = null,
    val assets: List<GitHubReleaseAsset> = emptyList(),
)

@Serializable
data class GitHubReleaseAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)
