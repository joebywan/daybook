package com.joebywan.daybook

import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.MoveDrill
import com.joebywan.daybook.core.ShikakuTraining
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.puzzles.Block
import com.joebywan.daybook.puzzles.Shikaku
import com.joebywan.daybook.puzzles.ShikakuState
import com.joebywan.daybook.puzzles.ShikakuTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shikaku's course (docs/TRAINING_SPEC.md). Each practice is checked against the rungs of the ladder re-derived here by
 * listing rectangles and counting (nothing borrowed from the teacher), and against a plain tiling search for its answers.
 */
class ShikakuTrainingTest {

    /** Every tiling by clue rectangles, up to [cap]: first hole in reading order, every rectangle rooted there. */
    private fun tilings(s: ShikakuState, cap: Int = 2): List<Set<Block>> {
        val w = s.width
        val h = s.height
        val used = BooleanArray(w * h)
        val chosen = ArrayList<Block>()
        val out = ArrayList<Set<Block>>()
        fun go() {
            if (out.size >= cap) return
            val first = used.indexOfFirst { !it }
            if (first < 0) { out += chosen.toSet(); return }
            for (r1 in first / w until h) for (c1 in first % w until w) {
                val b = Block(first / w, first % w, r1, c1)
                val cells = b.cells(w)
                val clues = cells.mapNotNull { s.clues[it] }
                if (cells.any { used[it] } || clues.size != 1 || clues[0] != b.area) continue
                cells.forEach { used[it] = true }
                chosen += b
                go()
                chosen.removeAt(chosen.size - 1)
                cells.forEach { used[it] = false }
            }
        }
        go()
        return out
    }

    /** The rungs below the what-if on one board, by listing every rectangle each open number could still take. */
    private class Rungs(s: ShikakuState) {
        val w = s.width
        private val taken = BooleanArray(w * s.height).also { t -> s.blocks.forEach { b -> b.cells(w).forEach { t[it] = true } } }
        val open = s.clues.indices.filter { s.clues[it] != null && !taken[it] }
        val options: Map<Int, List<Set<Int>>> = open.associateWith { clue ->
            val n = s.clues[clue]!!
            buildList {
                for (r0 in 0 until s.height) for (c0 in 0 until w) for (r1 in r0 until s.height) for (c1 in c0 until w) {
                    val b = Block(r0, c0, r1, c1)
                    val cells = b.cells(w)
                    if (b.area == n && clue in cells && cells.count { s.clues[it] != null } == 1 && cells.none { taken[it] }) add(cells.toSet())
                }
            }
        }
        /** Each rung's instances, as the squares of the rectangle each one settles. */
        val fitting: Set<Set<Int>> = open.mapNotNull { options.getValue(it).singleOrNull() }.toSet()
        val reaching: Set<Set<Int>> = (0 until w * s.height).mapNotNull { cell ->
            if (taken[cell] || s.clues[cell] != null) return@mapNotNull null
            val by = open.filter { c -> options.getValue(c).any { cell in it } }
            if (by.size == 1) options.getValue(by[0]).filter { cell in it }.singleOrNull() else null
        }.toSet()
        val sharing: Set<Set<Int>> = open.flatMap { a ->
            val opts = options.getValue(a)
            val shared = if (opts.size < 2) emptySet() else opts.reduce { x, y -> x intersect y } - a
            if (shared.isEmpty()) emptyList() else open.mapNotNull { b ->
                if (b == a || options.getValue(b).size < 2) null else options.getValue(b).filter { o -> o.none { it in shared } }.singleOrNull()
            }
        }.toSet()

        fun instances(rung: Int): Set<Set<Int>>? = when (rung) {
            0 -> fitting
            1 -> reaching
            2 -> sharing
            else -> null
        }

        fun has(rung: Int) = instances(rung)?.isNotEmpty() ?: false
    }

    private fun cellsOf(s: ShikakuState, b: Block) = b.cells(s.width).toSet()

    @Test fun `every practice is a real position whose next step is its technique, with no simpler step on it`() {
        assertTrue(ShikakuTraining.drill is MoveDrill)
        for ((rung, t) in ShikakuTraining.techniques.withIndex()) {
            val list = ShikakuTraining.practices(t).take(6).toList()
            assertEquals("practices for $t", 6, list.size)
            assertEquals("distinct boards for $t", 6, list.map { it.board }.distinct().size)
            for (p in list) {
                val b = p.board as ShikakuState
                assertEquals(t, p.step.technique)
                assertEquals("the hint agrees", t, Shikaku.teach(b)!!.technique)
                assertEquals("one answer", listOf(b.solution.toSet()), tilings(b))
                assertTrue("walked rectangles are the answer's", b.blocks.all { it in b.solution })
                val r = Rungs(b)
                for (lower in 0 until rung) assertFalse("$t board has a rung-$lower step", r.has(lower))
                if (rung < 3) assertTrue("$t board has its own step by counting", r.has(rung))
                // The step draws one rectangle of the answer, which some open number could still take.
                val d = p.step as Deduction
                val drawn = (d.apply(b) as ShikakuState).blocks - b.blocks.toSet()
                assertEquals(1, drawn.size)
                assertTrue(drawn[0] in b.solution)
                assertEquals(cellsOf(b, drawn[0]), d.targets)
                assertTrue(r.options.values.any { cellsOf(b, drawn[0]) in it })
            }
        }
    }

    @Test fun `a move is right when it makes the step, wrong when the teacher calls it a mistake, and otherwise plays on`() {
        for (t in ShikakuTraining.techniques) {
            for (p in ShikakuTraining.practices(t).take(3)) {
                val b = p.board as ShikakuState
                val d = p.step as Deduction
                assertEquals(true, ShikakuTraining.judge(p, d.apply(b)))
                // A rectangle the right size around an open number, but not the answer's: wrong.
                val r = Rungs(b)
                val stray = r.options.values.flatten().first { o -> b.solution.none { cellsOf(b, it) == o } }
                val strayBlock = Block(stray.min() / b.width, stray.min() % b.width, stray.max() / b.width, stray.max() % b.width)
                assertEquals(false, ShikakuTraining.judge(p, b.place(strayBlock)))
            }
        }
    }

    @Test fun `another instance of the same technique is right, and a true rectangle no instance justifies plays on`() {
        var others = 0
        var unjustified = 0
        for ((rung, t) in ShikakuTraining.techniques.take(3).withIndex()) {
            for (p in ShikakuTraining.practices(t).take(12)) {
                val b = p.board as ShikakuState
                val instances = Rungs(b).instances(rung)!!
                fun blockOf(cells: Set<Int>) = Block(cells.min() / b.width, cells.min() % b.width, cells.max() / b.width, cells.max() % b.width)
                for (x in instances - setOf((p.step as Deduction).targets)) {
                    others++
                    assertEquals("$t: rectangle $x", true, ShikakuTraining.judge(p, b.place(blockOf(x))))
                }
                // A rectangle of the answer that no instance of the technique settles: no verdict.
                b.solution.firstOrNull { it !in b.blocks && cellsOf(b, it) !in instances }?.let {
                    unjustified++
                    assertNull("$t: rectangle $it", ShikakuTraining.judge(p, b.place(it)))
                }
            }
        }
        assertTrue("some practice has another instance ($others)", others > 0)
        assertTrue("some practice has an unjustified true rectangle ($unjustified)", unjustified > 0)
    }

    @Test fun `each technique's list of steps starts with the step deduce picks`() {
        // deduce keeps its own direct path (it runs in the generator's gate); this pins the two to the same first step.
        var positions = 0
        for (k in 0 until 30) {
            val start = ShikakuTraining.board(k, Difficulty.EXPERT) ?: continue
            for (p in ShikakuTraining.drill.walk(start)) {
                val b = p.board as ShikakuState
                val pick = p.step as Deduction
                val all = ShikakuTeacher.instances(b.width, b.height, b.clues, b.blocks, pick.technique)
                positions++
                assertEquals(pick.targets, all.first().targets)
            }
        }
        assertTrue(positions > 100)
    }

    @Test fun `the final board is a proved Expert board, the first whose walk needs every technique`() {
        assertEquals(techniqueNamesOf("shikaku").keys.toList(), ShikakuTraining.techniques)
        val index = (0 until 60).first { k ->
            ShikakuTraining.board(k, Difficulty.EXPERT)?.let { ShikakuTraining.techniquesUsed(it) } == ShikakuTraining.techniques.toSet()
        }
        assertEquals(Training.seed(index), ShikakuTraining.examSeed)
        val exam = Shikaku.generateVerified(ShikakuTraining.examSeed!!, Difficulty.EXPERT)
        assertNotNull(exam)
        assertEquals(exam, ShikakuTraining.exam())
        assertEquals(ShikakuTraining.techniques.toSet(), ShikakuTraining.techniquesUsed(exam!!))
    }
}
