package dev.digitalducktape.openride.ui.classes

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TakenLabelTest {

    @Test
    fun `formats the taken date in the given zone`() {
        // 2026-07-19 00:30 UTC.
        val epochMs = 1_784_421_000_000L

        assertEquals("TAKEN JUL 19", TakenLabel.format(epochMs, ZoneId.of("UTC")))
        // The same instant is still Jul 18 on the US west coast.
        assertEquals("TAKEN JUL 18", TakenLabel.format(epochMs, ZoneId.of("America/Los_Angeles")))
    }
}
