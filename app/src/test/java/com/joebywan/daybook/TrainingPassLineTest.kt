package com.joebywan.daybook

import com.joebywan.daybook.core.SudokuTraining
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.core.TrainingRecord
import com.joebywan.daybook.puzzles.SudokuTeacher
import com.joebywan.daybook.ui.training.passLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The owner on 1.19: three in a row on "The last square in a row, column or box", already passed in the pilot, and no
 * congratulation. A run that reaches [Training.RUN] is congratulated every time; only the achievements are once.
 */
class TrainingPassLineTest {
    private val c = SudokuTraining
    private val t = SudokuTeacher.FULL_HOUSE

    @Test fun congratulatedOnFirstPass() =
        assertEquals("Passed! 3 in a row.", passLine(c, emptySet(), t, Training.RUN))

    @Test fun congratulatedAgainWhenAlreadyPassed() =
        assertEquals("Passed! 3 in a row.", passLine(c, setOf(TrainingRecord.entry(c.id, t)), t, Training.RUN))

    @Test fun onlyWhenTheRunReachesThree() {
        assertNull(passLine(c, emptySet(), t, Training.RUN - 1))
        assertNull(passLine(c, emptySet(), t, Training.RUN + 1))
    }

    @Test fun achievementOnlyTheFirstTime() {
        val othersPassed = (c.techniques - t).map { TrainingRecord.entry(c.id, it) }.toSet()
        assertEquals("Passed! 3 in a row. Achievement: Sudoku trained!", passLine(c, othersPassed, t, Training.RUN))
        assertEquals("Passed! 3 in a row.", passLine(c, othersPassed + TrainingRecord.entry(c.id, t), t, Training.RUN))
    }
}
