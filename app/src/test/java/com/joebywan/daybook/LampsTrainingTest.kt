package com.joebywan.daybook

import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.LampsTraining
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.puzzles.Lamps
import com.joebywan.daybook.puzzles.LampsLogic
import com.joebywan.daybook.puzzles.LampsState
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
 * Lamps' course (docs/TRAINING_SPEC.md). Each practice is checked against the rungs of the ladder re-derived here by
 * walking rays and counting (nothing borrowed from `LampsLogic`), and against [LampsOracle]'s count of answers.
 */
class LampsTrainingTest {

    /** The rungs below the what-if on one board. Crosses are the walk's, so they are known: no lamp there. */
    private class Rungs(private val s: LampsState) {
        val n = s.size
        private fun white(i: Int) = s.walls[i] == '.'
        private fun number(i: Int) = s.walls[i].let { if (it in '0'..'4') it - '0' else -1 }
        private fun beside(i: Int) = listOf(-n, n, -1, 1).map { i + it }
            .filter { j -> j in 0 until n * n && (j / n == i / n || j % n == i % n) && white(j) }

        /** The squares a lamp on [i] lights, itself included: out along each ray to a wall or the edge. */
        fun rays(i: Int): Set<Int> {
            val out = mutableSetOf(i)
            for ((dr, dc) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                var r = i / n + dr; var c = i % n + dc
                while (r in 0 until n && c in 0 until n && white(r * n + c)) { out += r * n + c; r += dr; c += dc }
            }
            return out
        }

        private fun litBy(lamps: Set<Int>) = lamps.flatMap { rays(it) }.toSet()
        private val lamps = s.marks.indices.filter { s.marks[it] == LampsLogic.LAMP_MARK }.toSet()
        private fun cand(i: Int, lamps: Set<Int>, lit: Set<Int>) = white(i) && i !in lamps && s.marks[i] != LampsLogic.CROSS && i !in lit
        val lit = litBy(lamps)
        val open = s.marks.indices.filter { cand(it, lamps, lit) }.toSet()
        private val walls = s.walls.indices.filter { number(it) >= 0 }

        val zero = walls.any { w -> number(w) == 0 && beside(w).any { it in open } }
        val done = walls.any { w -> number(w) > 0 && beside(w).count { it in lamps } == number(w) && beside(w).any { it in open } }
        val room = walls.any { w -> (number(w) - beside(w).count { it in lamps }).let { need -> need > 0 && beside(w).count { it in open } == need } }
        val onlyLight = s.walls.indices.any { y -> white(y) && y !in lit && rays(y).count { it in open } == 1 }

        /** A lamp on an open square that would leave some number too few lamps and places for them. */
        val starve = open.any { x ->
            val l = lamps + x; val after = litBy(l)
            walls.any { w -> beside(w).count { it in l } + beside(w).count { cand(it, l, after) } < number(w) }
        }

        /** A lamp on an open square that would leave some unlit square with nothing that could light it. */
        val dark = open.any { x ->
            val l = lamps + x; val after = litBy(l)
            s.walls.indices.any { y -> white(y) && y !in after && rays(y).none { cand(it, l, after) } }
        }

        fun has(rung: Int) = listOf(zero, done, room, onlyLight, starve, dark).getOrElse(rung) { false }
    }

    @Test fun `every practice is a real position whose next step is its technique, with no simpler step on it`() {
        assertEquals(techniqueNamesOf("lamps").keys.toList(), LampsTraining.techniques)
        for ((rung, t) in LampsTraining.techniques.withIndex()) {
            val list = LampsTraining.practices(t).take(6).toList()
            assertEquals("practices for $t", 6, list.size)
            assertEquals("distinct boards for $t", 6, list.map { it.board }.distinct().size)
            for (p in list) {
                val b = p.board as LampsState
                assertEquals(t, p.step.technique)
                assertEquals("the hint agrees", t, Lamps.teach(b)!!.technique)
                assertEquals("one answer", listOf(b.solution), LampsOracle.solutions(b.size, b.walls, limit = 2))
                assertTrue("walked lamps are the answer's", b.marks.indices.all { b.marks[it] != LampsLogic.LAMP_MARK || b.solution[it] == '1' })
                assertTrue("walked crosses are not", b.marks.indices.all { b.marks[it] != LampsLogic.CROSS || b.solution[it] != '1' })
                val r = Rungs(b)
                for (lower in 0 until rung) assertFalse("$t board has a rung-$lower step", r.has(lower))
                if (rung < 6) assertTrue("$t board has its own step by counting", r.has(rung))
                // The step's targets are open, and what it lights or crosses agrees with the answer.
                val d = p.step as Deduction
                assertTrue(d.targets.isNotEmpty() && d.targets.all { it in r.open })
                val after = d.apply(b) as LampsState
                for (c in d.targets) assertEquals(b.solution[c] == '1', after.marks[c] == LampsLogic.LAMP_MARK)
            }
        }
    }

    @Test fun `a move is right when it makes the step, wrong on a lamp or a cross no answer keeps, and otherwise plays on`() {
        for (t in LampsTraining.techniques) {
            for (p in LampsTraining.practices(t).take(3)) {
                val b = p.board as LampsState
                val d = p.step as Deduction
                assertEquals(true, LampsTraining.judge(p, d.apply(b)))
                val open = Rungs(b).open
                // A lamp outside the answer is the teacher's mistake.
                open.firstOrNull { b.solution[it] != '1' }?.let { assertEquals(false, LampsTraining.judge(p, b.withMark(it, LampsLogic.LAMP_MARK))) }
                // A cross on the answer's lamp is wrong here, though the game's hints read crosses as notes.
                open.firstOrNull { b.solution[it] == '1' }?.let { lamp ->
                    val crossed = b.withMark(lamp, LampsLogic.CROSS)
                    assertNull("the game's hint does not call it a mistake", Lamps.teach(crossed)?.takeIf { it.mistake })
                    assertEquals(false, LampsTraining.judge(p, crossed))
                }
                // One square of several is on the way: neither.
                if (d.targets.size > 1) {
                    val c = d.targets.first()
                    val one = b.withMark(c, if (b.solution[c] == '1') LampsLogic.LAMP_MARK else LampsLogic.CROSS)
                    if (!d.isReached(one)) assertNull(LampsTraining.judge(p, one))
                }
            }
        }
    }

    @Test fun `the final board is a proved Expert board, the first whose walk needs every technique`() {
        val index = (0 until 60).first { k ->
            LampsTraining.techniquesUsed(Lamps.generate(Training.seed(k), Difficulty.EXPERT)) == LampsTraining.techniques.toSet()
        }
        assertEquals(Training.seed(index), LampsTraining.examSeed)
        assertNotNull(LampsLogic.generateVerified(LampsTraining.examSeed!!, Difficulty.EXPERT))
        assertEquals(LampsTraining.techniques.toSet(), LampsTraining.techniquesUsed(LampsTraining.exam()!!))
    }

    @Test fun `lamps routes round-trip`() {
        listOf(Route.Training(puzzle = "lamps"), Route.Training("dark", puzzle = "lamps"), Route.Training("exam", puzzle = "lamps"))
            .forEach { assertEquals(it, decodeRoute(encodeRoute(it))) }
    }
}
