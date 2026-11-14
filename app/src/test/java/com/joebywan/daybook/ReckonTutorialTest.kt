package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.DigitKeyAction
import com.joebywan.daybook.core.PlacementStep
import com.joebywan.daybook.core.ReckonTraining
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.core.TutorialStep
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.puzzles.Reckon
import com.joebywan.daybook.puzzles.ReckonLogic
import com.joebywan.daybook.puzzles.ReckonState
import com.joebywan.daybook.puzzles.ReckonTeacher
import com.joebywan.daybook.puzzles.applyKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reckon's walkthrough, keys and Training course, each against the real state functions and the independent oracle. */
class ReckonTutorialTest {

    private val b0 = Reckon.tutorialBoard()

    @Test
    fun `the walkthrough board has exactly one answer, and it is the stored one`() {
        val sols = ReckonOracle.solutions(4, b0.cells, b0.cageOf, b0.ops, b0.targets, limit = 5)
        assertEquals(listOf(Reckon.TUTORIAL_SOLUTION), sols)
        // Every operation is on it, and the 18× L repeats its 3 in two squares that share no line.
        assertTrue(ReckonLogic.OPERATIONS.all { it in b0.ops })
        assertEquals(listOf(0, 4, 5), ReckonLogic.cages(b0.cageOf)[0])
        assertEquals(Reckon.TUTORIAL_SOLUTION[0], Reckon.TUTORIAL_SOLUTION[5])
        assertTrue(b0.isGiven(6) && b0.cells[6] == 4)
    }

    @Test
    fun `each walkthrough frame accepts its move, made by the real gestures, and rejects a wrong one`() {
        val frames = Reckon.tutorial
        assertEquals(8, frames.size)
        assertEquals(listOf(true, true, false, false, false, false, false, false), frames.map { it.judge == null && !it.freePlay })
        fun place(frame: Int, cell: Int, digit: Int, wrongCell: Int): ReckonState {
            val steps = frames[frame].steps!!
            val start = frames[frame].state as ReckonState
            assertEquals(TutorialStep.REJECT, steps(start.select(wrongCell)))
            val picked = start.select(cell)
            assertEquals(TutorialStep.PROGRESS, steps(picked))
            assertEquals(setOf(ReckonTeacher.pad(4, digit)), frames[frame].glow!!(picked).strong)
            assertEquals(TutorialStep.REJECT, steps(picked.enter(cell, digit % 4 + 1, false)))
            val done = picked.enter(cell, digit, false)
            assertEquals(TutorialStep.DONE, steps(done))
            if (frame + 1 < frames.size) assertEquals("frame ${frame + 1} starts where this one ends", done.cells, (frames[frame + 1].state as ReckonState).cells)
            return done
        }
        // 7+: two squares of 1 to 4 make 7 only as 3 + 4, and the square beside the 4 sits under the given 4.
        assertEquals("+", b0.ops[b0.cageOf[Reckon.TUTORIAL_PLUS]].toString())
        assertEquals(6 % 4, ReckonLogic.cages(b0.cageOf)[b0.cageOf[Reckon.TUTORIAL_PLUS]].first { it != Reckon.TUTORIAL_PLUS } % 4)
        place(2, Reckon.TUTORIAL_PLUS, 4, 10)
        // 3−: 4 less 1 either way round, and the row now has its 4.
        assertEquals(setOf(listOf(1, 4), listOf(4, 1)), ways(Reckon.TUTORIAL_MINUS).toSet())
        place(3, Reckon.TUTORIAL_MINUS, 1, 12)
        // 18× is only 3 × 2 × 3, the 2 at the bend.
        assertEquals(listOf(listOf(3, 2, 3)), ways(Reckon.TUTORIAL_BEND))
        val bent = place(4, Reckon.TUTORIAL_BEND, 2, 0)
        // The clash: a 4 above the given 4 turns red, a mistake, and tapping 4 again clears it.
        val clash = frames[5].steps!!
        val start = frames[5].state as ReckonState
        assertEquals(bent.cells, start.cells)
        assertEquals(TutorialStep.REJECT, clash(start))
        val picked = start.select(Reckon.TUTORIAL_WRONG)
        assertEquals(TutorialStep.PROGRESS, clash(picked))
        assertEquals(TutorialStep.REJECT, clash(picked.enter(Reckon.TUTORIAL_WRONG, 1, false)))
        val wrong = picked.enter(Reckon.TUTORIAL_WRONG, 4, false)
        assertEquals(TutorialStep.PROGRESS, clash(wrong))
        assertTrue(frames[5].glow!!(wrong).warning)
        assertEquals(setOf(Reckon.TUTORIAL_WRONG, 6), wrong.conflicts())
        assertTrue(Reckon.teach(wrong)!!.mistake)
        val cleared = wrong.enter(Reckon.TUTORIAL_WRONG, 4, false)
        assertEquals(TutorialStep.DONE, clash(cleared))
        // The pencil: a 1 in the same square (4÷ leaves it 1 or 4, and its column has the 4); a 4 there is refused.
        val pencil = frames[6].accepts!!
        val p0 = frames[6].state as ReckonState
        assertEquals(cleared.cells, p0.cells)
        assertEquals(Reckon.TUTORIAL_WRONG, p0.selected)
        assertEquals(setOf(listOf(4, 1), listOf(1, 4)), ways(Reckon.TUTORIAL_WRONG).toSet())
        assertFalse(pencil(p0.enter(Reckon.TUTORIAL_WRONG, 4, true)))
        val noted = p0.enter(Reckon.TUTORIAL_WRONG, Reckon.TUTORIAL_NOTED, true)
        assertTrue(pencil(noted))
        // Free play: hints alone finish it, with no fallback and no mistake.
        assertTrue(frames[7].freePlay)
        val free = frames[7].state as ReckonState
        assertEquals(noted.cells, free.cells)
        assertEquals(noted.notes, free.notes)
        var now = free
        repeat(20) {
            if (now.solved) return@repeat
            val d = Reckon.teach(now)!!
            assertFalse(d.fallback); assertFalse(d.mistake)
            now = d.apply(now) as ReckonState
        }
        assertTrue(now.solved)
        assertEquals(Reckon.TUTORIAL_SOLUTION, now.cells)
        for (f in frames) assertTrue("caption ${f.caption.length}: ${f.caption}", f.caption.length <= 170)
    }

    /** The ways of [square]'s cage on the walkthrough board, by brute force over 1 to 4 (the shape's repeat rule kept). */
    private fun ways(square: Int): List<List<Int>> {
        val sq = ReckonLogic.cages(b0.cageOf)[b0.cageOf[square]]
        val c = b0.cageOf[square]
        fun shares(a: Int, b: Int) = sq[a] / 4 == sq[b] / 4 || sq[a] % 4 == sq[b] % 4
        val all = sq.fold(listOf(emptyList<Int>())) { acc, _ -> acc.flatMap { p -> (1..4).map { p + it } } }
        return all.filter { d ->
            ReckonLogic.makes(b0.ops[c], b0.targets[c], d) && d.indices.none { a -> d.indices.any { b -> a < b && d[a] == d[b] && shares(a, b) } }
        }
    }

    @Test
    fun `keys reach the same states a tap does`() {
        val s = b0
        assertEquals(s.select(0), s.applyKey(DigitKeyAction.Move(0, 1), false)) // nothing selected: the first square
        val at = s.select(0)
        assertEquals(at.enter(0, 3, false), at.applyKey(DigitKeyAction.Digit(3), false))
        assertEquals(at.enter(0, 3, true), at.applyKey(DigitKeyAction.Digit(3), true))
        assertEquals(at.select(1), at.applyKey(DigitKeyAction.Move(0, 1), false))
        // Clear empties a digit, and rubs out a square's notes; on a given or an empty square it does nothing.
        val placed = at.enter(0, 3, false)
        assertEquals(0, placed.applyKey(DigitKeyAction.Clear, false)!!.cells[0])
        val noted = at.enter(0, 3, true)
        assertEquals(0, noted.applyKey(DigitKeyAction.Clear, false)!!.notes[0])
        assertNull(at.applyKey(DigitKeyAction.Clear, false))
        assertNull(s.select(6).applyKey(DigitKeyAction.Digit(1), false))
    }

    @Test
    fun `every practice is a real position whose next step is its technique, the walk's digits locked`() {
        for (t in ReckonTraining.techniques) {
            val list = ReckonTraining.practices(t).take(4).toList()
            assertEquals("practices for $t", 4, list.size)
            for (p in list) {
                val b = p.board as ReckonState
                val step = p.step as PlacementStep
                assertEquals(t, ReckonTeacher.deduce(b.size, b.cells, b.cageOf, b.ops, b.targets)!!.technique)
                assertEquals("the step's digit is the answer's", b.solution[step.cell], step.digit)
                assertTrue("every digit shown is a given", b.cells.indices.all { (b.cells[it] != 0) == b.isGiven(it) })
                assertEquals("one answer", 1, ReckonOracle.solutions(b.size, b.cells, b.cageOf, b.ops, b.targets).size)
                // A shown digit cannot be taken back.
                val shown = b.cells.indices.first { b.cells[it] != 0 }
                assertEquals(b, b.withCell(shown, 0))
            }
        }
    }

    @Test
    fun `the final board is a proved Expert board whose walk needs every technique`() {
        assertEquals(techniqueNamesOf("reckon").keys.toList(), ReckonTraining.techniques)
        // The first practice seed whose Expert board does: pinned so a change that moves it is seen.
        val first = (0 until 400).first { k ->
            ReckonTraining.board(k, Difficulty.EXPERT)?.let { ReckonTraining.techniquesUsed(it) == ReckonTraining.techniques.toSet() } == true
        }
        assertEquals(Training.seed(first), ReckonTraining.examSeed)
        assertNotNull(ReckonLogic.generateVerified(ReckonTraining.examSeed!!, Difficulty.EXPERT))
        assertEquals(ReckonTraining.techniques.toSet(), ReckonTraining.techniquesUsed(ReckonTraining.exam()!!))
    }
}
