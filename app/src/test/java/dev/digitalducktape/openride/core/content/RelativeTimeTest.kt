package dev.digitalducktape.openride.core.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelativeTimeTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `parses days ago`() {
        assertEquals(now - 3L * 86_400_000L, RelativeTime.toEpochMs("3 days ago", now))
    }

    @Test
    fun `parses singular units`() {
        assertEquals(now - 86_400_000L, RelativeTime.toEpochMs("1 day ago", now))
    }

    @Test
    fun `parses weeks months and years`() {
        assertEquals(now - 2L * 7 * 86_400_000L, RelativeTime.toEpochMs("2 weeks ago", now))
        assertEquals(now - 4L * 30 * 86_400_000L, RelativeTime.toEpochMs("4 months ago", now))
        assertEquals(now - 5L * 365 * 86_400_000L, RelativeTime.toEpochMs("5 years ago", now))
    }

    @Test
    fun `orders newer before older`() {
        val newer = RelativeTime.toEpochMs("2 days ago", now)!!
        val older = RelativeTime.toEpochMs("2 months ago", now)!!
        assertTrue(newer > older)
    }

    @Test
    fun `returns null for text that is not a relative time`() {
        assertNull(RelativeTime.toEpochMs("16K views", now))
        assertNull(RelativeTime.toEpochMs("The Spin Junkie", now))
        assertNull(RelativeTime.toEpochMs("Streamed live", now))
    }
}
