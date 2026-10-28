package com.joebywan.daybook

import com.joebywan.daybook.core.KingsTraining
import com.joebywan.daybook.core.SudokuTraining
import com.joebywan.daybook.puzzles.KingsState
import com.joebywan.daybook.puzzles.Mark
import com.joebywan.daybook.puzzles.SudokuState
import com.joebywan.daybook.puzzles.SudokuTeacher
import com.joebywan.daybook.ui.training.Verdict
import com.joebywan.daybook.ui.training.verdictLine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
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
        val other = (5 * 9 until 6 * 9).single { s.cells[it] == 0 }
        assertNotEquals(p.targetCell(), other)
        val filled = s.copy(cells = s.cells.toMutableList().also { it[other] = 7 })

        assertEquals(true, SudokuTraining.judge(p, filled))
        val made = SudokuTraining.made(p, filled)
        assertEquals(setOf(other), made.targets)
        assertEquals("That's it. 7 goes there.", verdictLine(Verdict.RIGHT, p.step, made))
        assertEquals("That's it, with a hint, so it doesn't count. 7 goes there.", verdictLine(Verdict.HINTED, p.step, made))

        // The step's own square keeps the step's explanation, and a wrong digit still shows what was there to find.
        val own = s.copy(cells = s.cells.toMutableList().also { it[p.targetCell()] = 4 })
        assertSame(p.step, SudokuTraining.made(p, own))
        assertEquals("That's it. ${p.step.explanation}", verdictLine(Verdict.RIGHT, p.step, SudokuTraining.made(p, own)))
        val wrong = s.copy(cells = s.cells.toMutableList().also { it[other] = 3 })
        assertEquals(false, SudokuTraining.judge(p, wrong))
        assertEquals("Not quite. ${p.step.explanation}", verdictLine(Verdict.WRONG, p.step, p.step))
    }

    @Test fun anotherKingsInstanceIsAnsweredAboutItself() {
        // A move drill: a crown on another last square of the same technique is right, and the verdict explains and
        // outlines that crown, not the teacher's pick.
        var others = 0
        val t = KingsTraining.techniques.first()
        for (p in KingsTraining.practices(t).take(12)) {
            val b = p.board as KingsState
            for (c in b.marks.indices.filter { b.marks[it] == Mark.EMPTY && it !in p.step.targets }) {
                val crowned = b.copy(marks = b.marks.toMutableList().also { it[c] = Mark.KING })
                if (KingsTraining.judge(p, crowned) != true) continue
                others++
                val made = KingsTraining.made(p, crowned)
                assertTrue("crown $c is the made step's", c in made.targets)
                assertNotEquals(p.step.explanation, made.explanation)
                assertEquals("That's it. ${made.explanation}", verdictLine(Verdict.RIGHT, p.step, made))
            }
        }
        assertTrue("some practice has another instance ($others)", others > 0)
    }

    private fun com.joebywan.daybook.core.Course.Practice.targetCell() = step.targets.single()
}
