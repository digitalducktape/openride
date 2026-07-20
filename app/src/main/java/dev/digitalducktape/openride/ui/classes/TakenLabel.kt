package dev.digitalducktape.openride.ui.classes

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Label for a class card's "already ridden" badge (v2): when the active rider last took
 * this class. Pure formatting, split out of the composable so it's plain-JUnit testable.
 */
object TakenLabel {
    private val formatter = DateTimeFormatter.ofPattern("MMM d")

    fun format(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val date = Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate()
        return "TAKEN ${formatter.format(date).uppercase()}"
    }
}
