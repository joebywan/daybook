package com.joebywan.daybook

import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.MinesTraining
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.puzzles.Mines
import com.joebywan.daybook.puzzles.MinesLogic
import com.joebywan.daybook.puzzles.MinesState
import com.joebywan.daybook.puzzles.MinesTeacher
import com.joebywan.daybook.ui.Route
import com.joebywan.daybook.ui.decodeRoute
import com.joebywan.daybook.ui.encodeRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mines' course (docs/TRAINING_SPEC.md). Each practice is checked against the ladder's levels re-derived here by plain
 * counting (nothing borrowed from the teacher: two numbers are brute-forced over how many mines their shared squares
 * hold), and against [MinesOracle]'s enumeration of every layout the visible numbers allow.
 */
class MinesTrainingTest {

    /** What a position offers, by counting: flags are the walk's, so they are known mines; open squares known safe. */
    private class Rungs(private val s: MinesState) {
        val w = s.width
        val n = s.width * s.height
        private fun around(i: Int) = (0 until n).filter { j -> j != i && kotlin.math.abs(j / w - i / w) <= 1 && kotlin.math.abs(j % w - i % w) <= 1 }
        val unknown = (0 until n).filter { !s.isOpen(it) && !s.isFlag(it) && it !in s.bumps }.toSet()
        private val numbers = (0 until n).filter { s.isOpen(it) }
        private fun unk(i: Int) = around(i).filter { it in unknown }
        private fun need(i: Int) = s.number(i) - around(i).count { s.isFlag(it) || it in s.bumps }

        /** A number whose unknown squares are all safe or all mines. */
        val single = numbers.any { i -> unk(i).isNotEmpty() && (need(i) == 0 || need(i) == unk(i).size) }

        /** Two numbers that decide a square neither decides alone: every count of mines on their shared squares tried. */
        val two = numbers.any { a ->
            numbers.any { b ->
                val ua = unk(a).toSet(); val ub = unk(b).toSet()
                val shared = ua intersect ub
                if (a == b || shared.isEmpty()) return@any false
                val onlyA = ua - shared; val onlyB = ub - shared
                val xs = (0..shared.size).filter { x -> need(a) - x in 0..onlyA.size && need(b) - x in 0..onlyB.size }
                fun decided(size: Int, needs: List<Int>) = size > 0 && (needs.all { it == 0 } || needs.all { it == size })
                decided(onlyA.size, xs.map { need(a) - it }) || decided(onlyB.size, xs.map { need(b) - it }) ||
                    decided(shared.size, xs)
            }
        }

        val left = s.total - (0 until n).count { s.isFlag(it) || it in s.bumps }
        val count = unknown.isNotEmpty() && (left == 0 || left == unknown.size)

        /** Some numbers whose unknown squares none share need every mine left, and some unknown square is outside them. */
        val edge: Boolean = run {
            val cand = numbers.filter { unk(it).isNotEmpty() && need(it) > 0 }
            fun go(k: Int, used: Set<Int>, sum: Int): Boolean {
                if (sum == left) return unknown.any { it !in used }
                if (sum > left || k == cand.size) return false
                val u = unk(cand[k])
                return (u.none { it in used } && go(k + 1, used + u, sum + need(cand[k]))) || go(k + 1, used, sum)
            }
            left > 0 && go(0, emptySet(), 0)
        }
    }

    /** The level of each technique below the what-if, as the ladder groups them: lower levels must be empty. */
    private fun lowerThan(t: String, r: Rungs): List<Pair<String, Boolean>> = when (t) {
        MinesTeacher.ALL_FOUND, MinesTeacher.ALL_MINES -> emptyList()
        MinesTeacher.SUBSET, MinesTeacher.PAIR -> listOf("single" to r.single)
        MinesTeacher.EDGE -> listOf("single" to r.single, "two" to r.two, "count" to r.count)
        else -> listOf("single" to r.single, "two" to r.two, "count" to r.count, "edge" to r.edge)
    }

    private fun own(t: String, r: Rungs): Boolean? = when (t) {
        MinesTeacher.ALL_FOUND, MinesTeacher.ALL_MINES -> r.single
        MinesTeacher.SUBSET, MinesTeacher.PAIR -> r.two
        MinesTeacher.EDGE -> r.edge
        else -> null
    }

    /**
     * Whether brute force alone clears [start]: reveal every square no layout of the visible numbers has a mine on, read
     * the numbers that turns up, and again, until every safe square is open. Mines' "one answer": nothing is ever a guess.
     */
    private fun clearsByOracle(start: MinesState): Boolean {
        var b = start
        val hit = BooleanArray(b.width * b.height) { it in b.bumps }
        while (!b.solved) {
            val o = MinesOracle.solve(b.width, b.height, visible(b), hit, b.total)
            val safe = b.mines.indices.filter { !b.isOpen(it) && !o.canMine[it] }
            if (safe.isEmpty()) return false
            // Flags are notes: clear them so a reveal is never refused (none is on a safe square, checked above).
            b = safe.fold(b) { acc, i -> (if (acc.isFlag(i)) acc.flag(i)!! else acc).let { it.reveal(i) ?: it } }
        }
        return true
    }

    private fun visible(b: MinesState) = IntArray(b.width * b.height) { if (b.isOpen(it)) b.number(it) else -1 }

    @Test fun `every practice is a real position whose next step is its technique, with no simpler step on it`() {
        assertEquals(listOf("all-found", "all-mines", "subset", "pair", "count-edge", "what-if"), MinesTraining.techniques)
        for (t in MinesTraining.techniques) {
            val list = MinesTraining.practices(t).take(6).toList()
            assertEquals("practices for $t", 6, list.size)
            assertEquals("distinct boards for $t", 6, list.map { it.board }.distinct().size)
            for (p in list) {
                val b = p.board as MinesState
                assertEquals(t, p.step.technique)
                assertEquals("the hint agrees", t, Mines.teach(b)!!.technique)
                val o = MinesOracle.solve(b.width, b.height, visible(b), BooleanArray(b.width * b.height) { it in b.bumps }, b.total)
                assertTrue("the stored layout is one the numbers allow", b.mines.indices.all { b.isOpen(it) || if (b.isMine(it)) o.canMine[it] else o.canSafe[it] })
                assertTrue("walked flags are mines in every layout", b.mines.indices.all { !b.isFlag(it) || !o.canSafe[it] })
                assertTrue("one answer: the oracle alone clears it", clearsByOracle(b))
                assertFalse(b.failed)
                val r = Rungs(b)
                for ((name, has) in lowerThan(t, r)) assertFalse("$t board has a $name step", has)
                own(t, r)?.let { assertTrue("$t board has its own step by counting", it) }
                // What the step reveals is safe and what it flags is a mine, in every layout.
                val d = p.step as Deduction
                assertTrue(d.targets.isNotEmpty() && d.targets.all { it in r.unknown })
                val after = d.apply(b) as MinesState
                assertTrue(d.targets.all { if (after.isOpen(it)) !o.canMine[it] else after.isFlag(it) && !o.canSafe[it] })
            }
        }
    }

    @Test fun `a move is right when it makes the step, wrong on a mine or a flag on a safe square, and otherwise plays on`() {
        for (t in MinesTraining.techniques) {
            for (p in MinesTraining.practices(t).take(3)) {
                val b = p.board as MinesState
                val d = p.step as Deduction
                val made = d.apply(b) as MinesState
                assertEquals(true, MinesTraining.judge(p, made))
                val safe = d.targets.filter { made.isOpen(it) }
                val mines = d.targets.filter { made.isFlag(it) }
                val hidden = b.mines.indices.filter { !b.isOpen(it) && !b.isFlag(it) }
                // A mine revealed loses a game; here it is just wrong.
                hidden.firstOrNull { b.isMine(it) }?.let { m ->
                    val lost = b.reveal(m)!!
                    assertTrue(lost.failed)
                    assertEquals(false, MinesTraining.judge(p, lost))
                }
                // A flag on a safe square is the teacher's mistake.
                assertEquals(false, MinesTraining.judge(p, b.flag(hidden.first { !b.isMine(it) })!!))
                // One square of several is on the way: never wrong, and no pass unless it completes another instance on
                // its own (a reveal can: another number's last safe square), which the instance test covers.
                if (safe.size > 1) {
                    val one = b.reveal(safe.first())!!
                    if (!safe.all { one.isOpen(it) }) assertNotEquals(false, MinesTraining.judge(p, one))
                } else if (safe.isEmpty() && mines.size > 1) {
                    assertNotEquals(false, MinesTraining.judge(p, b.flag(mines.first())!!))
                }
            }
        }
    }

    @Test fun `another instance of the same technique is right, and a true reveal no instance justifies plays on`() {
        var others = 0
        var unjustified = 0
        for (t in listOf(MinesTeacher.ALL_FOUND, MinesTeacher.ALL_MINES)) {
            for (p in MinesTraining.practices(t).take(12)) {
                val b = p.board as MinesState
                val w = b.width
                val n = b.width * b.height
                fun around(i: Int) = (0 until n).filter { j -> j != i && kotlin.math.abs(j / w - i / w) <= 1 && kotlin.math.abs(j % w - i % w) <= 1 }
                fun unk(i: Int) = around(i).filter { !b.isOpen(it) && !b.isFlag(it) }
                fun need(i: Int) = b.number(i) - around(i).count { b.isFlag(it) }
                val numbers = (0 until n).filter { b.isOpen(it) && unk(it).isNotEmpty() }
                // Every instance by counting: a number's hidden squares, all safe or all mines.
                val safeSets = numbers.filter { need(it) == 0 }.map { unk(it).toSet() }.toSet()
                val mineSets = numbers.filter { need(it) == unk(it).size }.map { unk(it).toSet() }.toSet()
                val mine = (p.step as Deduction).targets
                if (t == MinesTeacher.ALL_FOUND) for (x in safeSets - setOf(mine)) {
                    others++
                    assertEquals("reveal $x", true, MinesTraining.judge(p, x.fold(b) { acc, i -> acc.reveal(i) ?: acc }))
                } else for (x in mineSets - setOf(mine)) {
                    others++
                    assertEquals("flag $x", true, MinesTraining.judge(p, x.fold(b) { acc, i -> acc.flag(i) ?: acc }))
                }
                // A safe square that is no instance's, revealed without flooding: true, but no verdict.
                val justified = (if (t == MinesTeacher.ALL_FOUND) safeSets else mineSets).flatten().toSet() + mine
                (0 until n).firstOrNull { !b.isOpen(it) && !b.isFlag(it) && !b.isMine(it) && b.number(it) > 0 && it !in justified }?.let { c ->
                    unjustified++
                    assertNull("true reveal $c", MinesTraining.judge(p, b.reveal(c)!!))
                }
            }
        }
        assertTrue("some practice has another instance ($others)", others > 0)
        assertTrue("some practice has an unjustified true reveal ($unjustified)", unjustified > 0)
    }

    @Test fun `the final board is a proved Expert board, the first whose walk needs every technique`() {
        assertEquals(techniqueNamesOf("mines").keys.filter { it != MinesTeacher.COUNT }, MinesTraining.techniques)
        val index = (0 until 60).first { k ->
            MinesTraining.techniquesUsed(Mines.generate(Training.seed(k), Difficulty.EXPERT)) == MinesTraining.techniques.toSet()
        }
        assertEquals(Training.seed(index), MinesTraining.examSeed)
        assertNotNull(MinesLogic.generateVerified(MinesTraining.examSeed!!, Difficulty.EXPERT))
        assertEquals(MinesTraining.techniques.toSet(), MinesTraining.techniquesUsed(MinesTraining.exam()!!))
    }

    @Test fun `mines routes round-trip`() {
        listOf(Route.Training(puzzle = "mines"), Route.Training("what-if", puzzle = "mines"), Route.Training("exam", puzzle = "mines"))
            .forEach { assertEquals(it, decodeRoute(encodeRoute(it))) }
    }
}
