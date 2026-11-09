package com.joebywan.daybook

import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.core.UnblockTraining
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.puzzles.Unblock
import com.joebywan.daybook.puzzles.UnblockLogic
import com.joebywan.daybook.puzzles.UnblockState
import com.joebywan.daybook.puzzles.UnblockTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unblock's course (docs/TRAINING_SPEC.md). Every practice's slide is checked against [UnblockOracle]'s count of fewest
 * moves (pictures and single steps, nothing shared with the teacher's search), and a slide the course calls wrong is one
 * the oracle says goes no nearer.
 */
class UnblockTrainingTest {

    @Test fun `every practice is a real position whose next step is its technique and one nearer by the oracle`() {
        assertEquals(techniqueNamesOf("unblock").keys.filter { it != UnblockTeacher.EXIT }, UnblockTraining.techniques)
        for (t in UnblockTraining.techniques) {
            val list = UnblockTraining.practices(t).take(4).toList()
            assertEquals("practices for $t", 4, list.size)
            assertEquals("distinct boards for $t", 4, list.map { it.board }.distinct().size)
            for (p in list) {
                val b = p.board as UnblockState
                assertEquals(t, p.step.technique)
                assertEquals("the hint agrees", t, Unblock.teach(b)!!.technique)
                val left = UnblockOracle.fewest(b.cars)
                assertEquals("on the hints' walk, par less moves", b.par - b.moves, left)
                val after = (p.step as Deduction).apply(b) as UnblockState
                assertEquals(left - 1, UnblockOracle.fewest(after.cars))
                assertEquals(true, UnblockTraining.judge(p, after))
                if (t == UnblockTeacher.CLEAR) {
                    // The car moved stands in the red car's row, between it and the exit.
                    val red = b.cars[0]
                    val moved = b.cars.indices.first { b.cars[it] != after.cars[it] }
                    assertTrue(b.cars[moved].cells().any { it / 6 == red.row && it % 6 >= red.col + red.length })
                }
                // Every slide the oracle says goes no nearer is wrong; every one it says goes nearer is right or plays on.
                for (m in UnblockLogic.slides(b.cars)) {
                    val next = b.slide(m.car, m.by)!!
                    val verdict = UnblockTraining.judge(p, next)
                    if (UnblockOracle.fewest(next.cars) != left - 1) assertEquals("$m", false, verdict) else assertTrue("$m", verdict != false)
                }
            }
        }
    }

    @Test fun `the final board's walk needs every technique`() {
        val exam = UnblockTraining.exam()!!
        // And it is the first such seed.
        val k = (0 until 400).first { i ->
            UnblockTraining.board(i, Difficulty.EXPERT)?.let { UnblockTraining.techniquesUsed(it).containsAll(UnblockTraining.techniques) } == true
        }
        assertEquals("the first seed whose Expert walk needs all three is $k", UnblockTraining.board(k, Difficulty.EXPERT), exam)
        assertTrue(UnblockTraining.techniquesUsed(exam).containsAll(UnblockTraining.techniques))
        assertFalse(exam.solved)
        assertNull(Training.of("unblock")?.takeIf { it !== UnblockTraining })
    }
}
