package dev.digitalducktape.openride.core.content

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException

/**
 * Fetches raw bytes for a URL. A narrow interface (rather than calling [HttpURLConnection]
 * directly from [YouTubeContentRepository]) so tests can supply a fixture feed / simulate a
 * network failure without hitting the real network.
 */
fun interface FeedFetcher {
    /**
     * @throws IOException if the URL can't be fetched (network error, timeout).
     * @throws HttpStatusException if the server answered but with a non-2xx status — a
     *   subtype of [IOException] so every existing failure-handling path keeps working
     *   unchanged, but distinguishable by callers that need to tell "you're offline" apart
     *   from "that doesn't exist."
     */
    fun fetch(url: String): InputStream
}

/**
 * A fetch reached the server and got back a non-2xx response, as opposed to failing to reach
 * it at all (DNS, timeout, connection refused). Both are "the fetch failed" to
 * [YouTubeContentRepository]'s degradation ladder and [retryingOnce] — this stays a plain
 * [IOException] subclass so `catch (e: IOException)` / `catch (e: Exception)` in the content
 * layer keeps treating it exactly like any other failed fetch.
 *
 * It exists as its own type because the *rider-facing* framing of these two cases must not be
 * the same: a 404 on a mistyped `@handle` means "no such channel," not "check your
 * connection" — telling a rider to retry a lookup that will 404 every time is actively
 * misleading. Do not collapse this back into a bare [IOException]; that regression is exactly
 * what this type prevents.
 */
class HttpStatusException(val statusCode: Int, message: String) : IOException(message)

/**
 * Real implementation using [HttpURLConnection] — deliberately avoiding an OkHttp dependency
 * to keep the content layer dependency-light (T9), per the ticket's stated preference.
 */
class HttpUrlFeedFetcher : FeedFetcher {
    override fun fetch(url: String): InputStream {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.requestMethod = "GET"
        // Without a desktop UA, YouTube may serve a mobile or consent-wall variant of the
        // page whose embedded JSON has a different shape than YouTubePageParser expects.
        connection.setRequestProperty("User-Agent", DESKTOP_USER_AGENT)

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            connection.disconnect()
            throw HttpStatusException(responseCode, "HTTP $responseCode fetching $url")
        }
        return connection.inputStream
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 10_000
        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}

/** Fetches [url] and decodes it as UTF-8 text, always closing the stream. */
fun FeedFetcher.fetchText(url: String): String =
    fetch(url).use { it.bufferedReader().readText() }

/**
 * Runs [block], and on any failure runs it exactly once more.
 *
 * YouTube's feed and page endpoints intermittently answer 404/500 for URLs that are
 * perfectly valid — the same feed URL was observed alternating between 200 and 404 seconds
 * apart. Without this, a healthy channel would randomly fall back to cached content.
 *
 * [CancellationException] is rethrown immediately rather than retried: it doesn't mean the
 * fetch failed, it means the coroutine that wanted the result is gone. Retrying it would do
 * pointless work after the consumer stopped listening, and swallowing it here would break
 * structured concurrency by hiding the cancellation from the parent scope. Today's call sites
 * are synchronous [HttpURLConnection] I/O so this can't yet happen mid-fetch, but [retryingOnce]
 * is a general-purpose helper and the next caller may not be so lucky.
 */
fun <T> retryingOnce(block: () -> T): T = try {
    block()
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (_: Exception) {
    block()
}
