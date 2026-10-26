package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.data.Completion
import com.joebywan.daybook.ui.home.REMINDER_HOURS
import com.joebywan.daybook.ui.home.hourLabel
import com.joebywan.daybook.ui.home.reminderOfferDue
import com.joebywan.daybook.ui.home.reminderText
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderTest {

    @Test
    fun theTextGreetsForTheTimeOfDayAndSaysHowMuchIsLeft() {
        val tail = ": your puzzles are ready when you are."
        assertEquals("Good evening! About five hours left today$tail", reminderText(19))
        assertEquals("Good morning! The whole day is ahead of you$tail", reminderText(8))
        assertEquals("Good afternoon! The whole day is ahead of you$tail", reminderText(12))
        assertEquals("Good evening! About seven hours left today$tail", reminderText(17))
        assertEquals("Good evening! About three hours left today$tail", reminderText(21))
        assertEquals("Good evening! About an hour left today$tail", reminderText(22, 50))
        assertEquals("Good evening! Not long left today$tail", reminderText(23, 30))
        // Every minute of the day reads as a sentence: no "no hours", no number past the words.
        for (minute in 0 until 24 * 60) {
            val text = reminderText(minute / 60, minute % 60)
            assertFalse(text, "no hours" in text)
        }
    }

    @Test
    fun noLineSoundsLikeAThreat() {
        for (minute in 0 until 24 * 60 step 15) {
            val text = reminderText(minute / 60, minute % 60).lowercase()
            for (word in listOf("streak", "lose", "miss", "don't", "hurry", "only")) assertFalse(text, word in text)
        }
    }

    @Test
    fun theOfferWaitsForASolveTodayOnASecondDay() {
        val today = LocalDate(2026, 10, 26)
        val earlier = LocalDate(2026, 10, 20)
        fun solve(day: LocalDate?) = Completion("sudoku", Difficulty.STANDARD, day, 60, 0)
        assertFalse("no solves", reminderOfferDue(emptyList(), today))
        assertFalse("first day only", reminderOfferDue(listOf(solve(today), solve(today)), today))
        assertFalse("an earlier day but nothing today", reminderOfferDue(listOf(solve(earlier)), today))
        assertFalse("practice games have no day", reminderOfferDue(listOf(solve(null), solve(today)), today))
        assertTrue(reminderOfferDue(listOf(solve(earlier), solve(today)), today))
    }

    @Test
    fun theHoursReadAsAClock() {
        assertEquals(listOf("8 am", "12 pm", "5 pm", "7 pm", "9 pm"), REMINDER_HOURS.map(::hourLabel))
        assertEquals("12 am", hourLabel(0))
    }
}
