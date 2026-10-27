package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.SudokuTraining
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.puzzles.SudokuState
import com.joebywan.daybook.core.TrainingRecord
import com.joebywan.daybook.core.earnedAchievements
import com.joebywan.daybook.core.newlyTrained
import com.joebywan.daybook.puzzles.Sudoku
import com.joebywan.daybook.puzzles.SudokuTeacher
import com.joebywan.daybook.ui.Route
import com.joebywan.daybook.ui.backFrom
import com.joebywan.daybook.ui.decodeRoute
import com.joebywan.daybook.ui.encodeRoute
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Training (docs/TRAINING_SPEC.md): the practice boards are what they claim, the final board needs everything. */
class SudokuTrainingTest {

    /** Independent of the teacher: the singles on a grid, by plain counting (no candidates struck by any pattern). */
    private class Singles(cells: List<Int>) {
        private val units: List<List<Int>> = List(9) { r -> List(9) { r * 9 + it } } +
            List(9) { c -> List(9) { it * 9 + c } } +
            List(9) { b -> List(9) { (b / 3 * 3 + it / 3) * 9 + b % 3 * 3 + it % 3 } }
        private fun sees(a: Int, b: Int) = a != b && units.any { a in it && b in it }
        private fun fits(cells: List<Int>, i: Int, d: Int) = cells[i] == 0 && (0 until 81).none { sees(i, it) && cells[it] == d }

        val fullHouse = units.any { u -> u.count { cells[it] == 0 } == 1 }
        val naked = (0 until 81).any { i -> cells[i] == 0 && (1..9).count { fits(cells, i, it) } == 1 }
        val hidden = units.any { u -> (1..9).any { d -> u.none { cells[it] == d } && u.count { fits(cells, it, d) } == 1 } }
    }

    @Test fun `every practice is a real position whose next step is its technique, and simpler steps are absent`() {
        for (t in SudokuTraining.techniques) {
            val list = SudokuTraining.practices(t).take(8).toList()
            assertEquals("practices for $t", 8, list.size)
            assertEquals("distinct boards for $t", 8, list.map { (it.board as SudokuState).cells }.distinct().size)
            for (p in list) {
                val b = p.board as SudokuState
                assertEquals(t, p.step.technique)
                assertEquals(t, SudokuTeacher.deduce(b.cells)!!.technique)
                assertEquals("the step's digit is the answer's", b.solution[p.step.cell], p.step.digit)
                assertEquals(0, b.cells[p.step.cell])
                assertTrue("every digit shown is a given", b.cells.indices.all { (b.cells[it] != 0) == b.givens[it] })
                assertTrue("shown digits agree with the answer", b.cells.indices.all { b.cells[it] == 0 || b.cells[it] == b.solution[it] })
                assertEquals("one answer", 1, Sudoku.countSolutions(b.cells))
                val s = Singles(b.cells)
                when (SudokuTraining.techniques.indexOf(t)) {
                    0 -> assertTrue(s.fullHouse)
                    3 -> assertTrue(!s.hidden && !s.fullHouse && s.naked)
                    in 4..7 -> assertTrue("$t board has a single", !s.hidden && !s.naked && !s.fullHouse)
                }
            }
        }
    }

    @Test fun `the final board is a proved Expert board whose walk needs every technique`() {
        assertEquals(Training.seed(219), SudokuTraining.examSeed)
        assertNotNull(Sudoku.generateVerified(SudokuTraining.examSeed!!, Difficulty.EXPERT))
        assertEquals(SudokuTraining.techniques.toSet(), SudokuTraining.techniquesUsed(SudokuTraining.exam()!!))
    }

    @Test fun `every technique has an explanation`() {
        assertEquals(techniqueNamesOf("sudoku").keys.toList(), SudokuTraining.techniques)
        assertEquals(8, SudokuTraining.techniques.size)
    }

    @Test fun `passing every technique, then the final board, earns the two training achievements once each`() {
        val today = LocalDate(2026, 10, 26)
        fun got(t: Set<String>) = earnedAchievements(emptyList(), today, training = t).map { it.id }.toSet()
        val all = SudokuTraining.techniques.map { TrainingRecord.entry("sudoku", it) }.toSet()
        assertTrue(got(emptySet()).isEmpty())
        assertFalse("sudokuTrained" in got(all - all.first()))
        assertEquals(listOf("sudokuTrained"), newlyTrained(all - TrainingRecord.entry("sudoku", "what-if"), "sudoku", "what-if").map { it.id })
        assertEquals(setOf("sudokuTrained"), got(all))
        assertEquals(listOf("sudokuGraduate"), newlyTrained(all, "sudoku", Training.EXAM).map { it.id })
        assertEquals(setOf("sudokuTrained", "sudokuGraduate"), got(all + TrainingRecord.entry("sudoku", "exam")))
        assertTrue(newlyTrained(all, "sudoku", "what-if").isEmpty())
    }

    @Test fun `training routes round-trip, read the pilot's, and back goes where they were opened from`() {
        listOf(
            Route.Training(), Route.Training(puzzle = "cages"), Route.Training("naked-pair", puzzle = "sudoku"),
            Route.Training("exam", puzzle = "regions"), Route.Training("what-if", fromHint = true, puzzle = "sudoku"),
            Route.Training(puzzle = "inequality", fromHint = true), Route.Training("only-place", puzzle = "inequality", listFromHint = true),
        ).forEach { assertEquals(it, decodeRoute(encodeRoute(it))) }
        // Strings the pilot wrote: a practice was Sudoku's.
        assertEquals(Route.Training("naked-pair", puzzle = "sudoku"), decodeRoute("training|naked-pair|false"))
        assertEquals(Route.Training("what-if", true, "sudoku"), decodeRoute("training|what-if|true"))
        assertEquals(Route.Training(), decodeRoute("training||false"))
        assertEquals(Route.Settings, backFrom(Route.Training()))
        assertEquals(Route.Training(), backFrom(Route.Training(puzzle = "cages")))
        assertEquals(Route.Training(puzzle = "sudoku"), backFrom(Route.Training("naked-pair", puzzle = "sudoku")))
        // A practice opened from a list the rules sheet opened goes back to that list, which goes back to the game.
        assertEquals(Route.Training(fromHint = true, puzzle = "regions"), backFrom(Route.Training("last-square", puzzle = "regions", listFromHint = true)))
    }
}
