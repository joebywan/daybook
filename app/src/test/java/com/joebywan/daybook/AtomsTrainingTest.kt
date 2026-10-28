package com.joebywan.daybook

import com.joebywan.daybook.core.AtomsTraining
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.puzzles.Atoms
import com.joebywan.daybook.puzzles.AtomsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Atoms' course (docs/TRAINING_SPEC.md). Each practice is checked against [AtomsTeachingTest]'s independent enumerator of
 * bondings, and against the rungs below the what-if re-derived here by counting room on each line (nothing borrowed from
 * the teacher): basic room, then with crossings shut, then with bonds that would close a group off shut.
 */
class AtomsTrainingTest {
    private val rules = AtomsTeachingTest()

    /** The rungs below the what-if on one board: which of them has a bond to lay. */
    private class Rungs(val s: AtomsState, private val crosses: (Int, Int) -> Boolean) {
        val n = s.atoms.size
        val lines = s.pairs.indices
        fun need(a: Int) = s.atoms[a].bonds - lines.sumOf { if (s.pairs[it].a == a || s.pairs[it].b == a) s.counts[it] else 0 }

        /** Atoms joined to [a] by drawn bonds and by line [plus]. */
        fun group(a: Int, plus: Int): Set<Int> {
            val seen = mutableSetOf(a)
            var grew = true
            while (grew) {
                grew = false
                for (p in lines) {
                    if (s.counts[p] == 0 && p != plus) continue
                    val (x, y) = s.pairs[p].a to s.pairs[p].b
                    if ((x in seen) != (y in seen)) { seen += x; seen += y; grew = true }
                }
            }
            return seen
        }

        /** More bonds line [p] could still take, seeing [level]: 0 basic, 1 crossings, 2 closed-off groups. */
        fun room(p: Int, level: Int): Int {
            val (a, b) = s.pairs[p].a to s.pairs[p].b
            var r = maxOf(0, minOf(2 - s.counts[p], need(a), need(b)))
            if (level >= 1 && s.counts[p] == 0 && lines.any { q -> s.counts[q] > 0 && crosses(p, q) }) r = 0
            if (level >= 2 && r > 0 && need(a) == r && need(b) == r) {
                val g = group(a, p)
                if (g.size < n && g.all { it == a || it == b || need(it) == 0 }) r--
            }
            return r
        }

        /** Every forced bond at [level], as the rule that forces it: 0 one neighbour, 1 all forced, 2 at least one. */
        fun forced(level: Int): Set<Int> = buildSet {
            for (a in 0 until n) {
                val need = need(a)
                if (need <= 0) continue
                val mine = lines.filter { s.pairs[it].a == a || s.pairs[it].b == a }
                val room = mine.associateWith { room(it, level) }
                val total = room.values.sum()
                if (total < need) continue
                // A line must take what the others cannot, and it must still have that room with everything seen.
                val any = mine.any { p -> val k = need - (total - room.getValue(p)); k > 0 && k <= room(p, 2) }
                if (!any) continue
                add(
                    when {
                        room.values.count { it > 0 } == 1 -> 0
                        need == total -> 1
                        else -> 2
                    },
                )
            }
        }

        fun has(rung: Int): Boolean = when (rung) {
            in 0..2 -> rung in forced(0)
            3 -> forced(1).isNotEmpty()
            4 -> forced(2).isNotEmpty()
            else -> false
        }
    }

    private fun rungs(s: AtomsState) = Rungs(s) { p, q -> rules.linesCross(s.atoms, s.pairs[p], s.pairs[q]) }

    @Test fun `every practice is a real position whose next step is its technique, with no simpler step on it`() {
        for ((rung, t) in AtomsTraining.techniques.withIndex()) {
            val list = AtomsTraining.practices(t).take(6).toList()
            assertEquals("practices for $t", 6, list.size)
            assertEquals("distinct boards for $t", 6, list.map { it.board }.distinct().size)
            for (p in list) {
                val b = p.board as AtomsState
                assertEquals(t, p.step.technique)
                assertEquals("the hint agrees", t, Atoms.teach(b)!!.technique)
                assertEquals("one answer", listOf(b.solution), rules.allSolutions(b.atoms, b.pairs, cap = 2))
                assertTrue("walked bonds are the answer's", b.counts.indices.all { b.counts[it] <= b.solution[it] })
                val r = rungs(b)
                for (lower in 0 until rung) assertFalse("$t board has a rung-$lower step", r.has(lower))
                if (rung < 5) assertTrue("$t board has its own step by counting", r.has(rung))
                val after = (p.step as Deduction).apply(b) as AtomsState
                assertTrue("the step lays bonds", after.counts != b.counts)
                assertTrue("and the answer has them", after.counts.indices.all { after.counts[it] <= b.solution[it] })
            }
        }
    }

    @Test fun `a move is right when it makes the step, wrong past the answer, and otherwise plays on`() {
        var partial = 0
        for (t in AtomsTraining.techniques) {
            for (p in AtomsTraining.practices(t).take(3)) {
                val b = p.board as AtomsState
                val d = p.step as Deduction
                val after = d.apply(b) as AtomsState
                assertEquals(true, AtomsTraining.judge(p, after))
                // A bond on a line the answer leaves as it is: wrong.
                val over = b.pairs.indices.first { b.counts[it] == b.solution[it] && b.counts[it] < 2 }
                assertEquals(false, AtomsTraining.judge(p, b.cycle(over)))
                // A bond of the step's, short of all of it: play on.
                val short = b.pairs.indices.firstOrNull { after.counts[it] > b.counts[it] }?.let { b.cycle(it) }
                if (short != null && short.counts != after.counts) {
                    assertNull(AtomsTraining.judge(p, short))
                    partial++
                }
            }
        }
        assertTrue(partial > 0)
    }

    @Test fun `the final board is a proved Expert board, the first whose walk needs every technique`() {
        val index = (0 until 60).first { k ->
            AtomsTraining.techniquesUsed(Atoms.generate(Training.seed(k), Difficulty.EXPERT)) == AtomsTraining.techniques.toSet()
        }
        assertEquals(Training.seed(index), AtomsTraining.examSeed)
        assertNotNull(Atoms.generateVerified(AtomsTraining.examSeed!!, Difficulty.EXPERT))
        assertEquals(AtomsTraining.techniques.toSet(), AtomsTraining.techniquesUsed(AtomsTraining.exam()!!))
    }
}
