package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.SeedHash
import com.joebywan.daybook.data.Completion
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.datetime.LocalDate as KLocalDate

/**
 * The app's dates moved from `java.time.LocalDate` to `kotlinx.datetime.LocalDate` so the web build
 * can compile the same files. Nothing on disk stores a date object — completions store an epoch-day
 * number — so what has to hold is that the two libraries count days identically, and that a
 * completion written by a build from before the move still reads back as the same day.
 */
class CompletionFormatTest {

    @Test
    fun `kotlinx and java_time count epoch days identically for four centuries`() {
        var date = java.time.LocalDate.of(1900, 1, 1)
        val end = java.time.LocalDate.of(2300, 12, 31)
        while (!date.isAfter(end)) {
            val k = KLocalDate(date.year, date.monthValue, date.dayOfMonth)
            assertEquals("$date", date.toEpochDay(), k.toEpochDays())
            assertEquals("$date", k, KLocalDate.fromEpochDays(date.toEpochDay()))
            date = date.plusDays(1)
        }
    }

    @Test
    fun `a completion saved by the java_time build decodes to the same day`() {
        // Exactly what the old encode() wrote for kings, Hard, 2026-09-30, 1:15, one hint:
        // the day as java.time's toEpochDay().
        val legacy = "kings|HARD|${java.time.LocalDate.of(2026, 9, 30).toEpochDay()}|75|1"
        val decoded = Completion.decode(legacy)
        assertEquals(Completion("kings", Difficulty.HARD, KLocalDate(2026, 9, 30), 75, 1), decoded)
        assertEquals(legacy, decoded?.encode())
        assertEquals("kings|HARD|20726|75|1", legacy)

        val practice = "sudoku|EXPERT|-|300|0"
        assertEquals(practice, Completion.decode(practice)?.encode())
    }

    @Test
    fun `daily seeds are unchanged by the date type`() {
        var date = java.time.LocalDate.of(2026, 1, 1)
        repeat(3 * 366) {
            val k = KLocalDate.fromEpochDays(date.toEpochDay())
            for (tier in Difficulty.entries) {
                assertEquals(
                    SeedHash.daily(date.toEpochDay(), "kings", tier),
                    DailySeed.seedFor(k, "kings", tier),
                )
            }
            date = date.plusDays(1)
        }
    }
}
