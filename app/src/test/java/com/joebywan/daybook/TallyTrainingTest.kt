package com.joebywan.daybook

import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.MoveDrill
import com.joebywan.daybook.core.TallyTraining
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.puzzles.Tally
import com.joebywan.daybook.puzzles.TallyLogic
import com.joebywan.daybook.puzzles.TallyState
import com.joebywan.daybook.puzzles.TallyTeacher
import com.joebywan.daybook.ui.Route
import com.joebywan.daybook.ui.decodeRoute
import com.joebywan.daybook.ui.encodeRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tally's course (docs/TRAINING_SPEC.md). Each practice's lines are classified here by plain sums and by listing every
 * subset of a line's open numbers, nothing borrowed from the teacher; the board's answers are counted by [TallyOracle].
 */
class TallyTrainingTest {

    private fun lines(n: Int) = (0 until n).map { r -> List(n) { r * n + it } } + (0 until n).map { c -> List(n) { it * n + c } }

    private fun targets(s: TallyState) = lines(s.size).map { l -> l.filter { s.keep[it] == '1' }.sumOf { s.digitAt(it) } }

    /**
     * The rung a person finds in this line: 0 already made, 1 a number too big, 2 a number the line can't lose, 3 all
     * that is left makes it, 4 what every way agrees on; null when no way settles anything. The order is the hints' own
     * within a line: a line whose remainder makes its target exactly reads as rung 3, though rung 2 also holds there.
     */
    private fun rungOf(s: TallyState, line: List<Int>, target: Int): Int? {
        val open = line.filter { s.marks[it] == TallyLogic.UNMARKED }
        if (open.isEmpty()) return null
        val kept = line.filter { s.marks[it] == TallyLogic.KEPT }.sumOf { s.digitAt(it) }
        val left = line.filter { s.marks[it] != TallyLogic.REMOVED }.sumOf { s.digitAt(it) }
        val ways = (0 until (1 shl open.size)).filter { m -> kept + open.indices.filter { m shr it and 1 == 1 }.sumOf { s.digitAt(open[it]) } == target }
        val settles = open.indices.any { x -> ways.all { it shr x and 1 == 1 } || ways.none { it shr x and 1 == 1 } }
        if (ways.isEmpty() || !settles) return null
        return when {
            kept == target -> 0
            left == target -> 3
            open.any { s.digitAt(it) > target - kept } -> 1
            open.any { s.digitAt(it) > left - target } -> 2
            else -> 4
        }
    }

    /** The open squares of [line] that every subset making [target] keeps, or none does. */
    private fun settledIn(s: TallyState, line: List<Int>, target: Int): List<Int> {
        val open = line.filter { s.marks[it] == TallyLogic.UNMARKED }
        val kept = line.filter { s.marks[it] == TallyLogic.KEPT }.sumOf { s.digitAt(it) }
        val ways = (0 until (1 shl open.size)).filter { m -> kept + open.indices.filter { m shr it and 1 == 1 }.sumOf { s.digitAt(open[it]) } == target }
        return open.filterIndexed { x, _ -> ways.all { it shr x and 1 == 1 } || ways.none { it shr x and 1 == 1 } }
    }

    @Test fun `every practice is a real position whose next step is its technique, with no simpler step on it`() {
        assertTrue(TallyTraining.drill is MoveDrill)
        for ((rung, t) in TallyTraining.techniques.withIndex()) {
            val list = TallyTraining.practices(t).take(6).toList()
            assertEquals("practices for $t", 6, list.size)
            assertEquals("distinct boards for $t", 6, list.map { it.board }.distinct().size)
            for (p in list) {
                val b = p.board as TallyState
                val n = b.size
                assertEquals(t, p.step.technique)
                assertEquals("the hint agrees", t, Tally.teach(b)!!.technique)
                val goals = targets(b)
                val answers = TallyOracle.answerStrings(n, List(n * n) { b.digitAt(it) }, goals.take(n), goals.drop(n), cap = 2)
                assertEquals("one answer", listOf(b.keep), answers)
                assertTrue("walked marks agree with it", b.marks.indices.all {
                    b.marks[it] == TallyLogic.UNMARKED || (b.marks[it] == TallyLogic.KEPT) == (b.keep[it] == '1')
                })
                val rungs = lines(n).mapIndexedNotNull { k, l -> rungOf(b, l, goals[k]) }
                if (rung < 5) assertEquals("$t is the simplest step by counting", rung, rungs.min())
                else assertTrue("a what-if board has no line step", rungs.isEmpty())
                val d = p.step as Deduction
                assertTrue(d.targets.isNotEmpty() && d.targets.all { b.marks[it] == TallyLogic.UNMARKED })
                val after = d.apply(b) as TallyState
                for (i in d.targets) assertEquals(b.keep[i] == '1', after.marks[i] == TallyLogic.KEPT)
            }
        }
    }

    @Test fun `a move is right when it makes the step, wrong when the answer disagrees, and otherwise plays on`() {
        var others = 0
        var lucky = 0
        var counted = 0
        for (t in TallyTraining.techniques) {
            for (p in TallyTraining.practices(t).take(3)) {
                val b = p.board as TallyState
                val d = p.step as Deduction
                fun with(i: Int, c: Char) = b.copy(marks = b.marks.substring(0, i) + c + b.marks.substring(i + 1))
                fun right(i: Int) = if (b.keep[i] == '1') TallyLogic.KEPT else TallyLogic.REMOVED
                fun wrong(i: Int) = if (b.keep[i] == '1') TallyLogic.REMOVED else TallyLogic.KEPT
                assertEquals(true, TallyTraining.judge(p, d.apply(b)))
                val open = b.marks.indices.filter { b.marks[it] == TallyLogic.UNMARKED }
                // A wrong removal, or a wrong circle, is wrong wherever it is.
                for (i in listOf(open.first(), d.targets.first())) assertEquals(false, TallyTraining.judge(p, with(i, wrong(i))))
                open.firstOrNull { b.keep[it] == '0' }?.let { assertEquals(false, TallyTraining.judge(p, with(it, TallyLogic.KEPT))) }
                // Every other line whose rung, counted here, is this technique: making what its ways settle is right.
                val rung = TallyTraining.techniques.indexOf(t)
                val goals = targets(b)
                for ((k, line) in lines(b.size).withIndex()) if (rungOf(b, line, goals[k]) == rung && line.none { it in d.targets }) {
                    val made = settledIn(b, line, goals[k]).fold(b) { s, i -> s.copy(marks = s.marks.substring(0, i) + right(i) + s.marks.substring(i + 1)) }
                    assertEquals("another line's $t", true, TallyTraining.judge(p, made))
                    counted++
                }
                // The teacher's own list agrees; a right mark no instance justifies plays on.
                val same = TallyTeacher.steps(b.size, TallyLogic.digitArray(b), TallyLogic.targets(b), b.marks).map { Tally.deduction(b, it) }.filter { it.technique == t }
                same.firstOrNull { it.targets != d.targets }?.let { assertEquals(true, TallyTraining.judge(p, it.apply(b))); others++ }
                val justified = same.flatMap { it.targets }.toSet()
                open.firstOrNull { it !in justified }?.let { assertNull(TallyTraining.judge(p, with(it, right(it)))); lucky++ }
                d.targets.first().takeIf { f -> d.targets.size > 1 && same.none { it.targets == setOf(f) } }
                    ?.let { assertNull(TallyTraining.judge(p, with(it, right(it)))) }
            }
        }
        assertTrue("some practice has another instance to find", others > 0)
        assertTrue("and a true mark nothing justifies", lucky > 0)
        assertTrue("and another instance by counting", counted > 0)
    }

    @Test fun `the final board is a proved Expert board, the first whose walk needs every technique`() {
        assertEquals(techniqueNamesOf("tally").keys.toList(), TallyTraining.techniques)
        val index = (0 until 60).first { k ->
            TallyTraining.techniquesUsed(Tally.generate(Training.seed(k), Difficulty.EXPERT)) == TallyTraining.techniques.toSet()
        }
        assertEquals(Training.seed(index), TallyTraining.examSeed)
        assertNotNull(TallyLogic.generateVerified(TallyTraining.examSeed!!, Difficulty.EXPERT))
        assertEquals(TallyTraining.techniques.toSet(), TallyTraining.techniquesUsed(TallyTraining.exam()!!))
    }

    @Test fun `tally routes round-trip`() {
        listOf(Route.Training(puzzle = "tally"), Route.Training("every-way", puzzle = "tally"), Route.Training("exam", puzzle = "tally"))
            .forEach { assertEquals(it, decodeRoute(encodeRoute(it))) }
    }
}
