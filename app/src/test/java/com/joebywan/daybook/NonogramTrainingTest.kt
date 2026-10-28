package com.joebywan.daybook

import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.MoveDrill
import com.joebywan.daybook.core.NonogramTraining
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.puzzles.Nonogram
import com.joebywan.daybook.puzzles.NonogramLogic
import com.joebywan.daybook.puzzles.NonogramState
import com.joebywan.daybook.puzzles.NonogramTeacher
import com.joebywan.daybook.ui.Route
import com.joebywan.daybook.ui.decodeRoute
import com.joebywan.daybook.ui.encodeRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Nonogram's course (docs/TRAINING_SPEC.md). Each practice's lines are classified here by plain counting (packing the
 * runs left and right, and every layout tried by [NonogramOracle]), nothing borrowed from the teacher; the board's
 * answers are counted by [NonogramOracle] too.
 */
class NonogramTrainingTest {

    private fun lines(s: NonogramState) = (0 until s.height).map { r -> List(s.width) { r * s.width + it } } +
        (0 until s.width).map { c -> List(s.height) { it * s.width + c } }

    private fun clueOf(s: NonogramState, line: List<Int>) = NonogramOracle.runsOf(line.map { s.solution[it] == '1' })

    /** The first starts of [clue]'s runs packed to the left of [n] squares. */
    private fun packed(clue: List<Int>): List<Int> = clue.runningFold(0) { at, run -> at + run + 1 }.dropLast(1)

    /**
     * The rung a person finds in this line, simplest first: 0 nowhere to slide, 1 every run already there, 2 the overlap
     * of a blank line, 3 whatever the marks leave; null when the line settles nothing.
     */
    private fun rungOf(s: NonogramState, line: List<Int>): Int? {
        val n = line.size
        val clue = clueOf(s, line)
        val unknown = line.indices.filter { s.cells[line[it]] == NonogramLogic.UNMARKED }
        if (unknown.isEmpty()) return null
        if (clue.isNotEmpty() && clue.sum() + clue.size - 1 == n) return 0
        if (NonogramOracle.runsOf(line.map { s.cells[it] == NonogramLogic.FILLED }) == clue) return 1
        val left = packed(clue)
        val right = packed(clue.reversed()).zip(clue.reversed()).map { (at, run) -> n - at - run }.reversed()
        val covered = clue.indices.flatMap { k -> (right[k] until left[k] + clue[k]) }
        if (covered.any { it in unknown }) return 2
        val marks = IntArray(n) {
            when (s.cells[line[it]]) {
                NonogramLogic.FILLED -> NonogramLogic.FILL
                NonogramLogic.CROSSED -> NonogramLogic.CROSS
                else -> NonogramLogic.UNKNOWN
            }
        }
        return if (NonogramOracle.bruteForced(clue, marks)!!.any { it != NonogramLogic.UNKNOWN }) 3 else null
    }

    /** The squares of [line] every layout of its clue still possible agrees on, by [NonogramOracle]'s brute force. */
    private fun forcedIn(s: NonogramState, line: List<Int>): List<Int> {
        val marks = IntArray(line.size) {
            when (s.cells[line[it]]) {
                NonogramLogic.FILLED -> NonogramLogic.FILL
                NonogramLogic.CROSSED -> NonogramLogic.CROSS
                else -> NonogramLogic.UNKNOWN
            }
        }
        val f = NonogramOracle.bruteForced(clueOf(s, line), marks)!!
        return line.filterIndexed { x, _ -> f[x] != NonogramLogic.UNKNOWN }
    }

    @Test fun `every practice is a real position whose next step is its technique, with no simpler step on it`() {
        assertTrue(NonogramTraining.drill is MoveDrill)
        for ((rung, t) in NonogramTraining.techniques.withIndex()) {
            val list = NonogramTraining.practices(t).take(6).toList()
            assertEquals("practices for $t", 6, list.size)
            assertEquals("distinct boards for $t", 6, list.map { it.board }.distinct().size)
            for (p in list) {
                val b = p.board as NonogramState
                assertEquals(t, p.step.technique)
                assertEquals("the hint agrees", t, Nonogram.teach(b)!!.technique)
                val all = lines(b)
                val rows = all.take(b.height).map { clueOf(b, it) }
                val cols = all.drop(b.height).map { clueOf(b, it) }
                assertEquals("one answer", 1, NonogramOracle.countAnswers(b.width, b.height, rows, cols))
                assertTrue("walked marks agree with it", b.cells.indices.all {
                    b.cells[it] == NonogramLogic.UNMARKED || (b.cells[it] == NonogramLogic.FILLED) == (b.solution[it] == '1')
                })
                val rungs = all.mapNotNull { rungOf(b, it) }
                assertTrue("$t board has a simpler step", rungs.all { it >= rung })
                assertTrue("$t board has its own step by counting", rung in rungs)
                val d = p.step as Deduction
                assertTrue(d.targets.isNotEmpty() && d.targets.all { b.cells[it] == NonogramLogic.UNMARKED })
                val after = d.apply(b) as NonogramState
                for (i in d.targets) assertEquals(b.solution[i] == '1', after.cells[i] == NonogramLogic.FILLED)
            }
        }
    }

    @Test fun `a move is right when it makes the step, wrong when no picture keeps it, and otherwise plays on`() {
        var others = 0
        var lucky = 0
        var counted = 0
        for (t in NonogramTraining.techniques) {
            for (p in NonogramTraining.practices(t).take(3)) {
                val b = p.board as NonogramState
                val d = p.step as Deduction
                fun with(i: Int, c: Char) = b.copy(cells = b.cells.substring(0, i) + c + b.cells.substring(i + 1))
                fun right(i: Int) = if (b.solution[i] == '1') NonogramLogic.FILLED else NonogramLogic.CROSSED
                fun wrong(i: Int) = if (b.solution[i] == '1') NonogramLogic.CROSSED else NonogramLogic.FILLED
                assertEquals(true, NonogramTraining.judge(p, d.apply(b)))
                val open = b.cells.indices.filter { b.cells[it] == NonogramLogic.UNMARKED }
                // A wrong fill, or a wrong cross, is wrong wherever it is.
                for (i in listOf(open.first(), d.targets.first())) assertEquals(false, NonogramTraining.judge(p, with(i, wrong(i))))
                open.firstOrNull { b.solution[it] == '1' }?.let { assertEquals(false, NonogramTraining.judge(p, with(it, NonogramLogic.CROSSED))) }
                // Every other line whose rung, counted here, is this technique: making what it settles is right.
                val rung = NonogramTraining.techniques.indexOf(t)
                for (line in lines(b)) if (rungOf(b, line) == rung && line.none { it in d.targets }) {
                    val made = forcedIn(b, line).fold(b) { s, i -> s.copy(cells = s.cells.substring(0, i) + right(i) + s.cells.substring(i + 1)) }
                    assertEquals("another line's $t", true, NonogramTraining.judge(p, made))
                    counted++
                }
                // The teacher's own list agrees; a right mark no instance justifies plays on.
                val same = NonogramTeacher.steps(b.width, b.height, NonogramLogic.rowClues(b), NonogramLogic.colClues(b), b.cells).map { Nonogram.deduction(b, it) }.filter { it.technique == t }
                same.firstOrNull { it.targets != d.targets }?.let { assertEquals(true, NonogramTraining.judge(p, it.apply(b))); others++ }
                val justified = same.flatMap { it.targets }.toSet()
                open.firstOrNull { it !in justified }?.let { assertNull(NonogramTraining.judge(p, with(it, right(it)))); lucky++ }
                d.targets.first().takeIf { f -> d.targets.size > 1 && same.none { it.targets == setOf(f) } }
                    ?.let { assertNull(NonogramTraining.judge(p, with(it, right(it)))) }
            }
        }
        assertTrue("some practice has another instance to find", others > 0)
        assertTrue("and a true mark nothing justifies", lucky > 0)
        assertTrue("and another instance by counting", counted > 0)
    }

    @Test fun `the final board is a proved Expert board, the first whose walk needs every technique`() {
        assertEquals(techniqueNamesOf("nonogram").keys.toList(), NonogramTraining.techniques)
        val index = (0 until 60).first { k ->
            NonogramTraining.techniquesUsed(Nonogram.generate(Training.seed(k), Difficulty.EXPERT)) == NonogramTraining.techniques.toSet()
        }
        assertEquals(Training.seed(index), NonogramTraining.examSeed)
        assertNotNull(NonogramLogic.generateVerified(NonogramTraining.examSeed!!, Difficulty.EXPERT))
        assertEquals(NonogramTraining.techniques.toSet(), NonogramTraining.techniquesUsed(NonogramTraining.exam()!!))
    }

    @Test fun `nonogram routes round-trip`() {
        listOf(Route.Training(puzzle = "nonogram"), Route.Training("overlap", puzzle = "nonogram"), Route.Training("exam", puzzle = "nonogram"))
            .forEach { assertEquals(it, decodeRoute(encodeRoute(it))) }
    }
}
