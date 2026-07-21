package dev.digitalducktape.openride.core.content

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassRelevanceTest {

    @Test
    fun `keeps titles with a cycling term`() {
        assertTrue(ClassRelevance.isCyclingTitle("45 MIN RIDE | Introducing a new combo"))
        assertTrue(ClassRelevance.isCyclingTitle("SANDLOT // 40 Minute Spin Class • HIIT Cycling Workout"))
        assertTrue(ClassRelevance.isCyclingTitle("30 minute Indoor Cycling MTB Seceda Panorama Tour"))
        assertTrue(ClassRelevance.isCyclingTitle("The most beautiful place I've ever cycled"))
        assertTrue(ClassRelevance.isCyclingTitle("Sellaronda Bike Day 2026"))
    }

    @Test
    fun `matching is case-insensitive`() {
        assertTrue(ClassRelevance.isCyclingTitle("get fit fast | 20 min high energy spin class"))
    }

    @Test
    fun `drops other-workout titles that carry no cycling term`() {
        assertFalse(ClassRelevance.isCyclingTitle("WEEKLY WORKOUT: 45 MIN SCULPT CLASS | Full Body"))
        assertFalse(ClassRelevance.isCyclingTitle("WEEKLY WORKOUT | Lower Body Strength, dumbbell only"))
        assertFalse(ClassRelevance.isCyclingTitle("30 Min Full Body Yoga Flow"))
    }

    @Test
    fun `drops junk and personal titles that carry no cycling term`() {
        assertFalse(ClassRelevance.isCyclingTitle("April 9, 2026"))
        assertFalse(ClassRelevance.isCyclingTitle("https://my.playbookapp.io/gabriella-guevara"))
        assertFalse(ClassRelevance.isCyclingTitle("MARRIED 10 YEARS | Our story and then some..."))
    }

    @Test
    fun `vetoes a cycling term that is really a non-class format`() {
        assertFalse(ClassRelevance.isCyclingTitle("WEEKLY VLOG EP. 15 | My favorite ride of the week"))
        assertFalse(ClassRelevance.isCyclingTitle("My cycling story: how I started teaching"))
        assertFalse(ClassRelevance.isCyclingTitle("New bike unboxing and first impressions"))
    }

    @Test
    fun `matches on word boundaries, not substrings`() {
        // "bride" contains "ride", "bicycle-free" contains "cycle" — neither is a cycling class.
        assertFalse(ClassRelevance.isCyclingTitle("Getting ready to be a bride"))
        assertFalse(ClassRelevance.isCyclingTitle("A stride-by-stride running breakdown"))
    }
}
