package dev.digitalducktape.openride.ui.classes

import dev.digitalducktape.openride.core.content.ChannelSection
import dev.digitalducktape.openride.core.content.ContentCategory
import dev.digitalducktape.openride.core.content.Video
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassFiltersTest {

    private fun video(id: String, durationSec: Int?, publishedEpochMs: Long) = Video(
        id = id,
        title = "Class $id",
        thumbnailUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
        channelName = "Channel",
        durationSec = durationSec,
        publishedEpochMs = publishedEpochMs,
    )

    private val workout = ChannelSection(
        sourceId = 1L,
        channelId = "UCworkout",
        channelName = "Workout Channel",
        category = ContentCategory.Workout,
        videos = listOf(
            video("w15", 15 * 60, 3_000L),
            video("w25", 25 * 60, 1_000L),
            video("w50", 50 * 60, 5_000L),
        ),
        refreshFailed = false,
    )

    private val scenic = ChannelSection(
        sourceId = 2L,
        channelId = "UCscenic",
        channelName = "Scenic Channel",
        category = ContentCategory.Scenic,
        videos = listOf(
            video("s40", 40 * 60, 4_000L),
            video("sNull", null, 2_000L),
        ),
        refreshFailed = false,
    )

    private val sections = listOf(workout, scenic)

    @Test
    fun `Any length matches everything including unknown durations`() {
        assertTrue(ClassFiltering.matchesLength(video("a", null, 0L), LengthFilter.Any))
        assertTrue(ClassFiltering.matchesLength(video("b", 12 * 60, 0L), LengthFilter.Any))
    }

    @Test
    fun `length buckets are inclusive at their lower bound and exclusive at their upper`() {
        assertTrue(ClassFiltering.matchesLength(video("a", 19 * 60, 0L), LengthFilter.Under20))
        assertFalse(ClassFiltering.matchesLength(video("b", 20 * 60, 0L), LengthFilter.Under20))
        assertTrue(ClassFiltering.matchesLength(video("c", 20 * 60, 0L), LengthFilter.From20To30))
        assertFalse(ClassFiltering.matchesLength(video("d", 30 * 60, 0L), LengthFilter.From20To30))
        assertTrue(ClassFiltering.matchesLength(video("e", 30 * 60, 0L), LengthFilter.From30To45))
        assertTrue(ClassFiltering.matchesLength(video("f", 45 * 60, 0L), LengthFilter.Over45))
    }

    @Test
    fun `a video with unknown duration matches no specific bucket`() {
        val unknown = video("x", null, 0L)

        assertFalse(ClassFiltering.matchesLength(unknown, LengthFilter.Under20))
        assertFalse(ClassFiltering.matchesLength(unknown, LengthFilter.Over45))
    }

    @Test
    fun `rows keeps every section when the category is All`() {
        assertEquals(listOf(1L, 2L), ClassFiltering.rows(sections, CategoryFilter.All).map { it.sourceId })
    }

    @Test
    fun `rows keeps only the matching category`() {
        assertEquals(listOf(2L), ClassFiltering.rows(sections, CategoryFilter.Scenic).map { it.sourceId })
        assertEquals(listOf(1L), ClassFiltering.rows(sections, CategoryFilter.Workout).map { it.sourceId })
    }

    @Test
    fun `grid sorts newest first by default`() {
        val filters = ClassFilters(sort = ClassSort.Newest)

        val grid = ClassFiltering.grid(sections, filters, Random(0))

        assertEquals(listOf("w50", "s40", "w15", "sNull", "w25"), grid.map { it.id })
    }

    @Test
    fun `grid sorts oldest first`() {
        val grid = ClassFiltering.grid(sections, ClassFilters(sort = ClassSort.Oldest), Random(0))

        assertEquals(listOf("w25", "sNull", "w15", "s40", "w50"), grid.map { it.id })
    }

    @Test
    fun `grid random ordering is a permutation of the same videos`() {
        val grid = ClassFiltering.grid(sections, ClassFilters(sort = ClassSort.Random), Random(7))

        assertEquals(5, grid.size)
        assertEquals(
            setOf("w15", "w25", "w50", "s40", "sNull"),
            grid.map { it.id }.toSet(),
        )
    }

    @Test
    fun `grid random ordering is stable for a given seed`() {
        val first = ClassFiltering.grid(sections, ClassFilters(sort = ClassSort.Random), Random(7))
        val second = ClassFiltering.grid(sections, ClassFilters(sort = ClassSort.Random), Random(7))

        assertEquals(first.map { it.id }, second.map { it.id })
    }

    @Test
    fun `grid applies category and length together`() {
        val filters = ClassFilters(
            category = CategoryFilter.Workout,
            sort = ClassSort.Newest,
            length = LengthFilter.From20To30,
        )

        assertEquals(listOf("w25"), ClassFiltering.grid(sections, filters, Random(0)).map { it.id })
    }

    @Test
    fun `default browse is only the untouched filter set`() {
        assertTrue(ClassFilters().isDefaultBrowse)
        assertTrue(ClassFilters(category = CategoryFilter.Scenic).isDefaultBrowse)
        assertFalse(ClassFilters(sort = ClassSort.Oldest).isDefaultBrowse)
        assertFalse(ClassFilters(length = LengthFilter.Over45).isDefaultBrowse)
    }
}
