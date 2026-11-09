package com.joebywan.daybook

import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.LinksTraining
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.core.praisedTechniquesOf
import com.joebywan.daybook.puzzles.Links
import com.joebywan.daybook.puzzles.LinksLogic
import com.joebywan.daybook.puzzles.LinksState
import com.joebywan.daybook.ui.Route
import com.joebywan.daybook.ui.decodeRoute
import com.joebywan.daybook.ui.encodeRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Links' course (docs/TRAINING_SPEC.md). Each practice board is checked by [LinksOracle] (one answer, the stored one), and
 * each step's lines and the walk's lines against that answer; the drill is judged on the real moves.
 */
class LinksTrainingTest {

    private fun answerHas(b: LinksState, a: Int, x: Int) = LinksLogic.hasLine(b.size, LinksLogic.masksOf(b.answer), a, x)

    private fun lines(b: LinksState): List<Pair<Int, Int>> {
        val m = LinksLogic.masksOf(b.links)
        return (0 until b.size * b.size).flatMap { q ->
            listOf(LinksLogic.RIGHT, LinksLogic.DOWN).filter { m[q] and (1 shl it) != 0 }.map { q to LinksLogic.next(b.size, q, it) }
        }
    }

    @Test fun `every practice is a real position whose next step is its technique, and agrees with the one answer`() {
        assertEquals(praisedTechniquesOf("links").toList(), LinksTraining.techniques)
        for (t in LinksTraining.techniques) {
            val list = LinksTraining.practices(t).take(6).toList()
            assertEquals("practices for $t", 6, list.size)
            assertEquals("distinct boards for $t", 6, list.map { it.board }.distinct().size)
            for (p in list) {
                val b = p.board as LinksState
                assertEquals(t, p.step.technique)
                assertEquals("the hint agrees", t, Links.teach(b)!!.technique)
                assertEquals("one answer", 1L, LinksOracle.count(b.size, b.ends))
                assertTrue("the stored answer obeys", LinksOracle.obeys(b.size, b.ends, LinksLogic.masksOf(b.answer)))
                assertTrue("walked lines are the answer's", lines(b).all { (a, x) -> answerHas(b, a, x) })
                val after = (p.step as Deduction).apply(b) as LinksState
                assertTrue("the step's lines are the answer's", lines(after).all { (a, x) -> answerHas(b, a, x) })
            }
        }
    }

    @Test fun `a move is right when it makes the step, wrong on a line no answer keeps`() {
        for (t in LinksTraining.techniques) {
            for (p in LinksTraining.practices(t).take(3)) {
                val b = p.board as LinksState
                val d = p.step as Deduction
                assertEquals(true, LinksTraining.judge(p, d.apply(b)))
                // A line between two neighbours the answer does not join, from an empty square.
                val n = b.size
                val wrong = (0 until n * n).firstNotNullOfOrNull { q ->
                    val x = LinksLogic.next(n, q, LinksLogic.RIGHT)
                    if (x >= 0 && b.ends[q] == '.' && b.ends[x] == '.' && !answerHas(b, q, x) && b.links[q] == '0' && b.links[x] == '0') q to x else null
                } ?: continue
                assertEquals(false, LinksTraining.judge(p, b.drag(listOf(wrong.first, wrong.second))))
            }
        }
    }

    @Test fun `the final board is a proved Expert board, the first whose walk needs every technique`() {
        val index = (0 until 200).first { k ->
            LinksTraining.techniquesUsed(LinksLogic.generateVerified(Training.seed(k), Difficulty.EXPERT)!!) == LinksTraining.techniques.toSet()
        }
        assertEquals(Training.seed(index), LinksTraining.examSeed)
        assertNotNull(LinksLogic.generateVerified(LinksTraining.examSeed!!, Difficulty.EXPERT))
        assertEquals(LinksTraining.techniques.toSet(), LinksTraining.techniquesUsed(LinksTraining.exam()!!))
    }

    @Test fun `links routes round-trip`() {
        listOf(Route.Training(puzzle = "links"), Route.Training("cut-off", puzzle = "links"), Route.Training("exam", puzzle = "links"))
            .forEach { assertEquals(it, decodeRoute(encodeRoute(it))) }
    }
}
