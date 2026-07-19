package dev.digitalducktape.openride.ui.profile

/**
 * Predefined avatar building blocks offered on profile creation (PRD P0-3: "name + avatar").
 * Kept as plain ARGB ints / strings (no `androidx.compose.ui.graphics.Color`) so the
 * create-profile view model stays testable with plain JUnit — the create screen converts
 * [colors] to `Color` at render time.
 */
object AvatarOptions {
    val colors: List<Int> = listOf(
        0xFFE8442D.toInt(),
        0xFF2D9CE8.toInt(),
        0xFF35C46A.toInt(),
        0xFFF2B22B.toInt(),
        0xFF9B59E8.toInt(),
        0xFFE85D9C.toInt(),
        0xFF4DD0C4.toInt(),
        0xFFB0BEC5.toInt(),
    )

    val emojis: List<String> = listOf("🚴", "🔥", "⚡", "💪", "🚵", "🏆", "❤️", "⭐")

    val defaultColor: Int = colors.first()
    val defaultEmoji: String = emojis.first()
}
