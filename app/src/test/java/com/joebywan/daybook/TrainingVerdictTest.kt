package com.joebywan.daybook

import com.joebywan.daybook.core.SudokuTraining
import com.joebywan.daybook.puzzles.SudokuState
import com.joebywan.daybook.puzzles.SudokuTeacher
import com.joebywan.daybook.ui.training.Verdict
import com.joebywan.daybook.ui.training.verdictLine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner on 1.19: a full-house practice with a box and a row each one square short. They filled the row; it counted,
 * but the slot said "That's it." and then explained the box's square, which read as "wrong, do the box instead".
 */
class TrainingVerdictTest {
    @Test fun aRightDigitElsewhereIsAnsweredAboutItsOwnSquare() = runBlocking {
        // Full-house practice 5: the step is the middle-left box's last square (row 5, column 1, a 4); row 6 is also one
        // short, at column 7, where a 7 goes.
        val p = SudokuTraining.practice(SudokuTeacher.FULL_HOUSE, 5)!!
        val s = p.board as SudokuState
        val row = 5 * 9 until 6 * 9
        val other = row.single { s.cells[it] == 0 }
        assertNotEquals(p.step.cell, other)
        assertEquals(7, s.solution[other])

        val line = verdictLine(Verdict.RIGHT, p.step, other, 7)
        assertEquals("That's it: 7 goes there.", line)
        assertFalse(line.contains(p.step.explanation))
        assertTrue(verdictLine(Verdict.HINTED, p.step, other, 7).startsWith("That's right, 7 goes there"))
        // The step's own square keeps the step's explanation, and a wrong digit still shows what was there to find.
        assertEquals("That's it. ${p.step.explanation}", verdictLine(Verdict.RIGHT, p.step, p.step.cell, p.step.digit))
        assertEquals("Not quite. ${p.step.explanation}", verdictLine(Verdict.WRONG, p.step, other, 3))
    }
}
