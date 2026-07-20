package dev.digitalducktape.openride.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Version comparison + manifest validation for the opt-in self-updater (PRD #22/T22). Pure JVM:
 * [UpdateChecker] does no I/O.
 */
class UpdateCheckerTest {

    private val checker = UpdateChecker()

    private fun manifestJson(
        versionCode: Int = 2,
        versionName: String = "0.2.0",
        apkUrl: String = "https://example.com/openride.apk",
        notes: String? = "Adds routes",
    ): String = buildString {
        append("""{"versionCode":$versionCode,"versionName":"$versionName","apkUrl":"$apkUrl"""")
        if (notes != null) append(""","notes":"$notes"""")
        append("}")
    }

    @Test
    fun `a higher versionCode is an available update`() {
        val result = checker.evaluate(currentVersionCode = 1, manifestJson = manifestJson(versionCode = 2))

        assertTrue(result is UpdateCheckResult.Available)
        val manifest = (result as UpdateCheckResult.Available).manifest
        assertEquals(2, manifest.versionCode)
        assertEquals("0.2.0", manifest.versionName)
        assertEquals("https://example.com/openride.apk", manifest.apkUrl)
        assertEquals("Adds routes", manifest.notes)
    }

    @Test
    fun `an equal versionCode is up to date`() {
        val result = checker.evaluate(currentVersionCode = 2, manifestJson = manifestJson(versionCode = 2))

        assertEquals(UpdateCheckResult.UpToDate(2), result)
    }

    @Test
    fun `a lower versionCode is up to date, never a downgrade offer`() {
        val result = checker.evaluate(currentVersionCode = 5, manifestJson = manifestJson(versionCode = 3))

        assertEquals(UpdateCheckResult.UpToDate(5), result)
    }

    @Test
    fun `versionName is not used for ordering`() {
        // Older-looking name but a higher code — the code wins.
        val result = checker.evaluate(
            currentVersionCode = 1,
            manifestJson = manifestJson(versionCode = 2, versionName = "0.0.9"),
        )

        assertTrue(result is UpdateCheckResult.Available)
    }

    @Test
    fun `a non-https apkUrl is rejected`() {
        val result = checker.evaluate(
            currentVersionCode = 1,
            manifestJson = manifestJson(apkUrl = "http://example.com/openride.apk"),
        )

        assertEquals(UpdateCheckResult.Failed("Update download URL must be https"), result)
    }

    @Test
    fun `a file scheme apkUrl is rejected`() {
        val result = checker.evaluate(
            currentVersionCode = 1,
            manifestJson = manifestJson(apkUrl = "file:///sdcard/evil.apk"),
        )

        assertEquals(UpdateCheckResult.Failed("Update download URL must be https"), result)
    }

    @Test
    fun `malformed JSON fails gracefully`() {
        val result = checker.evaluate(currentVersionCode = 1, manifestJson = "not json at all")

        assertEquals(UpdateCheckResult.Failed("Couldn't read the update manifest"), result)
    }

    @Test
    fun `JSON missing a required field fails gracefully`() {
        val result = checker.evaluate(currentVersionCode = 1, manifestJson = """{"versionCode":3}""")

        assertEquals(UpdateCheckResult.Failed("Couldn't read the update manifest"), result)
    }

    @Test
    fun `unknown extra fields are tolerated`() {
        val json = """
            {"versionCode":9,"versionName":"1.0","apkUrl":"https://example.com/a.apk","future":"x"}
        """.trimIndent()

        val result = checker.evaluate(currentVersionCode = 1, manifestJson = json)

        assertTrue(result is UpdateCheckResult.Available)
    }

    @Test
    fun `notes are optional`() {
        val result = checker.evaluate(currentVersionCode = 1, manifestJson = manifestJson(notes = null))

        assertTrue(result is UpdateCheckResult.Available)
        assertEquals(null, (result as UpdateCheckResult.Available).manifest.notes)
    }
}
