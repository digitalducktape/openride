package dev.digitalducktape.openride.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Version comparison + asset selection for the GitHub-native self-updater (PRD #22/T22). Pure
 * JVM: [UpdateChecker] does no I/O — it takes the `/releases/latest` JSON and the installed
 * version and decides whether the release is newer.
 *
 * The authoritative version is the trailing integer in the APK asset filename
 * (`openride-<infix>-<versionCode>.apk`); the release tag/body are only for display.
 */
class UpdateCheckerTest {

    private val checker = UpdateChecker()

    /** A `/releases/latest` payload carrying one bike (`real`) asset. */
    private fun releaseJson(
        versionCode: Int = 2,
        tag: String = "v0.2.0",
        body: String? = "Adds routes",
        infix: String = "real",
        apkUrl: String = "https://github.com/o/r/releases/download/v0.2.0/openride-$infix-$versionCode.apk",
    ): String = buildString {
        append("""{"tag_name":"$tag","name":"OpenRide 0.2.0"""")
        if (body != null) append(""","body":"$body"""")
        append(""","assets":[{"name":"openride-$infix-$versionCode.apk","browser_download_url":"$apkUrl"}]}""")
    }

    @Test
    fun `a higher versionCode in the asset filename is an available update`() {
        val result = checker.evaluate(currentVersionCode = 1, assetInfix = "real", releaseJson = releaseJson(versionCode = 2))

        assertTrue(result is UpdateCheckResult.Available)
        val update = (result as UpdateCheckResult.Available).update
        assertEquals(2, update.versionCode)
        assertEquals("0.2.0", update.versionName)
        assertEquals(
            "https://github.com/o/r/releases/download/v0.2.0/openride-real-2.apk",
            update.apkUrl,
        )
        assertEquals("Adds routes", update.notes)
    }

    @Test
    fun `an equal versionCode is up to date`() {
        val result = checker.evaluate(currentVersionCode = 2, assetInfix = "real", releaseJson = releaseJson(versionCode = 2))

        assertEquals(UpdateCheckResult.UpToDate(2), result)
    }

    @Test
    fun `a lower versionCode is up to date, never a downgrade offer`() {
        val result = checker.evaluate(currentVersionCode = 5, assetInfix = "real", releaseJson = releaseJson(versionCode = 3))

        assertEquals(UpdateCheckResult.UpToDate(5), result)
    }

    @Test
    fun `the version name comes from the tag with a leading v stripped`() {
        val result = checker.evaluate(
            currentVersionCode = 1,
            assetInfix = "real",
            releaseJson = releaseJson(versionCode = 9, tag = "v1.4.2"),
        )

        assertTrue(result is UpdateCheckResult.Available)
        assertEquals("1.4.2", (result as UpdateCheckResult.Available).update.versionName)
    }

    @Test
    fun `only the asset matching the build's infix is considered`() {
        // A release carrying both a mock and a real APK; the mock build must pick the mock one.
        val json = """
            {"tag_name":"v0.3.0","assets":[
              {"name":"openride-real-4.apk","browser_download_url":"https://github.com/o/r/openride-real-4.apk"},
              {"name":"openride-mock-4.apk","browser_download_url":"https://github.com/o/r/openride-mock-4.apk"}
            ]}
        """.trimIndent()

        val result = checker.evaluate(currentVersionCode = 1, assetInfix = "mock", releaseJson = json)

        assertTrue(result is UpdateCheckResult.Available)
        assertEquals(
            "https://github.com/o/r/openride-mock-4.apk",
            (result as UpdateCheckResult.Available).update.apkUrl,
        )
    }

    @Test
    fun `no asset matching the infix fails gracefully`() {
        // Only a real asset published; a mock build finds nothing to install.
        val result = checker.evaluate(currentVersionCode = 1, assetInfix = "mock", releaseJson = releaseJson(infix = "real"))

        assertEquals(UpdateCheckResult.Failed("No matching build in the latest release"), result)
    }

    @Test
    fun `a non-https download URL is rejected`() {
        val result = checker.evaluate(
            currentVersionCode = 1,
            assetInfix = "real",
            releaseJson = releaseJson(apkUrl = "http://github.com/o/r/openride-real-2.apk"),
        )

        assertEquals(UpdateCheckResult.Failed("Update download URL must be https"), result)
    }

    @Test
    fun `malformed JSON fails gracefully`() {
        val result = checker.evaluate(currentVersionCode = 1, assetInfix = "real", releaseJson = "not json at all")

        assertEquals(UpdateCheckResult.Failed("Couldn't read the latest release"), result)
    }

    @Test
    fun `unknown extra fields in the release payload are tolerated`() {
        val json = """
            {"tag_name":"v1.0","id":42,"draft":false,"assets":[
              {"name":"openride-real-9.apk","browser_download_url":"https://github.com/o/r/a.apk","size":123}
            ]}
        """.trimIndent()

        val result = checker.evaluate(currentVersionCode = 1, assetInfix = "real", releaseJson = json)

        assertTrue(result is UpdateCheckResult.Available)
    }

    @Test
    fun `notes are optional`() {
        val result = checker.evaluate(currentVersionCode = 1, assetInfix = "real", releaseJson = releaseJson(body = null))

        assertTrue(result is UpdateCheckResult.Available)
        assertEquals(null, (result as UpdateCheckResult.Available).update.notes)
    }
}
