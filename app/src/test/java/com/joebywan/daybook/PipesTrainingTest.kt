package com.joebywan.daybook

import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PipesTraining
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.puzzles.Pipes
import com.joebywan.daybook.puzzles.PipesState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pipes' course (docs/TRAINING_SPEC.md). Each practice is checked against [PipesTeachingTest]'s independent enumerator of
 * answers, and against the two lower rungs re-derived here from the shapes alone: the border, then neighbours agreeing
 * on the sides they share, to a fixed point (nothing borrowed from the teacher).
 */
class PipesTrainingTest {
    private val answers = PipesTeachingTest()

    private fun turnsOf(m: Int): List<Int> = (0 until 4).map { k -> ((m shl k) or (m shr (4 - k))) and 15 }.distinct()

    /** Each tile's turns left after the border alone ([neighbours] false) or the border and neighbours to a fixed point. */
    private fun pinned(s: PipesState, neighbours: Boolean): List<List<Int>> {
        val w = s.width
        val h = s.height
        fun nb(i: Int, d: Int) = when (d) {
            0 -> if (i >= w) i - w else -1
            1 -> if (i % w < w - 1) i + 1 else -1
            2 -> if (i < w * (h - 1)) i + w else -1
            else -> if (i % w > 0) i - 1 else -1
        }
        val cand = s.cells.indices.map { i -> turnsOf(s.cells[i]).filter { m -> (0 until 4).all { d -> nb(i, d) >= 0 || m and (1 shl d) == 0 } } }
            .toMutableList()
        var changed = neighbours
        while (changed) {
            changed = false
            for (i in cand.indices) for (d in 0 until 4) {
                val j = nb(i, d)
                if (j < 0) continue
                val bit = 1 shl d
                val back = 1 shl ((d + 2) % 4)
                val open = when {
                    cand[i].all { it and bit != 0 } -> true
                    cand[i].none { it and bit != 0 } -> false
                    else -> continue
                }
                val kept = cand[j].filter { (it and back != 0) == open }
                if (kept.size < cand[j].size) { cand[j] = kept; changed = true }
            }
        }
        return cand
    }

    /** Whether some tile the rung settles is not yet sitting its one way. */
    private fun hasStep(s: PipesState, neighbours: Boolean) =
        pinned(s, neighbours).withIndex().any { (i, c) -> c.size == 1 && s.cells[i] != c[0] }

    private val rungOf = mapOf("border" to 0, "set-neighbour" to 1, "whichever-way" to 1, "no-loop" to 2)

    @Test fun `every practice is a real position whose next step is its technique, with no simpler step on it`() {
        assertEquals(rungOf.keys.toList(), PipesTraining.techniques)
        for (t in PipesTraining.techniques) {
            val list = PipesTraining.practices(t).take(6).toList()
            assertEquals("practices for $t", 6, list.size)
            assertEquals("distinct boards for $t", 6, list.map { it.board }.distinct().size)
            for (p in list) {
                val b = p.board as PipesState
                assertEquals(t, p.step.technique)
                assertEquals("the hint agrees", t, Pipes.teach(b)!!.technique)
                val all = answers.allSolutions(b.width, b.height, b.cells, cap = 2)
                assertEquals("one answer", 1, all.size)
                val answer = all[0]
                assertTrue("walked tiles are the answer's", b.turned.all { b.cells[it] == answer[it] })
                val d = p.step as Deduction
                val cell = d.targets.single()
                assertNotEquals("the step's tile is not yet set", answer[cell], b.cells[cell])
                assertEquals("the step sets it to the answer", answer[cell], (d.apply(b) as PipesState).cells[cell])
                val rung = rungOf.getValue(t)
                if (rung >= 1) assertFalse("$t board has a border step", hasStep(b, neighbours = false))
                if (rung >= 2) assertFalse("$t board has a neighbour step", hasStep(b, neighbours = true))
                if (rung == 0) assertTrue(hasStep(b, neighbours = false))
                if (rung == 1) assertTrue(hasStep(b, neighbours = true))
            }
        }
    }

    @Test fun `turning the step's tile round is never wrong, and a tile no answer keeps is`() {
        var wrongSeen = 0
        var dryPlayOn = 0
        for (t in PipesTraining.techniques) {
            for (p in PipesTraining.practices(t).take(4)) {
                val b = p.board as PipesState
                val d = p.step as Deduction
                val cell = d.targets.single()
                val answer = answers.allSolutions(b.width, b.height, b.cells, cap = 2).single()
                assertEquals(true, PipesTraining.judge(p, d.apply(b)))
                // Each quarter turn of the step's tile on the way round: never wrong, right only at its way.
                var s = b
                repeat(4) {
                    s = s.rotate(cell)
                    assertEquals(if (s.cells[cell] == answer[cell]) true else null, PipesTraining.judge(p, s))
                }
                // Another tile turned off every answer: wrong when it runs full, play on while it is dry.
                for (c in b.cells.indices) {
                    if (c == cell) continue
                    val turned = b.rotate(c)
                    if (turned.cells[c] == answer[c]) continue
                    val verdict = PipesTraining.judge(p, turned)
                    if (c in Pipes.filled(turned)) {
                        assertEquals("tile $c turned off the answer, wet", false, verdict)
                        wrongSeen++
                    } else {
                        assertNull("tile $c turned, dry", verdict)
                        dryPlayOn++
                    }
                }
            }
        }
        assertTrue(wrongSeen > 0 && dryPlayOn > 0)
    }

    @Test fun `the final board is a proved Expert board, the first whose walk needs every technique`() {
        val index = (0 until 60).first { k ->
            PipesTraining.techniquesUsed(Pipes.generate(Training.seed(k), Difficulty.EXPERT)) == PipesTraining.techniques.toSet()
        }
        assertEquals(Training.seed(index), PipesTraining.examSeed)
        assertNotNull(Pipes.generateVerified(PipesTraining.examSeed!!, Difficulty.EXPERT))
        assertEquals(PipesTraining.techniques.toSet(), PipesTraining.techniquesUsed(PipesTraining.exam()!!))
    }
}
