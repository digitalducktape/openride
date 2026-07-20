package dev.digitalducktape.openride.core.content

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RetryingFetchTest {

    @Test
    fun `returns the first successful result without retrying`() {
        var calls = 0
        val result = retryingOnce {
            calls++
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(1, calls)
    }

    @Test
    fun `retries once after a failure and returns the second result`() {
        var calls = 0
        val result = retryingOnce {
            calls++
            if (calls == 1) throw IOException("HTTP 500") else "ok"
        }

        assertEquals("ok", result)
        assertEquals(2, calls)
    }

    @Test
    fun `rethrows when both attempts fail`() {
        var calls = 0
        assertThrows(IOException::class.java) {
            retryingOnce<String> {
                calls++
                throw IOException("HTTP 404")
            }
        }
        assertEquals(2, calls)
    }

    @Test
    fun `fetchText decodes the stream as UTF-8`() {
        val fetcher = FeedFetcher { "héllo".byteInputStream() }

        assertEquals("héllo", fetcher.fetchText("https://example.test"))
    }
}
