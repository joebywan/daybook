package com.joebywan.daybook

import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.KingsTraining
import com.joebywan.daybook.core.MoveDrill
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.puzzles.Kings
import com.joebywan.daybook.puzzles.KingsState
import com.joebywan.daybook.puzzles.Mark
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
 * Kings' course (docs/TRAINING_SPEC.md), the first on the move drill. Each practice is checked against the rungs of the
 * ladder re-derived here by plain counting over open squares (nothing borrowed from the teacher), and against
 * [KingsOracle]'s brute-force count of answers.
 */
class KingsTrainingTest {

    /** The rungs below the what-if on one board, by counting: which of them has a step to take. */
    private class Rungs(s: KingsState) {
        val n = s.size
        private val region = s.region
        private val kings = s.marks.indices.filter { s.marks[it] == Mark.KING }
        private fun rules(k: Int, c: Int) =
            c / n == k / n || c % n == k % n || region[c] == region[k] || kotlin.math.abs(c / n - k / n) <= 1 && kotlin.math.abs(c % n - k % n) <= 1
        val open: Set<Int> = s.marks.indices.filter { c -> s.marks[c] == Mark.EMPTY && kings.none { rules(it, c) } }.toSet()

        // Unsatisfied houses, each a set of squares: regions, rows, columns.
        private fun free(cells: List<Int>) = cells.none { it in kings }
        val regions = region.distinct().map { r -> region.indices.filter { region[it] == r } }.filter(::free)
        val rows = (0 until n).map { r -> List(n) { r * n + it } }.filter(::free)
        val cols = (0 until n).map { c -> List(n) { it * n + c } }.filter(::free)
        private val houses = regions + rows + cols
        private fun openIn(h: List<Int>) = h.filter { it in open }

        val last = houses.any { openIn(it).size == 1 }

        /** Every last-square instance: the one open square of a house that has one. */
        val lastSquares: Set<Int> = houses.mapNotNull { h -> openIn(h).singleOrNull() }.toSet()

        /** Every locked instance, as the squares it crosses out: a colour inside one line, or a line inside one colour. */
        val locked: Set<Set<Int>> = buildSet {
            for (lines in listOf(rows, cols)) {
                for (r in regions) {
                    val line = lines.singleOrNull { l -> openIn(r).all { it in l } } ?: continue
                    if (openIn(r).isEmpty()) continue
                    openIn(line).filter { it !in r }.toSet().takeIf { it.isNotEmpty() }?.let(::add)
                }
                for (l in lines) {
                    val r = regions.singleOrNull { r -> openIn(l).all { it in r } } ?: continue
                    if (openIn(l).isEmpty()) continue
                    openIn(r).filter { it !in l }.toSet().takeIf { it.isNotEmpty() }?.let(::add)
                }
            }
        }

        /** [k] regions whose open squares fill exactly [k] lines that have other open squares, or [k] lines the other way. */
        fun confined(k: Int): Boolean {
            for (lines in listOf(rows, cols)) {
                for (group in subsets(regions, k)) {
                    val cells = group.flatten().filter { it in open }
                    val hit = lines.filter { l -> cells.any { it in l } }
                    if (hit.size == k && hit.flatten().any { it in open && group.none { g -> it in g } }) return true
                }
                for (group in subsets(lines, k)) {
                    val cells = group.flatten().filter { it in open }
                    val hit = regions.filter { r -> cells.any { it in r } }
                    if (hit.size == k && hit.flatten().any { it in open && group.none { g -> it in g } }) return true
                }
            }
            return false
        }

        val wouldEmpty = open.any { x -> houses.any { h -> x !in h && openIn(h).let { o -> o.isNotEmpty() && o.all { rules(x, it) } } } }

        fun has(rung: Int): Boolean = when (rung) {
            0 -> last
            1 -> confined(1)
            2 -> wouldEmpty
            3 -> (2 until n).any { confined(it) }
            else -> false
        }

        private fun <T> subsets(items: List<T>, k: Int): List<List<T>> =
            if (k == 0) listOf(emptyList()) else items.indices.flatMap { i -> subsets(items.drop(i + 1), k - 1).map { listOf(items[i]) + it } }
    }

    @Test fun `every practice is a real position whose next step is its technique, with no simpler step on it`() {
        assertTrue(KingsTraining.drill is MoveDrill)
        for ((rung, t) in KingsTraining.techniques.withIndex()) {
            val list = KingsTraining.practices(t).take(6).toList()
            assertEquals("practices for $t", 6, list.size)
            assertEquals("distinct boards for $t", 6, list.map { it.board }.distinct().size)
            for (p in list) {
                val b = p.board as KingsState
                val n = b.size
                assertEquals(t, p.step.technique)
                assertEquals("the hint agrees", t, Kings.teach(b)!!.technique)
                val answers = KingsOracle.allSolutions(n, b.region, cap = 2)
                assertEquals("one answer", listOf(b.solution), answers)
                assertTrue("walked kings are the answer's", b.marks.indices.all { b.marks[it] != Mark.KING || it in b.solution })
                assertTrue("walked crosses are not", b.marks.indices.all { b.marks[it] != Mark.BLOCKED || it !in b.solution })
                val r = Rungs(b)
                for (lower in 0 until rung) assertFalse("$t board has a rung-$lower step", r.has(lower))
                if (rung < 4) assertTrue("$t board has its own step by counting", r.has(rung))
                // The step's targets are open, and what it crowns or crosses agrees with the answer.
                val d = p.step as Deduction
                assertTrue(d.targets.isNotEmpty() && d.targets.all { it in r.open })
                val after = d.apply(b) as KingsState
                for (c in d.targets) assertEquals(c in b.solution, after.marks[c] == Mark.KING)
            }
        }
    }

    @Test fun `a move is right when it makes the step, wrong when the teacher calls it a mistake, and otherwise plays on`() {
        for (t in KingsTraining.techniques) {
            for (p in KingsTraining.practices(t).take(3)) {
                val b = p.board as KingsState
                val d = p.step as Deduction
                assertEquals(true, KingsTraining.judge(p, d.apply(b)))
                // A crown on an open square outside the answer is wrong, whatever it is beside.
                val open = Rungs(b).open
                val stray = open.first { it !in b.solution }
                assertEquals(false, KingsTraining.judge(p, b.copy(marks = b.marks.toMutableList().also { it[stray] = Mark.KING })))
                // So is a cross on the answer's square.
                val crown = b.solution.first { b.marks[it] == Mark.EMPTY }
                assertEquals(false, KingsTraining.judge(p, b.copy(marks = b.marks.toMutableList().also { it[crown] = Mark.BLOCKED })))
                // One cross of a step that crosses several is on the way: neither.
                if (d.targets.size > 1 && d.targets.none { it in b.solution }) {
                    val one = b.copy(marks = b.marks.toMutableList().also { it[d.targets.first()] = Mark.BLOCKED })
                    assertNull(KingsTraining.judge(p, one))
                }
            }
        }
    }

    @Test fun `another instance of the same technique is right, and a true cross no instance justifies plays on`() {
        var others = 0
        var unjustified = 0
        for ((rung, t) in KingsTraining.techniques.take(2).withIndex()) {
            for (p in KingsTraining.practices(t).take(12)) {
                val b = p.board as KingsState
                val r = Rungs(b)
                val mine = (p.step as Deduction).targets
                fun with(cells: Set<Int>, mark: Mark) = b.copy(marks = b.marks.toMutableList().also { m -> cells.forEach { m[it] = mark } })
                if (rung == 0) {
                    for (c in r.lastSquares - mine) { others++; assertEquals("crown $c", true, KingsTraining.judge(p, with(setOf(c), Mark.KING))) }
                } else {
                    for (x in r.locked - setOf(mine)) { others++; assertEquals("cross $x", true, KingsTraining.judge(p, with(x, Mark.BLOCKED))) }
                }
                // A cross that is true (no answer crowns it) but completes no instance of the technique: no verdict.
                val instances = if (rung == 0) emptySet() else r.locked
                r.open.firstOrNull { it !in b.solution && setOf(it) !in instances && it !in mine }?.let { c ->
                    unjustified++
                    assertNull("true cross $c", KingsTraining.judge(p, with(setOf(c), Mark.BLOCKED)))
                }
            }
        }
        assertTrue("some practice has another instance ($others)", others > 0)
        assertTrue("some practice has an unjustified true cross ($unjustified)", unjustified > 0)
    }

    @Test fun `the final board is a proved Expert board, the first whose walk needs every technique`() {
        assertEquals(techniqueNamesOf("kings").keys.toList(), KingsTraining.techniques)
        val index = (0 until 60).first { k ->
            KingsTraining.techniquesUsed(Kings.generate(Training.seed(k), Difficulty.EXPERT)) == KingsTraining.techniques.toSet()
        }
        assertEquals(Training.seed(index), KingsTraining.examSeed)
        assertNotNull(Kings.generateVerified(KingsTraining.examSeed!!, Difficulty.EXPERT))
        assertEquals(KingsTraining.techniques.toSet(), KingsTraining.techniquesUsed(KingsTraining.exam()!!))
    }

    @Test fun `kings routes round-trip`() {
        listOf(Route.Training(puzzle = "kings"), Route.Training("what-if", puzzle = "kings"), Route.Training("exam", puzzle = "kings"))
            .forEach { assertEquals(it, decodeRoute(encodeRoute(it))) }
    }
}
