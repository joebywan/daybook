package com.joebywan.daybook

import com.joebywan.daybook.ui.archive.ArchiveCalendar
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveCalendarTest {
    private val today = LocalDate(2026, 10, 23)

    @Test fun `only days from the first daily to today can be played`() {
        assertTrue(ArchiveCalendar.playable(LocalDate(2026, 1, 1), today))
        assertTrue(ArchiveCalendar.playable(today, today))
        assertFalse(ArchiveCalendar.playable(LocalDate(2025, 12, 31), today))
        assertFalse(ArchiveCalendar.playable(LocalDate(2026, 10, 24), today))
        assertEquals(LocalDate(2026, 1, 1), ArchiveCalendar.firstMonth())
    }

    @Test fun `opens on the day just played, else the last day played, else this month`() {
        val played = listOf(LocalDate(2026, 3, 9), LocalDate(2026, 7, 14))
        assertEquals(LocalDate(2026, 2, 1), ArchiveCalendar.opening(LocalDate(2026, 2, 20), played, today))
        assertEquals(LocalDate(2026, 7, 1), ArchiveCalendar.opening(null, played, today))
        assertEquals(LocalDate(2026, 10, 1), ArchiveCalendar.opening(null, emptyList(), today))
        // A clock set back, or a day from before the first daily, still opens inside the bounds.
        assertEquals(LocalDate(2026, 7, 1), ArchiveCalendar.opening(null, played + LocalDate(2027, 1, 5), today))
        assertEquals(LocalDate(2026, 1, 1), ArchiveCalendar.opening(LocalDate(2025, 6, 1), played, today))
        assertEquals(LocalDate(2026, 10, 1), ArchiveCalendar.opening(LocalDate(2027, 6, 1), played, today))
    }
}
