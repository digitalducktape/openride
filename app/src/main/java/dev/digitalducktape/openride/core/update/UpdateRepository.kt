package dev.digitalducktape.openride.core.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dev.digitalducktape.openride.core.content.FeedFetcher
import dev.digitalducktape.openride.core.content.HttpUrlFeedFetcher
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fetches the update manifest, downloads the APK, and builds the install intent (PRD #22/T22).
 *
 * **Nothing here installs anything.** [downloadApk] only writes a file into the app's own cache;
 * handing it to the system package installer is a separate, explicitly user-tapped step
 * ([installIntentFor]), and the actual install is then the platform installer's own confirmation
 * UI. That keeps the ticket's "no auto-install without user tap" property structural rather than
 * a UI convention.
 *
 * Reuses [FeedFetcher] (the same narrow HTTP seam the content layer uses) so tests can supply a
 * fixture manifest without a network.
 */
class UpdateRepository(
    private val context: Context,
    private val fetcher: FeedFetcher = HttpUrlFeedFetcher(),
    private val checker: UpdateChecker = UpdateChecker(),
) {

    /** Fetches [manifestUrl] and evaluates it against [currentVersionCode]. Never throws. */
    suspend fun check(manifestUrl: String, currentVersionCode: Int): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            val body = try {
                fetcher.fetch(manifestUrl).use { it.bufferedReader().readText() }
            } catch (e: Exception) {
                return@withContext UpdateCheckResult.Failed("Couldn't reach the update URL")
            }
            checker.evaluate(currentVersionCode, body)
        }

    /**
     * Downloads [manifest]'s APK into the app's cache and returns the file, or `null` if the
     * download failed. A partial download is deleted rather than left behind for the installer
     * to choke on.
     */
    suspend fun downloadApk(manifest: UpdateManifest): File? = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, UPDATES_DIR).apply { mkdirs() }
        val target = File(dir, "openride-${manifest.versionCode}.apk")
        try {
            fetcher.fetch(manifest.apkUrl).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target
        } catch (e: Exception) {
            target.delete()
            null
        }
    }

    /**
     * Builds the intent that hands [apk] to the system package installer. Launching it shows the
     * platform's own install confirmation — the user still has to accept there.
     */
    fun installIntentFor(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private companion object {
        const val UPDATES_DIR = "updates"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
