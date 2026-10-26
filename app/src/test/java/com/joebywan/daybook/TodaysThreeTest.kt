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
        // 20751 mod 11 = 5 (mosaic), mod 13 = 3 (tower, in the registry's order), mod 7 = 3 (target).
        assertEquals(listOf("mosaic", "tower", "target"), TodaysThree.of(day))
        assertEquals(listOf("kings", "nonogram", "cipher"), TodaysThree.of(LocalDate(2026, 10, 26)))
        assertEquals(listOf("tents", "inequality", "codeword"), TodaysThree.of(LocalDate(2026, 10, 27)))
    }

    @Test fun `the slots' lengths are pairwise coprime, so each puzzle comes round every list length`() {
        val ids = PuzzleRegistry.all.map { it.id }
        val mid = ids.filter { it !in TodaysThree.QUICK && it !in TodaysThree.WORDS }
        tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
        val sizes = listOf(TodaysThree.QUICK.size, mid.size, TodaysThree.WORDS.size)
        assertEquals(listOf(11, 13, 7), sizes)
        assertTrue(sizes.indices.all { i -> sizes.indices.all { j -> i == j || gcd(sizes[i], sizes[j]) == 1 } })
        val days = (0 until 1001).map { TodaysThree.of(LocalDate.fromEpochDays(day.toEpochDays() + it)) }
        assertEquals("no set repeats in 1,001 days", 1001, days.toSet().size)
        ids.forEach { id ->
            val on = days.indices.filter { id in days[it] }
            val gap = on.zipWithNext { a, b -> b - a }.max()
            assertTrue("$id waits $gap days", gap <= 13)
        }
        // A new puzzle joining the mid-weight list makes it 14, sharing 7 with the words: the slot then skips at each
        // 77-day turn, and still no set repeats within a year.
        val more = ids + "new"
        assertEquals(365, (0 until 365).map { TodaysThree.of(LocalDate.fromEpochDays(day.toEpochDays() + it), more) }.toSet().size)
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
        // 20751 mod 3 = 0 (sudoku), mod 4 = 3 (target); the quick slot is the shared three's.
        assertEquals(listOf("mosaic", "sudoku", "target"), TodaysThree.starter(day))
        val mids = PuzzleOrder.GENTLEST.filter { it !in TodaysThree.QUICK && it !in TodaysThree.WORDS }
        assertEquals("the gentlest mid-weights", mids.take(TodaysThree.STARTER_MID.size), TodaysThree.STARTER_MID)
        assertEquals("the gentlest word ones", PuzzleOrder.GENTLEST.filter { it in TodaysThree.WORDS }.take(TodaysThree.STARTER_WORDS.size), TodaysThree.STARTER_WORDS)
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
