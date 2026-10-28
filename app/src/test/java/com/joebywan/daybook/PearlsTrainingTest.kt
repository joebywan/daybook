package com.joebywan.daybook

import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PearlsTraining
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.puzzles.Pearls
import com.joebywan.daybook.puzzles.PearlsGrid
import com.joebywan.daybook.puzzles.PearlsLogic
import com.joebywan.daybook.puzzles.PearlsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pearls' course (docs/TRAINING_SPEC.md). Each practice is checked against [PearlsOracle]'s independent count of loops,
 * and against the rungs below the what-if re-derived here from the lines and crosses by counting a square's ways and
 * reading the pearls' rules off their neighbours (nothing borrowed from the teacher but the grid's geometry).
 */
class PearlsTrainingTest {

    /** The rungs below the what-if on one board. Directions 0 up, 1 right, 2 down, 3 left; -1 is off the board. */
    private class Rungs(val s: PearlsState) {
        val n = s.size
        val g = PearlsGrid.of(n)
        private fun lineOn(e: Int) = e >= 0 && s.edges[e] == PearlsLogic.LINE_MARK
        private fun linesAt(q: Int) = (0 until 4).count { lineOn(g.edge(q, it)) }

        /** Off the board, crossed, or beside a square that already has its two lines. */
        fun shut(e: Int) = e < 0 || s.edges[e] == PearlsLogic.CROSS_MARK ||
            !lineOn(e) && (linesAt(g.endA[e]) >= 2 || linesAt(g.endB[e]) >= 2)
        private fun free(e: Int) = !shut(e) && !lineOn(e)
        private fun ways(q: Int) = (0 until 4).count { !shut(g.edge(q, it)) }
        private fun on(q: Int, d: Int) = if (q < 0) -1 else g.edge(q, d)
        private fun next(q: Int, d: Int) = if (q < 0) -1 else g.next(q, d)

        private fun blackArm(p: Int, d: Int): Boolean {
            val q = next(p, d)
            return !shut(g.edge(p, d)) && s.pearls[q] != 'b' && !shut(g.edge(q, d)) &&
                !lineOn(g.edge(q, (d + 1) % 4)) && !lineOn(g.edge(q, (d + 3) % 4))
        }

        /** A black pearl with an arm settled (a line out, or the other way shut) whose two lines or back cross are not all in. */
        fun black() = s.pearls.indices.any { p ->
            s.pearls[p] == 'b' && (0 until 2).any { axis ->
                listOf(axis, axis + 2).any { d ->
                    val o = (d + 2) % 4
                    (lineOn(g.edge(p, d)) || !blackArm(p, o)) &&
                        (free(g.edge(p, d)) || free(on(next(p, d), d)) || free(g.edge(p, o)))
                }
            }
        }

        private fun whiteAxis(p: Int, axis: Int): Boolean {
            val (a, b) = axis to axis + 2
            return !shut(g.edge(p, a)) && !shut(g.edge(p, b)) && !lineOn(g.edge(p, axis + 1)) && !lineOn(g.edge(p, (axis + 3) % 4)) &&
                !(lineOn(on(next(p, a), a)) && lineOn(on(next(p, b), b)))
        }

        /** The axis a white pearl must run along, if settled. */
        private fun whiteRuns(p: Int): Int? = (0 until 2).firstOrNull { axis ->
            lineOn(g.edge(p, axis)) || lineOn(g.edge(p, axis + 2)) || !whiteAxis(p, 1 - axis)
        }

        fun white() = s.pearls.indices.any { p ->
            s.pearls[p] == 'w' && whiteRuns(p)?.let { axis ->
                // A way along it not yet drawn, or a way across not yet shut.
                listOf(axis, axis + 1, axis + 2, (axis + 3) % 4).any { free(g.edge(p, it)) }
            } == true
        }

        /** A white pearl run straight through, the loop straight on beyond one side and not yet turned off on the other. */
        fun whiteTurn() = s.pearls.indices.any { p ->
            s.pearls[p] == 'w' && whiteRuns(p)?.let { axis ->
                listOf(axis, axis + 2).any { d -> val o = (d + 2) % 4; lineOn(on(next(p, d), d)) && free(on(next(p, o), o)) }
            } == true
        }

        fun oneWay() = (0 until n * n).any { q -> linesAt(q) == 1 && ways(q) == 2 }
        fun pearlWays() = (0 until n * n).any { q -> s.pearls[q] != '.' && linesAt(q) == 0 && ways(q) == 2 }
        fun deadEnd() = (0 until n * n).any { q -> s.pearls[q] == '.' && linesAt(q) == 0 && ways(q) == 1 }

        /** Two ends of one stretch of line side by side, which would close a loop leaving out a line or a pearl. */
        fun smallLoop(): Boolean {
            val lines = (0 until g.edgeCount).filter { lineOn(it) }
            fun reach(a: Int): Set<Int> {
                val seen = mutableSetOf(a)
                var grew = true
                while (grew) {
                    grew = false
                    for (e in lines) if ((g.endA[e] in seen) != (g.endB[e] in seen)) { seen += g.endA[e]; seen += g.endB[e]; grew = true }
                }
                return seen
            }
            return (0 until g.edgeCount).any { e ->
                val (a, b) = g.endA[e] to g.endB[e]
                !shut(e) && !lineOn(e) && linesAt(a) == 1 && linesAt(b) == 1 && b in reach(a) && reach(a).let { str ->
                    lines.any { g.endA[it] !in str } || s.pearls.indices.any { s.pearls[it] != '.' && it !in str }
                }
            }
        }

        fun has(rung: Int) = when (rung) {
            0 -> black()
            1 -> white()
            2 -> whiteTurn()
            3 -> oneWay()
            4 -> pearlWays() || deadEnd()
            5 -> smallLoop()
            else -> false
        }
    }

    /** The answer's edges, from the oracle's loop of squares. */
    private fun answerEdges(s: PearlsState): Set<Int> {
        val all = PearlsOracle.answers(s.size, s.pearls)
        assertEquals("one answer", 1, all.size)
        val loop = all[0]
        val g = PearlsGrid.of(s.size)
        return loop.indices.map { g.between(loop[it], loop[(it + 1) % loop.size]) }.toSet()
    }

    // Ladder rung of each technique; "a pearl with two ways in" (dropped from the course) sits with the dead end.
    private val rungOf = mapOf("black-pearl" to 0, "white-pearl" to 1, "white-turn" to 2, "one-way" to 3, "dead-end" to 4, "small-loop" to 5, "what-if" to 6)

    @Test fun `every practice is a real position whose next step is its technique, with no simpler step on it`() {
        assertEquals(rungOf.keys.toList(), PearlsTraining.techniques)
        for (t in PearlsTraining.techniques) {
            val list = PearlsTraining.practices(t).take(6).toList()
            assertEquals("practices for $t", 6, list.size)
            assertEquals("distinct boards for $t", 6, list.map { it.board }.distinct().size)
            for (p in list) {
                val b = p.board as PearlsState
                assertEquals(t, p.step.technique)
                assertEquals("the hint agrees", t, Pearls.teach(b)!!.technique)
                val answer = answerEdges(b)
                for (e in b.edges.indices) {
                    if (b.edges[e] == PearlsLogic.LINE_MARK) assertTrue("walked line $e is the loop's", e in answer)
                    if (b.edges[e] == PearlsLogic.CROSS_MARK) assertFalse("walked cross $e is not", e in answer)
                }
                val r = Rungs(b)
                val rung = rungOf.getValue(t)
                for (lower in 0 until rung) assertFalse("$t board has a rung-$lower step", r.has(lower))
                if (rung < 6) assertTrue("$t board has its own step by counting", r.has(rung))
                // The step agrees with the loop.
                val after = (p.step as Deduction).apply(b) as PearlsState
                assertNotEquals(b.edges, after.edges)
                for (e in b.edges.indices) if (after.edges[e] != b.edges[e]) {
                    assertEquals(e in answer, after.edges[e] == PearlsLogic.LINE_MARK)
                }
            }
        }
    }

    @Test fun `a move is right when it makes the step, wrong when the loop disagrees, and otherwise plays on`() {
        var partial = 0
        for (t in PearlsTraining.techniques) {
            for (p in PearlsTraining.practices(t).take(3)) {
                val b = p.board as PearlsState
                val d = p.step as Deduction
                val after = d.apply(b) as PearlsState
                assertEquals(true, PearlsTraining.judge(p, after))
                val answer = answerEdges(b)
                val open = b.edges.indices.filter { b.edges[it] == PearlsLogic.OPEN_MARK }
                // A cross on the loop is wrong; so is a line off it.
                open.firstOrNull { it in answer }?.let { assertEquals(false, PearlsTraining.judge(p, b.tap(it))) }
                open.firstOrNull { it !in answer }?.let { e ->
                    assertEquals(false, PearlsTraining.judge(p, b.paint(listOf(e), PearlsLogic.LINE_MARK)))
                }
                // One mark of a step that makes several: not wrong, and play on while the rest are missing.
                val changed = b.edges.indices.filter { after.edges[it] != b.edges[it] }
                if (changed.size > 1) {
                    val one = b.paint(listOf(changed[0]), after.edges[changed[0]])
                    val verdict = PearlsTraining.judge(p, one)
                    assertNotEquals(false, verdict)
                    if (verdict == null) partial++
                }
            }
        }
        assertTrue(partial > 0)
    }

    @Test fun `the final board is a proved Expert board, the first whose walk needs every technique`() {
        val index = (0 until 60).first { k ->
            PearlsTraining.techniquesUsed(Pearls.generate(Training.seed(k), Difficulty.EXPERT)) == PearlsTraining.techniques.toSet()
        }
        assertEquals(Training.seed(index), PearlsTraining.examSeed)
        assertNotNull(PearlsLogic.generateVerified(PearlsTraining.examSeed!!, Difficulty.EXPERT))
        assertEquals(PearlsTraining.techniques.toSet(), PearlsTraining.techniquesUsed(PearlsTraining.exam()!!))
    }
}
