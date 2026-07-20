package dev.digitalducktape.openride.core.content

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches raw bytes for a URL. A narrow interface (rather than calling [HttpURLConnection]
 * directly from [YouTubeContentRepository]) so tests can supply a fixture feed / simulate a
 * network failure without hitting the real network.
 */
fun interface FeedFetcher {
    /** @throws IOException if the URL can't be fetched (network error, non-2xx response, timeout). */
    fun fetch(url: String): InputStream
}

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
            throw IOException("HTTP $responseCode fetching $url")
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
 */
fun <T> retryingOnce(block: () -> T): T = try {
    block()
} catch (first: Exception) {
    block()
}
