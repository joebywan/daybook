package com.joebywan.daybook

import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.LoopEdges
import com.joebywan.daybook.core.LoopTraining
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.puzzles.Loop
import com.joebywan.daybook.puzzles.LoopLogic
import com.joebywan.daybook.puzzles.LoopState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Loop's course (docs/TRAINING_SPEC.md). Each practice is checked against [LoopOracle]'s independent count of loops (it
 * colours squares, never edges), and the simplest rungs are re-derived here by counting: a number's sides and a dot's ways.
 */
class LoopTrainingTest {

    /** The answer's edges, from the oracle's squares. */
    private fun answer(s: LoopState): String {
        val all = LoopOracle.answers(s.size, s.clues)
        assertEquals("one answer", 1, all.size)
        return LoopOracle.loopOf(s.size, all[0])
    }

    /**
     * A number whose count settles a side: all its lines drawn with an open side left, or only as many sides open as it
     * lacks. An open side at a dot that already has two lines is shut (no line can go there), counted here by hand.
     */
    private fun countStep(s: LoopState): Boolean {
        val n = s.size
        val m = n + 1
        val h = n * m
        // The two dots at the ends of edge e, and how many lines meet at each dot.
        fun ends(e: Int) = if (e < h) listOf((e / n) * m + e % n, (e / n) * m + e % n + 1) else (e - h).let { listOf(it, it + m) }
        val lineCount = IntArray(m * m)
        for (e in s.edges.indices) if (s.edges[e] == '-') ends(e).forEach { lineCount[it]++ }
        fun open(e: Int) = s.edges[e] == '.' && ends(e).all { lineCount[it] < 2 }
        return (0 until n * n).any { q ->
            val k = s.clues[q]
            if (k == '.') return@any false
            val sides = LoopLogic.sides(n, q)
            val lines = sides.count { s.edges[it] == '-' }
            val free = sides.count(::open)
            free > 0 && (lines == k - '0' || lines + free == k - '0')
        }
    }

    @Test fun `every practice is a real position whose next step is its technique, and the step agrees with the loop`() {
        assertEquals(listOf("count", "one-way", "dead-end", "corner", "small-loop", "what-if"), LoopTraining.techniques)
        for (t in LoopTraining.techniques) {
            val list = LoopTraining.practices(t).take(6).toList()
            assertEquals("practices for $t", 6, list.size)
            assertEquals("distinct boards for $t", 6, list.map { it.board }.distinct().size)
            for (p in list) {
                val b = p.board as LoopState
                assertEquals(t, p.step.technique)
                assertEquals("the hint agrees", t, Loop.teach(b)!!.technique)
                val loop = answer(b)
                for (e in b.edges.indices) {
                    if (b.edges[e] == LoopEdges.LINE_MARK) assertEquals("walked line $e", '1', loop[e])
                    if (b.edges[e] == LoopEdges.CROSS_MARK) assertEquals("walked cross $e", '0', loop[e])
                }
                // Nothing simpler: past the first technique, no number's count settles anything by counting.
                if (t != "count") assertFalse("$t board has a count step", countStep(b))
                val after = (p.step as Deduction).apply(b) as LoopState
                assertNotEquals(b.edges, after.edges)
                for (e in b.edges.indices) if (after.edges[e] != b.edges[e]) {
                    assertEquals(loop[e] == '1', after.edges[e] == LoopEdges.LINE_MARK)
                }
            }
        }
    }

    @Test fun `a move is right when it makes the step, wrong when the loop disagrees, and otherwise plays on`() {
        var partial = 0
        for (t in LoopTraining.techniques) {
            for (p in LoopTraining.practices(t).take(3)) {
                val b = p.board as LoopState
                val after = (p.step as Deduction).apply(b) as LoopState
                assertEquals(true, LoopTraining.judge(p, after))
                val loop = answer(b)
                val open = b.edges.indices.filter { b.edges[it] == LoopEdges.OPEN_MARK }
                open.firstOrNull { loop[it] == '1' }?.let { assertEquals(false, LoopTraining.judge(p, b.tap(it))) }
                open.firstOrNull { loop[it] == '0' }?.let { e ->
                    assertEquals(false, LoopTraining.judge(p, b.paint(listOf(e), LoopEdges.LINE_MARK)))
                }
                val changed = b.edges.indices.filter { after.edges[it] != b.edges[it] }
                if (changed.size > 1) {
                    val verdict = LoopTraining.judge(p, b.paint(listOf(changed[0]), after.edges[changed[0]]))
                    assertNotEquals(false, verdict)
                    if (verdict == null) partial++
                }
            }
        }
        assertTrue(partial > 0)
    }

    @Test fun `the final board is a proved Expert board, the first whose walk needs every technique`() {
        val index = (0 until 60).first { k ->
            LoopTraining.techniquesUsed(Loop.generate(Training.seed(k), Difficulty.EXPERT)) == LoopTraining.techniques.toSet()
        }
        assertEquals("examIndex", Training.seed(index), LoopTraining.examSeed)
        assertNotNull(LoopLogic.generateVerified(LoopTraining.examSeed!!, Difficulty.EXPERT))
        assertEquals(LoopTraining.techniques.toSet(), LoopTraining.techniquesUsed(LoopTraining.exam()!!))
    }
}
