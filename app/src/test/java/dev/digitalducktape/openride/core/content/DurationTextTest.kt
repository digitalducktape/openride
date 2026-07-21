package dev.digitalducktape.openride.core.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DurationTextTest {

    @Test
    fun `parses minutes and seconds`() {
        assertEquals(1103, DurationText.toSeconds("18:23"))
    }

    @Test
    fun `parses hours minutes and seconds`() {
        assertEquals(3723, DurationText.toSeconds("1:02:03"))
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals(600, DurationText.toSeconds(" 10:00 "))
    }

    @Test
    fun `returns null for non-duration badges`() {
        assertNull(DurationText.toSeconds("4 videos"))
        assertNull(DurationText.toSeconds("LIVE"))
        assertNull(DurationText.toSeconds(""))
        assertNull(DurationText.toSeconds("1:2:3:4"))
    }
}
