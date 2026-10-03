package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Streak
import com.joebywan.daybook.core.streakOf
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.data.Stats
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StreakTest {
    private val start = 20000L
    private fun day(i: Int) = LocalDate.fromEpochDays(start + i)

    /** A history as a 0/1 string, index 0 = day 0. Today is the day after the last character unless given. */
    private fun run(pattern: String, todayIndex: Int = pattern.length): Streak =
        streakOf(pattern.indices.filter { pattern[it] == '1' }.map { day(it) }.toSet(), day(todayIndex))

    /**
     * Deliberately unlike the implementation: walks forward keeping the flags of the current run in a
     * list and asks, each day, whether the last seven (or fewer) hold three misses.
     */
    private fun oracle(flags: List<Boolean>): Pair<Int, Int> {
        var run = mutableListOf<Boolean>()
        var best = 0
        for (played in flags) {
            if (run.isEmpty() && !played) continue
            run.add(played)
            if (run.takeLast(7).count { !it } > 2) {
                best = maxOf(best, run.count { it })
                run = mutableListOf()
            }
        }
        val cur = run.count { it }
        return cur to maxOf(best, cur)
    }

    @Test
    fun `exactly five of seven stays alive and four of seven dies`() {
        val five = run("1111111" + "0110111")   // last seven days: 5 played
        assertTrue(five.alive)
        assertEquals(12, five.current)
        val four = run("1111111" + "0110110")   // last seven days: 4 played
        assertFalse(four.alive)
        assertEquals(11, four.best)
    }

    @Test
    fun `a hundred day streak cannot hide a three day gap`() {
        val dead = run("1".repeat(100) + "000")
        assertEquals(0, dead.current)
        assertEquals(100, dead.best)
        assertTrue(dead.lapsed)
        val forgiven = run("1".repeat(100) + "00")   // two days off is the most a streak survives
        assertEquals(100, forgiven.current)
        assertTrue(forgiven.alive)
    }

    @Test
    fun `today is not a miss until it ends`() {
        val waiting = run("1111111" + "00", 9)       // yesterday and the day before missed; today still open
        assertEquals(7, waiting.current)
        assertTrue(waiting.atRisk)                   // a third miss today would end it
        val played = streakOf((0..6).map { day(it) }.toSet() + day(9), day(9))
        assertEquals(8, played.current)
        assertFalse(played.atRisk)
        val untouched = run("1111111" + "0", 8)      // one miss so far, today open: safe
        assertFalse(untouched.atRisk)
    }

    @Test
    fun `new players may miss two days since their first play`() {
        assertEquals(1, run("1").current)
        assertEquals(2, run("101").current)
        assertEquals(2, run("1001").current)          // two misses: still alive
        val dead = run("10001")                       // three misses in a row end it; the later play starts again
        assertEquals(1, dead.current)
        assertEquals(1, dead.best)
        assertEquals(0, run("").current)
        assertFalse(run("").lapsed)
    }

    @Test
    fun `best streak survives a lapse and future days are ignored`() {
        val s = run("1111" + "0000" + "11")
        assertEquals(2, s.current)
        assertEquals(4, s.best)
        assertEquals(1, streakOf(setOf(day(0), day(50)), day(1)).current)
    }

    @Test
    fun `agrees with an independent model on thousands of random histories`() {
        val rnd = Random(7)
        repeat(4000) {
            val flags = List(rnd.nextInt(1, 70)) { _ -> rnd.nextDouble() < 0.2 + 0.8 * rnd.nextDouble() }
            val playedToday = rnd.nextBoolean()
            val all = flags + playedToday
            val (cur, best) = oracle(if (playedToday) all else flags)
            val days = all.indices.filter { all[it] }.map { day(it) }.toSet()
            val s = streakOf(days, day(all.size - 1))
            assertEquals("$all", cur, s.current)
            assertEquals("$all", best, s.best)
            // at risk: alive, today still open, and an unplayed today would be the fatal miss
            val risky = !playedToday && cur > 0 && oracle(flags + false).first == 0
            assertEquals("$all", risky, s.atRisk)
        }
    }

    @Test
    fun `completions feed it by day, practice never counts, and an old save still reads`() {
        val all = listOf(
            Completion("kings", Difficulty.HARD, day(0), 60, 0),
            Completion("sudoku", Difficulty.HARD, day(0), 60, 0),
            Completion("sudoku", Difficulty.HARD, null, 60, 0),
            Completion("kings", Difficulty.HARD, day(1), 60, 0),
        )
        assertEquals(2, Stats.streak(all, day(1)).current)
        val legacy = "kings|HARD|$start|75|1"   // the on-disk format is unchanged
        assertEquals(1, Stats.streak(listOfNotNull(Completion.decode(legacy)), day(0)).current)
    }
}
