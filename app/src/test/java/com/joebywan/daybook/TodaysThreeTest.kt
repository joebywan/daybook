package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.TodaysThree
import com.joebywan.daybook.core.earnedAchievements
import com.joebywan.daybook.data.Completion
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Today's three ([TodaysThree]): the date alone decides it, and its achievement. */
class TodaysThreeTest {
    private val day = LocalDate(2026, 10, 25)

    @Test fun `the three are pinned to the date`() {
        // Worked out by hand from the lists and the epoch day (20751): a change here moves everyone's three.
        assertEquals(listOf("sets", "lits", "cipher"), TodaysThree.of(day))
        assertEquals(listOf("mosaic", "tower", "words"), TodaysThree.of(LocalDate(2026, 10, 26)))
        assertEquals(listOf("tents", "nonogram", "wordwheel"), TodaysThree.of(LocalDate(2026, 10, 27)))
    }

    @Test fun `every puzzle has a slot, each slot keeps to its kind, and a year never repeats a day's three`() {
        val ids = PuzzleRegistry.all.map { it.id }
        assertTrue("a listed puzzle that is not registered", (TodaysThree.QUICK + TodaysThree.WORDS).all { it in ids })
        val year = (0 until 365).map { TodaysThree.of(LocalDate.fromEpochDays(day.toEpochDays() + it)) }
        year.forEach { (quick, mid, words) ->
            assertTrue(quick in TodaysThree.QUICK)
            assertTrue(mid !in TodaysThree.QUICK && mid !in TodaysThree.WORDS)
            assertTrue(words in TodaysThree.WORDS)
        }
        assertEquals("every puzzle comes up", ids.toSet(), year.flatten().toSet())
        assertEquals(365, year.toSet().size)
    }

    @Test fun `the achievement needs all three on one tier, on that day`() {
        val three = TodaysThree.of(day)
        fun solve(id: String, tier: Difficulty, on: LocalDate? = day) = Completion(id, tier, on, 60, 0)
        fun earned(h: List<Completion>) = earnedAchievements(h, day).any { it.id == "todaysThree" }
        val two = three.take(2).map { solve(it, Difficulty.HARD) }
        assertFalse(earned(two))
        assertFalse("another tier", earned(two + solve(three[2], Difficulty.STANDARD)))
        assertFalse("a practice board", earned(two + solve(three[2], Difficulty.HARD, on = null)))
        assertFalse("another day", earned(two + solve(three[2], Difficulty.HARD, on = LocalDate(2026, 10, 26))))
        assertTrue(earned(two + solve(three[2], Difficulty.HARD)))
    }
}
