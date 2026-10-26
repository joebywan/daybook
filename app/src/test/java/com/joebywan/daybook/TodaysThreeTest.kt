package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.TodaysThree
import com.joebywan.daybook.core.earnedAchievements
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.data.PuzzleOrder
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
        // Mid-weight steps (d mod 45 + d / 45): 20751 is 6 + 461 = 467, index 8 of 17 (467 mod 17).
        assertEquals(listOf("sets", "regions", "wordwheel"), TodaysThree.of(day))
        assertEquals(listOf("mosaic", "cages", "target"), TodaysThree.of(LocalDate(2026, 10, 26)))
        assertEquals(listOf("tents", "dominoes", "cipher"), TodaysThree.of(LocalDate(2026, 10, 27)))
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

    @Test fun `a newcomer's starter three keeps the quick slot and draws the rest from the gentlest`() {
        // 20751 mod 4 = 3 (mines), mod 3 = 0 (words); the quick slot is the shared three's.
        assertEquals(listOf("sets", "mines", "words"), TodaysThree.starter(day))
        val mids = PuzzleOrder.GENTLEST.filter { it !in TodaysThree.QUICK && it !in TodaysThree.WORDS }
        assertEquals("the gentlest mid-weights", mids.take(TodaysThree.STARTER_MID.size), TodaysThree.STARTER_MID)
        assertEquals("the gentlest word ones", PuzzleOrder.GENTLEST.filter { it in TodaysThree.WORDS }.take(3), TodaysThree.STARTER_WORDS)
        val year = (0 until 365).map { LocalDate.fromEpochDays(day.toEpochDays() + it) }
        year.forEach { d ->
            val (quick, mid, words) = TodaysThree.starter(d)
            assertEquals(TodaysThree.of(d)[0], quick)
            assertTrue(mid in TodaysThree.STARTER_MID && words in TodaysThree.STARTER_WORDS)
        }
        assertEquals("every starter puzzle comes up", (TodaysThree.STARTER_MID + TodaysThree.STARTER_WORDS).toSet(),
            year.flatMap { TodaysThree.starter(it).drop(1) }.toSet())
    }

    @Test fun `the achievement also takes the day's starter three`() {
        val starter = TodaysThree.starter(day)
        assertTrue(starter != TodaysThree.of(day))
        val h = starter.map { Completion(it, Difficulty.STANDARD, day, 60, 0) }
        assertTrue(earnedAchievements(h, day).any { it.id == "todaysThree" })
        assertFalse(earnedAchievements(h.dropLast(1), day).any { it.id == "todaysThree" })
    }
}
