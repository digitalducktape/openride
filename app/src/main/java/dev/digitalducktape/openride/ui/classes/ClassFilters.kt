package dev.digitalducktape.openride.ui.classes

import dev.digitalducktape.openride.core.content.ChannelSection
import dev.digitalducktape.openride.core.content.ContentCategory
import dev.digitalducktape.openride.core.content.Video
import kotlin.random.Random

/** Which category chip is selected on the Classes tab. */
enum class CategoryFilter(val label: String) {
    All("All"),
    Workout("Workout"),
    Scenic("Scenic"),
}

/** How the flat class list is ordered. */
enum class ClassSort(val label: String) {
    Newest("Newest"),
    Oldest("Oldest"),
    Random("Random"),
}

/**
 * Class-length buckets. Bounds are inclusive at [minSec] and exclusive at [maxSec] so the
 * buckets tile the range without overlapping.
 *
 * A video whose duration is unknown matches only [Any] — pretending it belongs in a specific
 * bucket would put classes in front of a rider who asked for a specific length and didn't get
 * one. (Duration is unknown only on the RSS-fallback path; see `YouTubeContentRepository`.)
 */
enum class LengthFilter(val label: String, val minSec: Int, val maxSec: Int?) {
    Any("Any", 0, null),
    Under20("< 20 min", 0, 20 * 60),
    From20To30("20–30 min", 20 * 60, 30 * 60),
    From30To45("30–45 min", 30 * 60, 45 * 60),
    Over45("45+ min", 45 * 60, null),
}

/**
 * The Classes tab's current filter selection.
 *
 * @property isDefaultBrowse true while the tab should show its per-creator rows. Category
 *   narrows which rows appear, so it doesn't leave browse mode; a sort or length choice is a
 *   question about the whole catalog and switches to the flat grid.
 * @property hideTaken when true, classes the active profile has already finished are hidden.
 *   It's a filter *within* whichever layout is showing — it deliberately does not feed
 *   [isDefaultBrowse], so hiding completed classes narrows the browse rows in place rather than
 *   forcing the flat grid.
 */
data class ClassFilters(
    val category: CategoryFilter = CategoryFilter.All,
    val sort: ClassSort = ClassSort.Newest,
    val length: LengthFilter = LengthFilter.Any,
    val hideTaken: Boolean = false,
) {
    val isDefaultBrowse: Boolean
        get() = sort == ClassSort.Newest && length == LengthFilter.Any
}

/** Pure filtering/sorting over loaded sections — no state, so it's directly testable. */
object ClassFiltering {

    fun matchesLength(video: Video, length: LengthFilter): Boolean {
        if (length == LengthFilter.Any) return true
        val duration = video.durationSec ?: return false
        return duration >= length.minSec && (length.maxSec == null || duration < length.maxSec)
    }

    private fun forCategory(sections: List<ChannelSection>, category: CategoryFilter): List<ChannelSection> =
        when (category) {
            CategoryFilter.All -> sections
            CategoryFilter.Workout -> sections.filter { it.category == ContentCategory.Workout }
            CategoryFilter.Scenic -> sections.filter { it.category == ContentCategory.Scenic }
        }

    /**
     * The creator rows to show, in configured order: narrowed to [ClassFilters.category] and, when
     * [ClassFilters.hideTaken] is on, with the profile's already-finished classes ([taken] video
     * ids) removed from each row.
     */
    fun rows(
        sections: List<ChannelSection>,
        filters: ClassFilters,
        taken: Set<String>,
    ): List<ChannelSection> =
        forCategory(sections, filters.category).map { section ->
            if (filters.hideTaken) {
                section.copy(videos = section.videos.filterNot { it.id in taken })
            } else {
                section
            }
        }

    /** Every matching video across creators, ordered by [ClassFilters.sort]. */
    fun grid(
        sections: List<ChannelSection>,
        filters: ClassFilters,
        random: Random,
        taken: Set<String>,
    ): List<Video> {
        val matching = forCategory(sections, filters.category)
            .flatMap { it.videos }
            .filter { matchesLength(it, filters.length) }
            .filter { !filters.hideTaken || it.id !in taken }
        return when (filters.sort) {
            ClassSort.Newest -> matching.sortedByDescending { it.publishedEpochMs }
            ClassSort.Oldest -> matching.sortedBy { it.publishedEpochMs }
            ClassSort.Random -> matching.shuffled(random)
        }
    }
}
