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
    }
}
