package com.joebywan.daybook

import com.joebywan.daybook.core.ChessTraining
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.KingsTraining
import com.joebywan.daybook.core.MosaicTraining
import com.joebywan.daybook.core.PipesTraining
import com.joebywan.daybook.core.SudokuTraining
import com.joebywan.daybook.puzzles.ChessRules
import com.joebywan.daybook.puzzles.ChessState
import com.joebywan.daybook.puzzles.ChessTeacher
import com.joebywan.daybook.puzzles.KingsState
import com.joebywan.daybook.puzzles.Mark
import com.joebywan.daybook.puzzles.MosaicState
import com.joebywan.daybook.puzzles.Pipes
import com.joebywan.daybook.puzzles.PipesState
import com.joebywan.daybook.puzzles.PipesTeacher
import com.joebywan.daybook.puzzles.SudokuState
import com.joebywan.daybook.puzzles.SudokuTeacher
import com.joebywan.daybook.ui.training.Verdict
import com.joebywan.daybook.ui.training.verdictLine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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

    @Test fun anotherPipesTileIsAnsweredAboutItself() {
        // Pipes' own drill: another tile the technique settles, turned to its way, is explained and outlined itself.
        var others = 0
        for (t in PipesTraining.techniques) for (p in PipesTraining.practices(t).take(8)) {
            val b = p.board as PipesState
            for (d in PipesTeacher.steps(b.width, b.height, b.cells, t).map(Pipes::deduction)) {
                if (d.targets == p.step.targets) continue
                val turned = d.apply(b)
                assertEquals(true, PipesTraining.judge(p, turned))
                val made = PipesTraining.made(p, turned)
                assertEquals(d.targets, made.targets)
                assertEquals(d.explanation, made.explanation)
                others++
            }
            assertSame(p.step, PipesTraining.made(p, (p.step as Deduction).apply(b)))
        }
        assertTrue("some practice has another tile ($others)", others > 0)
    }

    @Test fun anotherMosaicFillIsAnsweredAboutItselfAndAnAsideIsNoted() {
        // MosaicDrill: a right fill that isn't the teacher's is explained and outlined as the player's; a fill that keeps
        // the limit but isn't the technique gets the note (and no verdict); a losing fill is wrong, with no note.
        var others = 0
        var asides = 0
        for (t in MosaicTraining.techniques) for (p in MosaicTraining.practices(t).take(6)) {
            val b = p.board as MosaicState
            val fills = b.cells.indices.flatMap { c -> (0 until b.colours).filter { it != b.cells[c] }.map { c to it } }
                .distinctBy { (c, k) -> b.flood(c, k).cells }
            for ((c, k) in fills) {
                val after = b.flood(c, k)
                when (MosaicTraining.judge(p, after)) {
                    true -> {
                        val made = MosaicTraining.made(p, after)
                        assertTrue("fill $c is the made step's", c in made.targets)
                        if (after.cells == ((p.step as Deduction).apply(b) as MosaicState).cells) assertSame(p.step, made)
                        else { assertTrue(made.targets != p.step.targets || made.explanation != p.step.explanation); others++ }
                        assertNull(MosaicTraining.aside(p, after))
                    }
                    null -> if (MosaicTraining.aside(p, after) != null) asides++
                    false -> assertNull(MosaicTraining.aside(p, after))
                }
            }
        }
        assertTrue("some practice has another right fill ($others)", others > 0)
        assertTrue("some practice has a keeping fill outside the technique ($asides)", asides > 0)
    }

    @Test fun anotherChessKeyIsAnsweredAboutItself() {
        // Chess: every key is right (most positions have one; a few mates in one have several); one the teacher didn't pick is explained as the key played, outlining its squares.
        var others = 0
        for (p in ChessTraining.practices(ChessTeacher.MATE_IN_ONE).take(80)) {
            val b = p.board as ChessState
            val line = ChessTeacher.replay(b)!!
            for (m in ChessRules.legalMoves(line.position)) {
                val after = b.play(m)
                if (ChessTraining.judge(p, after) != true) continue
                val made = ChessTraining.made(p, after)
                assertTrue(made.targets.containsAll(setOf(ChessRules.from(m), ChessRules.to(m))))
                if (made.targets != p.step.targets) { assertNotEquals(p.step.explanation, made.explanation); others++ }
            }
        }
        assertTrue("some practice has a second key ($others)", others > 0)
    }

    private fun com.joebywan.daybook.core.Course.Practice.targetCell() = step.targets.single()
}
