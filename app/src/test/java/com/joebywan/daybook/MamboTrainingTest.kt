package com.joebywan.daybook

import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.MamboTraining
import com.joebywan.daybook.core.MoveDrill
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.puzzles.Mambo
import com.joebywan.daybook.puzzles.MamboState
import com.joebywan.daybook.puzzles.Sym
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
 * Mambo's course (docs/TRAINING_SPEC.md). Each practice is checked against the ladder's rungs re-derived here by plain
 * counting along lines (nothing borrowed from the teacher), and against [MamboOracle]'s count of answers.
 */
class MamboTrainingTest {

    /** Which of the first five rungs has a step on [s], by counting. */
    private class Rungs(s: MamboState) {
        private val n = s.size
        private val c = s.cells
        private val links = s.links
        private val lines = (0 until n).map { r -> List(n) { r * n + it } } + (0 until n).map { k -> List(n) { it * n + k } }

        private fun pair() = lines.any { l ->
            (0 until n - 1).any { k ->
                c[l[k]] != Sym.NONE && c[l[k]] == c[l[k + 1]] &&
                    (l.getOrNull(k - 1)?.let { c[it] == Sym.NONE } == true || l.getOrNull(k + 2)?.let { c[it] == Sym.NONE } == true)
            }
        }

        private fun sandwich() = lines.any { l -> (0 until n - 2).any { k -> c[l[k]] != Sym.NONE && c[l[k]] == c[l[k + 2]] && c[l[k + 1]] == Sym.NONE } }

        private fun link() = links.any { (c[it.a] == Sym.NONE) != (c[it.b] == Sym.NONE) }

        private fun quota() = lines.any { l ->
            l.any { c[it] == Sym.NONE } && listOf(Sym.SUN, Sym.MOON).any { y -> l.count { c[it] == y } == n / 2 }
        }

        /** A line one short of some symbol where its last one on some open square leaves three of the other in a row. */
        private fun almost() = lines.any { l ->
            val open = l.filter { c[it] == Sym.NONE }
            open.size >= 2 && listOf(Sym.SUN, Sym.MOON).any { y ->
                l.count { c[it] == y } == n / 2 - 1 && open.any { e ->
                    val other = if (y == Sym.SUN) Sym.MOON else Sym.SUN
                    val trial = l.map { if (c[it] != Sym.NONE) c[it] else if (it == e) y else other }
                    (0 until n - 2).any { k -> trial[k] == other && trial[k + 1] == other && trial[k + 2] == other }
                }
            }
        }

        fun has(rung: Int) = when (rung) {
            0 -> pair()
            1 -> sandwich()
            2 -> link()
            3 -> quota()
            4 -> almost()
            else -> false
        }
    }

    private fun clue(b: MamboState) = b.cells.indices.map { if (b.givens[it]) b.solution[it] else Sym.NONE }

    @Test fun `every practice is a real position whose next step is its technique, with no simpler step on it`() {
        assertTrue(MamboTraining.drill is MoveDrill)
        for ((rung, t) in MamboTraining.techniques.withIndex()) {
            val list = MamboTraining.practices(t).take(6).toList()
            assertEquals("practices for $t", 6, list.size)
            assertEquals("distinct boards for $t", 6, list.map { it.board }.distinct().size)
            for (p in list) {
                val b = p.board as MamboState
                assertEquals(t, p.step.technique)
                assertEquals("the hint agrees", t, Mambo.teach(b)!!.technique)
                assertEquals("one answer", 1, MamboOracle.countAnswers(b.size, clue(b), b.links))
                assertEquals("and the walked board keeps it", 1, MamboOracle.countAnswers(b.size, b.cells, b.links))
                assertTrue("walked symbols are the answer's", b.cells.indices.all { b.cells[it] == Sym.NONE || b.cells[it] == b.solution[it] })
                val r = Rungs(b)
                for (lower in 0 until rung) assertFalse("$t board has a rung-$lower step", r.has(lower))
                if (rung < 5) assertTrue("$t board has its own step by counting", r.has(rung))
                val d = p.step as Deduction
                assertTrue(d.targets.isNotEmpty() && d.targets.all { b.cells[it] == Sym.NONE })
                val after = d.apply(b) as MamboState
                for (i in d.targets) assertEquals(b.solution[i], after.cells[i])
            }
        }
    }

    @Test fun `a move is right when it makes the step, wrong when no answer keeps it, and otherwise plays on`() {
        assertTrue("a sun passes through a moon, so a wrong verdict waits", MamboTraining.drill.settleMillis > 0)
        for (t in MamboTraining.techniques) {
            for (p in MamboTraining.practices(t).take(3)) {
                val b = p.board as MamboState
                val d = p.step as Deduction
                fun with(i: Int, y: Sym) = b.copy(cells = b.cells.toMutableList().also { it[i] = y })
                assertEquals(true, MamboTraining.judge(p, d.apply(b)))
                val open = b.cells.indices.filter { b.cells[it] == Sym.NONE }
                // The wrong symbol anywhere open is wrong: the board has one answer.
                for (i in listOf(open.first(), d.targets.first())) assertEquals(false, MamboTraining.judge(p, with(i, b.solution[i].other())))
                // The right symbol somewhere else is sound, but not the step: play on.
                val elsewhere = open.first { it !in d.targets }
                assertNull(MamboTraining.judge(p, with(elsewhere, b.solution[elsewhere])))
                // One of a step that places several is on the way.
                if (d.targets.size > 1) d.targets.first().let { assertNull(MamboTraining.judge(p, with(it, b.solution[it]))) }
            }
        }
    }

    @Test fun `the final board is a proved Expert board, the first whose walk needs every technique`() {
        assertEquals(techniqueNamesOf("mambo").keys.toList(), MamboTraining.techniques)
        val index = (0 until 60).first { k ->
            MamboTraining.techniquesUsed(Mambo.generate(Training.seed(k), Difficulty.EXPERT)) == MamboTraining.techniques.toSet()
        }
        assertEquals(Training.seed(index), MamboTraining.examSeed)
        assertNotNull(Mambo.generateVerified(MamboTraining.examSeed!!, Difficulty.EXPERT))
        assertEquals(MamboTraining.techniques.toSet(), MamboTraining.techniquesUsed(MamboTraining.exam()!!))
    }

    @Test fun `mambo routes round-trip`() {
        listOf(Route.Training(puzzle = "mambo"), Route.Training("what-if", puzzle = "mambo"), Route.Training("exam", puzzle = "mambo"))
            .forEach { assertEquals(it, decodeRoute(encodeRoute(it))) }
    }
}
