package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.ui.stats.ThisWeek
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThisWeekTest {
    // A Tuesday, so the week runs back across the new year.
    private val today = LocalDate(2027, 1, 5)

    private fun c(day: LocalDate?, seconds: Int = 60, d: Difficulty = Difficulty.STANDARD, id: String = "sudoku") =
        Completion(id, d, day, seconds, 0)

    @Test fun `empty history is seven empty days and a fresh week`() {
        val days = ThisWeek.days(emptyList(), today)
        assertEquals((30..31).map { LocalDate(2026, 12, it) } + (1..5).map { LocalDate(2027, 1, it) }, days.map { it.day })
        assertEquals(List(7) { 0 }, days.map { it.solved })
        assertEquals("A fresh week: today's boards are ready.", ThisWeek.summary(days))
        assertNull(ThisWeek.trend(emptyList(), "sudoku", today))
    }

    @Test fun `the strip counts dated solves across the year boundary and leaves out practice and older days`() {
        val all = listOf(
            c(LocalDate(2026, 12, 30)), c(LocalDate(2026, 12, 30), id = "kings"), c(LocalDate(2027, 1, 5)),
            c(LocalDate(2026, 12, 29)), // seven days back: last week
            c(null), c(null), // practice and levels carry no day
        )
        val days = ThisWeek.days(all, today)
        assertEquals(listOf(2, 0, 0, 0, 0, 0, 1), days.map { it.solved })
        assertEquals("2 days played, 3 daily boards solved.", ThisWeek.summary(days))
        assertEquals("1 day played, 1 daily board solved.", ThisWeek.summary(ThisWeek.days(listOf(c(today)), today)))
    }

    @Test fun `weeks end today, take the median, and leave a gap as null`() {
        val all = listOf(
            c(today, 100), c(LocalDate(2026, 12, 30), 300), c(LocalDate(2026, 12, 31), 200), // week 0: 100, 200, 300
            c(LocalDate(2026, 12, 29), 50), // week 1 (seven days back)
            c(LocalDate(2026, 11, 16), 70), // week 7 (50 days back), the oldest kept
            c(LocalDate(2026, 11, 10), 999), // week 8 (56 days back): outside
            c(today, 5, Difficulty.HARD), c(today, 5, id = "kings"), c(null, 5), // other tier, other puzzle, practice
        )
        assertEquals(listOf(70, null, null, null, null, null, 50, 200), ThisWeek.weeklyMedians(all, "sudoku", Difficulty.STANDARD, today))
    }

    @Test fun `a trend needs three weeks and takes the most solved tier`() {
        val two = listOf(c(today), c(LocalDate(2026, 12, 20)))
        assertNull(ThisWeek.trend(two, "sudoku", today))
        val three = two + c(LocalDate(2026, 12, 10), 90) + List(3) { c(today, 40, Difficulty.HARD) }
        val t = ThisWeek.trend(three, "sudoku", today)!!
        assertEquals(Difficulty.STANDARD, t.difficulty)
        assertEquals(60, t.recent)
        // A tie goes to the easier tier.
        val tie = three + c(LocalDate(2026, 12, 20), 40, Difficulty.HARD) + c(LocalDate(2026, 12, 10), 40, Difficulty.HARD)
        assertEquals(Difficulty.STANDARD, ThisWeek.trend(tie, "sudoku", today)!!.difficulty)
    }
}
