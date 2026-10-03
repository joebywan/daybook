package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.ui.play.finishPraise
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinishPraiseTest {
    private val today = LocalDate(2026, 10, 10)
    private fun d(back: Int) = LocalDate.fromEpochDays(today.toEpochDays() - back)
    private fun c(day: LocalDate?, secs: Int = 100, hints: Int = 0, tier: Difficulty = Difficulty.HARD, id: String = "nonogram") =
        Completion(id, tier, day, secs, hints)
    private fun run(history: List<Completion>, solve: Completion) = finishPraise(history, solve, today, "Nonogram")
    private fun days(vararg back: Int) = back.map { c(d(it), 500) }

    @Test fun firstEverSolveIsDayOneAndNoRecordClaim() {
        val p = run(emptyList(), c(today))
        assertEquals("Day one", p.title)
        assertTrue(p.lines.none { it.contains("fastest") })
    }

    @Test fun streakExtendsOnFirstSolveOfTheDayOnly() {
        val h = days(1, 2, 3)
        assertEquals("4 day streak", run(h, c(today)).title)
        assertNull(run(h + c(today), c(today)).title)
    }

    @Test fun milestonesAreLoud() {
        assertTrue(run(days(1, 2, 3, 4, 5, 6), c(today)).big)
        assertFalse(run(days(1, 2, 3, 4, 5), c(today)).big)
        assertTrue(run((1..29).map { c(d(it), 500) }, c(today)).big)
        assertTrue(run((1..99).map { c(d(it), 500) }, c(today)).big)
    }

    @Test fun windowSavedMissedYesterday() {
        val p = run(days(2, 3, 4), c(today))
        assertEquals("4 day streak", p.title)
        assertEquals(listOf("You're back, and your streak is still going."), p.lines)
    }

    @Test fun lapsedRestartWelcomesBack() {
        assertEquals("Welcome back", run(days(20, 21, 22), c(today)).title)
    }

    @Test fun practiceAndArchiveDaysGetNoStreakLine() {
        assertNull(run(days(1, 2), c(null)).title)
        assertNull(run(days(1, 2), c(d(3))).title)
    }

    @Test fun fastestNeedsHistoryAndBeatsStrictly() {
        val h = listOf(c(d(5), 100), c(d(6), 90))
        assertEquals("Your fastest Hard Nonogram yet.", run(h, c(null, 80)).lines.first())
        assertTrue(run(h, c(null, 90)).lines.none { it.contains("fastest") })
        assertTrue(run(h, c(null, 95)).lines.none { it.contains("fastest") })
        val other = listOf(c(d(5), 10, tier = Difficulty.EXPERT), c(d(6), 10, id = "x"))
        assertTrue(run(other, c(null, 80)).lines.none { it.contains("fastest") })
    }

    @Test fun percentileIsOwnHistoryRoundedDown() {
        val h = listOf(50, 60, 70, 80, 90, 100, 110).map { c(null, it) }
        assertTrue(run(h, c(null, 85)).lines.none { it.startsWith("Faster than") })
        assertTrue(run(h, c(null, 65)).lines.none { it.startsWith("Faster than") }) // 5 of 7 = 71%
        assertEquals("Faster than 85% of your own Hard solves.", run(h, c(null, 55)).lines.first()) // 6 of 7
        assertTrue(run(h.take(4), c(null, 55)).lines.none { it.startsWith("Faster than") })
    }

    @Test fun hintsNeverScoldAndNoHintsIsPraised() {
        assertTrue(run(emptyList(), c(null, 100, hints = 3)).lines.single().startsWith("Hints are how a move sticks"))
        assertEquals(listOf("No hints needed."), run(emptyList(), c(null, 100, hints = 0)).lines)
    }

    @Test fun longSolvesGetPerseverance() {
        assertEquals("That one fought back, and you stuck with it.", run(emptyList(), c(null, 700)).lines.first())
        val h = (1..5).map { c(null, 200) }
        assertTrue(run(h, c(null, 300)).lines.none { it.startsWith("That one") })
        assertTrue(run(h, c(null, 450)).lines.first().startsWith("That one"))
        assertTrue(run(h, c(null, 120)).lines.none { it.startsWith("That one") })
    }

    @Test fun neverMoreThanOneLine() {
        val h = days(1, 2, 3) + (1..6).map { c(null, 900) }
        assertEquals(1, run(h, c(today, 100)).lines.size)
        assertEquals(1, run(h, c(null, 100)).lines.size)
    }
}
