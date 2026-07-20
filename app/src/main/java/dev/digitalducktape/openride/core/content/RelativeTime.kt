package dev.digitalducktape.openride.core.content

/**
 * Turns YouTube's relative publish label (`"4 months ago"`) into an approximate epoch
 * timestamp.
 *
 * The channel page only ever states age this coarsely. Exact timestamps come from the RSS
 * feed, but that feed only covers a channel's most recent 15 videos — everything older is
 * ordered by this approximation instead. Months and years use flat 30/365-day lengths: the
 * result is only ever used for sorting a browse list, so calendar accuracy would buy nothing.
 */
object RelativeTime {
    private val PATTERN = Regex("""^(\d+)\s+(second|minute|hour|day|week|month|year)s?\s+ago$""")

    fun toEpochMs(text: String, nowEpochMs: Long): Long? {
        val match = PATTERN.find(text.trim().lowercase()) ?: return null
        val count = match.groupValues[1].toLongOrNull() ?: return null
        val unitMs = when (match.groupValues[2]) {
            "second" -> 1_000L
            "minute" -> 60_000L
            "hour" -> 3_600_000L
            "day" -> 86_400_000L
            "week" -> 7L * 86_400_000L
            "month" -> 30L * 86_400_000L
            else -> 365L * 86_400_000L
        }
        return nowEpochMs - count * unitMs
    }
}
