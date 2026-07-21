package dev.digitalducktape.openride.core.content

/**
 * Decides whether a class title (video or playlist) is a cycling class, so a creator's
 * *other* content — sculpt/strength workouts, vlogs, hauls — stays out of the Classes catalog.
 *
 * A configured channel is a whole creator, not a spin-only feed: a fitness creator posts spin
 * classes alongside strength classes, vlogs, and personal videos, and the duration + members
 * filters can't tell them apart. The [content catalog expansion][1] originally called title
 * keyword filtering a non-goal ("would drop valid classes"); real mixed-content channels
 * reversed that, and this is the agreed mitigation.
 *
 * The rule is a hybrid: a title is a cycling class when it **contains a cycling term** AND
 * **contains no veto term**.
 * - The allow-list carries the whole burden of excluding *other workout types* — a sculpt or
 *   yoga title simply has no cycling word, so it's dropped without the veto list needing to
 *   enumerate every kind of exercise.
 * - The veto list only has to catch titles that *do* mention cycling but aren't a class:
 *   "Sunday **ride** vlog", "my **cycling** story", "new **bike** unboxing".
 *
 * Terms match case-insensitively and on word boundaries, so "ride" doesn't fire on "bride" or
 * "stride". The two term lists are the intended tuning knobs; adjust them from real misses
 * rather than reworking the shape.
 *
 * [1]: docs/superpowers/specs/2026-07-19-content-catalog-expansion-design.md
 */
object ClassRelevance {

    /** Cycling vocabulary. Stems cover common inflections (ride/rides, cycle/cycled/cycling). */
    private val CYCLING = Regex(
        """\b(rides?|spin(ning)?|cycl(e|es|ed|ing)|bik(e|es|ed|ing)|saddle|cadence|peloton|rpm|watts?)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Non-class formats that can co-occur with a cycling word and must still be dropped. */
    private val VETO = Regex(
        """\b(vlog|haul|grwm|unboxing|podcast|recap|storytime|story|q&a|try[- ]on|day in my life)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun isCyclingTitle(title: String): Boolean =
        CYCLING.containsMatchIn(title) && !VETO.containsMatchIn(title)
}
