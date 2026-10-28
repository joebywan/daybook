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
        val fits = open.any { options.getValue(it).size == 1 }
        val reaches = (0 until w * s.height).any { cell ->
            if (taken[cell] || s.clues[cell] != null) return@any false
            val by = open.filter { c -> options.getValue(c).any { cell in it } }
            by.size == 1 && options.getValue(by[0]).count { cell in it } == 1
        }
        val common = open.any { a ->
            val opts = options.getValue(a)
            val shared = if (opts.size < 2) emptySet() else opts.reduce { x, y -> x intersect y } - a
            shared.isNotEmpty() && open.any { b ->
                b != a && options.getValue(b).size >= 2 && options.getValue(b).count { o -> o.none { it in shared } } == 1
            }
        }

        fun has(rung: Int) = when (rung) {
            0 -> fits
            1 -> reaches
            2 -> common
            else -> false
        }
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
                val step = (d.apply(b) as ShikakuState).blocks.single { it !in b.blocks }
                assertEquals(true, ShikakuTraining.judge(p, d.apply(b)))
                // A rectangle the right size around an open number, but not the answer's: wrong.
                val r = Rungs(b)
                val stray = r.options.values.flatten().first { o -> b.solution.none { cellsOf(b, it) == o } }
                val strayBlock = Block(stray.min() / b.width, stray.min() % b.width, stray.max() / b.width, stray.max() % b.width)
                assertEquals(false, ShikakuTraining.judge(p, b.place(strayBlock)))
                // Another rectangle of the answer, drawn first: play on.
                val other = b.solution.firstOrNull { it != step && it !in b.blocks }
                if (other != null) assertNull(ShikakuTraining.judge(p, b.place(other)))
            }
        }
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
