package dev.digitalducktape.openride.core.content

/**
 * Parses YouTube's thumbnail duration badge text (`"18:23"`, `"1:02:03"`) into seconds.
 *
 * The same badge slot also carries non-duration text on some tiles (`"4 videos"` on a
 * playlist, `"LIVE"` on a stream), so anything that isn't a colon-separated clock returns
 * `null` — the caller treats that as "duration unknown", never as zero.
 */
object DurationText {
    fun toSeconds(text: String): Int? {
        val parts = text.trim().split(':')
        if (parts.size !in 2..3) return null
        val numbers = parts.map { it.toIntOrNull() ?: return null }
        if (numbers.any { it < 0 }) return null
        return when (numbers.size) {
            2 -> numbers[0] * 60 + numbers[1]
            else -> numbers[0] * 3600 + numbers[1] * 60 + numbers[2]
        }
    }
}
