package com.joebywan.daybook

import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.TentsTraining
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.puzzles.Tents
import com.joebywan.daybook.puzzles.TentsLogic
import com.joebywan.daybook.puzzles.TentsState
import com.joebywan.daybook.puzzles.TentsTeacher
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
 * Tents' course (docs/TRAINING_SPEC.md). Each practice is checked against the rungs of the ladder re-derived here by
 * counting squares (nothing borrowed from the teacher), and against [TentsOracle]'s count of answers, which walks trees
 * rather than squares. Grass a practice judges wrong is grass where the one answer has a tent.
 */
class TentsTrainingTest {

    /** The rungs on one board. Grass, the player's and the kind that sprouts round a tent, is known: no tent there. */
    private class Rungs(s: TentsState) {
        val n = s.size
        private val trees = s.trees
        private val seen = s.seen
        private fun orth(i: Int) = listOf(-n, n, -1, 1).map { i + it }.filter { j -> j in 0 until n * n && (j / n == i / n || j % n == i % n) }
        private fun around(i: Int) = (0 until n * n).filter { j -> j != i && kotlin.math.abs(j / n - i / n) <= 1 && kotlin.math.abs(j % n - i % n) <= 1 }
        private fun tent(i: Int) = seen[i] == TentsLogic.TENT
        val free = (0 until n * n).filter { !trees[it] && seen[it] == 0 }.toSet()

        val noTree = free.any { x -> orth(x).none { trees[it] } }
        private val lines = (0 until n).map { r -> List(n) { r * n + it } to s.rowCounts[r] } +
            (0 until n).map { c -> List(n) { it * n + c } to s.colCounts[c] }
        val line = lines.any { (l, want) ->
            val open = l.count { it in free }
            val need = want - l.count { tent(it) }
            open > 0 && (need == 0 || need == open)
        }
        private val treeList = (0 until n * n).filter { trees[it] }
        private fun spots(t: Int) = orth(t).filter { !trees[it] && seen[it] != TentsLogic.GRASS }
        val oneSpot = treeList.any { t -> spots(t).let { it.size == 1 && it[0] in free } }
        val shared = treeList.any { t ->
            val c = spots(t)
            c.isNotEmpty() && free.any { x -> x !in c && c.all { it in around(x) } }
        }
        /** A tree whose tent is beside it and beside no other tree. */
        private fun served(t: Int) = orth(t).any { tent(it) && orth(it).count { j -> trees[j] } == 1 }
        val servedSquare = free.any { x -> orth(x).filter { trees[it] }.let { near -> near.isNotEmpty() && near.all(::served) } }

        fun has(t: String) = when (t) {
            TentsTeacher.NO_TREE -> noTree
            TentsTeacher.LINE_DONE, TentsTeacher.LINE_FULL -> line
            TentsTeacher.ONE_SPOT -> oneSpot
            TentsTeacher.SHARED -> shared
            else -> servedSquare
        }
    }

    @Test fun `every practice is a real position whose next step is its technique, with no simpler step on it`() {
        assertEquals(techniqueNamesOf("tents").keys.filter { it != TentsTeacher.TOUCH }, TentsTraining.techniques)
        val levels = listOf(TentsTeacher.NO_TREE, TentsTeacher.LINE_DONE, TentsTeacher.ONE_SPOT, TentsTeacher.SHARED, TentsTeacher.SERVED)
        for (t in TentsTraining.techniques) {
            val list = TentsTraining.practices(t).take(6).toList()
            assertEquals("practices for $t", 6, list.size)
            assertEquals("distinct boards for $t", 6, list.map { it.board }.distinct().size)
            for (p in list) {
                val b = p.board as TentsState
                val n = b.size
                assertEquals(t, p.step.technique)
                assertEquals("the hint agrees", t, Tents.teach(b)!!.technique)
                assertEquals("one answer", 1, TentsOracle.count(n, b.trees, b.rowCounts, b.colCounts))
                val tents = b.solution.indices.filter { b.solution[it] }
                assertTrue("the stored answer keeps the clues", (0 until n).all { r -> tents.count { it / n == r } == b.rowCounts[r] && tents.count { it % n == r } == b.colCounts[r] })
                assertTrue("walked tents are the answer's", b.marks.indices.all { b.marks[it] != TentsLogic.TENT || b.solution[it] })
                assertTrue("walked grass is not", b.seen.indices.all { b.seen[it] != TentsLogic.GRASS || !b.solution[it] })
                val r = Rungs(b)
                // Lines done and lines full are one rung: the ladder looks at each line for either.
                val level = levels.indexOf(if (t == TentsTeacher.LINE_FULL) TentsTeacher.LINE_DONE else t)
                for (lower in levels.take(level)) assertFalse("$t board has a $lower step", r.has(lower))
                assertTrue("$t board has its own step by counting", r.has(t))
                val d = p.step as Deduction
                // A step may restate grass already laid among the squares it rules out, never a tree's square.
                assertTrue(d.targets.any { it in r.free } && d.targets.none { b.trees[it] })
                val after = d.apply(b) as TentsState
                for (c in d.targets) assertEquals(b.solution[c], after.marks[c] == TentsLogic.TENT)
            }
        }
    }

    @Test fun `a move is right when it makes the step, wrong on a tent or grass no answer keeps, and otherwise plays on`() {
        for (t in TentsTraining.techniques) {
            for (p in TentsTraining.practices(t).take(3)) {
                val b = p.board as TentsState
                val d = p.step as Deduction
                assertEquals(true, TentsTraining.judge(p, d.apply(b)))
                val free = Rungs(b).free
                // A tent outside the answer is the teacher's mistake.
                assertEquals(false, TentsTraining.judge(p, b.withMark(free.first { !b.solution[it] }, TentsLogic.TENT)))
                // Grass on the answer's tent is wrong here, though the game's hints read grass as notes.
                free.firstOrNull { b.solution[it] }?.let { tent ->
                    val grassed = b.withMark(tent, TentsLogic.GRASS)
                    assertNull("the game's hint does not call it a mistake", Tents.teach(grassed)?.takeIf { it.mistake })
                    assertEquals(false, TentsTraining.judge(p, grassed))
                }
                // One square of several is on the way: neither.
                if (d.targets.size > 1) {
                    val c = d.targets.first()
                    val one = b.withMark(c, if (b.solution[c]) TentsLogic.TENT else TentsLogic.GRASS)
                    if (!d.isReached(one)) assertNull(TentsTraining.judge(p, one))
                }
            }
        }
    }

    @Test fun `another instance of the same technique is right, and true grass no instance justifies plays on`() {
        var others = 0
        var unjustified = 0
        for (p in TentsTraining.practices(TentsTeacher.ONE_SPOT).take(12)) {
            val b = p.board as TentsState
            val n = b.size
            val free = Rungs(b).free
            fun orth(i: Int) = listOf(-n, n, -1, 1).map { i + it }.filter { j -> j in 0 until n * n && (j / n == i / n || j % n == i % n) }
            // Every instance by counting: a tree with one square left that is not a tree or grass, and that square free.
            val spots = b.trees.indices.filter { b.trees[it] }
                .mapNotNull { t -> orth(t).filter { !b.trees[it] && b.seen[it] != TentsLogic.GRASS }.singleOrNull()?.takeIf { it in free } }.toSet()
            val mine = (p.step as Deduction).targets
            for (x in spots - mine) {
                others++
                assertEquals("tent $x", true, TentsTraining.judge(p, b.withMark(x, TentsLogic.TENT)))
            }
            // Grass is never a step of this technique: true grass gets no verdict.
            free.firstOrNull { !b.solution[it] }?.let { c ->
                unjustified++
                assertNull("true grass $c", TentsTraining.judge(p, b.withMark(c, TentsLogic.GRASS)))
            }
        }
        assertTrue("some practice has another instance ($others)", others > 0)
        assertTrue("some practice has unjustified true grass ($unjustified)", unjustified > 0)
    }

    @Test fun `the final board is a proved Expert board, the first whose walk needs every technique`() {
        val index = (0 until 60).first { k ->
            TentsTraining.techniquesUsed(Tents.generate(Training.seed(k), Difficulty.EXPERT)) == TentsTraining.techniques.toSet()
        }
        assertEquals(Training.seed(index), TentsTraining.examSeed)
        assertNotNull(TentsLogic.generateVerified(TentsTraining.examSeed!!, Difficulty.EXPERT))
        assertEquals(TentsTraining.techniques.toSet(), TentsTraining.techniquesUsed(TentsTraining.exam()!!))
    }

    @Test fun `the listed facts start with the generator's own pick, all along a walk`() {
        for (tier in Difficulty.entries) for (k in 0 until 10) {
            val b = Tents.generate(Training.seed(k), tier) as TentsState
            val st = IntArray(b.size * b.size)
            while (true) {
                val f = TentsTeacher.nextFact(b.size, b.trees, b.rowCounts, b.colCounts, st) ?: break
                val g = TentsTeacher.facts(b.size, b.trees, b.rowCounts, b.colCounts, st).first()
                assertEquals(listOf(f.technique, f.targets, f.value, f.cited), listOf(g.technique, g.targets, g.value, g.cited))
                for (i in f.targets) st[i] = f.value
            }
        }
    }

    @Test fun `tents routes round-trip`() {
        listOf(Route.Training(puzzle = "tents"), Route.Training("served", puzzle = "tents"), Route.Training("exam", puzzle = "tents"))
            .forEach { assertEquals(it, decodeRoute(encodeRoute(it))) }
    }
}
