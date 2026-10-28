package com.joebywan.daybook

import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.LitsTraining
import com.joebywan.daybook.core.MoveDrill
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.puzzles.Lits
import com.joebywan.daybook.puzzles.LitsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LITS' course (docs/TRAINING_SPEC.md). Each practice is checked against the rungs of the ladder re-derived here from
 * [LitsOracle]'s shapes and letters (nothing borrowed from the teacher), and against [LitsOracle]'s exhaustive answers
 * under the win check's rules, which have no connectivity rule (PR #15). "Wrong" is "no answer keeps this", which the
 * oracle confirms for each wrong move.
 */
class LitsTrainingTest {

    private class Shape(val cells: Set<Int>, val letter: Char)

    /** The rungs below the what-if on one board, by listing every shape each region could still hold. */
    private class Rungs(s: LitsState) {
        val w = s.width
        val h = s.height
        val shaded = s.shaded
        private val ids = s.region.distinct().sorted()
        val regions = ids.map { id -> s.region.indices.filter { s.region[it] == id } }
        private fun shadedIn(k: Int) = regions[k].filter { shaded[it] }
        private fun complete(k: Int) = shadedIn(k).size >= 4

        /** The letter on each square of a region whose four shaded squares make a tetromino. */
        private val letter: Array<Char?> = arrayOfNulls<Char>(w * h).also { out ->
            for (k in regions.indices) {
                val q = shadedIn(k)
                if (q.size == 4) LitsOracle.letter(q, w)?.let { m -> q.forEach { out[it] = m } }
            }
        }

        private fun edge(a: Int, b: Int) = (a / w == b / w && kotlin.math.abs(a - b) == 1) || kotlin.math.abs(a - b) == w

        /** Every 2x2 window as its four squares. */
        private val windows = (0 until h - 1).flatMap { r -> (0 until w - 1).map { c -> r * w + c } }
            .map { listOf(it, it + 1, it + w, it + w + 1) }

        fun geo(k: Int) = LitsOracle.shapesIn(regions[k], w).map { Shape(it.first.toSet(), it.second) }
            .filter { it.cells.containsAll(shadedIn(k)) }

        fun fillsBlock(t: Shape) = windows.any { win -> win.any { it in t.cells } && win.all { it in t.cells || shaded[it] } }
        fun clashes(t: Shape) = t.cells.any { a -> (0 until w * h).any { b -> b !in t.cells && edge(a, b) && shaded[b] && letter[b] == t.letter } }
        fun cand(k: Int) = geo(k).filter { !fillsBlock(it) && !clashes(it) }

        /** [t] and [u], shapes of two regions, can't both stand: one letter edge to edge, or a 2x2 with the shading. */
        fun conflict(t: Shape, u: Shape) =
            (t.letter == u.letter && t.cells.any { a -> u.cells.any { edge(a, it) } }) ||
                windows.any { win -> win.any { it in t.cells } && win.any { it in u.cells } && win.all { it in t.cells || it in u.cells || shaded[it] } }

        private fun common(shapes: List<Shape>): Set<Int> =
            if (shapes.isEmpty()) emptySet() else shapes.map { it.cells }.reduce { a, b -> a intersect b }

        /** The squares [shapes], all a region could hold, force: those every one covers that are not shaded yet. */
        private fun forced(k: Int, shapes: List<Shape>) = if (complete(k)) emptySet() else common(shapes).filter { !shaded[it] }.toSet()

        /** Each rung's instances, as the squares each one shades, region by region. */
        val whole = regions.indices.filter { regions[it].size == 4 && !complete(it) }.map { k -> regions[k].filter { !shaded[it] }.toSet() }
        val overlap = regions.indices.map { forced(it, geo(it)) }.filter { it.isNotEmpty() }
        private val noBlock = regions.indices.map { k -> forced(k, geo(k).filter { !fillsBlock(it) }) }

        /** The 2x2 rule where it forces something; else the letter rule, alone or with the 2x2 (one per region, so). */
        val avoid = noBlock.filter { it.isNotEmpty() }
        val clash = regions.indices.filter { noBlock[it].isEmpty() }
            .map { k -> forced(k, geo(k).filter { !clashes(it) }).ifEmpty { forced(k, cand(k)) } }.filter { it.isNotEmpty() }
        val neighbour = regions.indices.map { k ->
            forced(k, cand(k).filter { t -> regions.indices.none { b -> b != k && cand(b).none { u -> !conflict(t, u) } } })
        }.filter { it.isNotEmpty() }

        fun instances(rung: Int): List<Set<Int>>? = when (rung) {
            0 -> whole
            1 -> overlap
            2 -> avoid
            3 -> clash
            4 -> neighbour
            else -> null
        }

        /** By rung: the 2x2 and the letter rule are one rung (the teacher takes whichever region comes first). */
        fun has(rung: Int) = when (rung) {
            2, 3 -> avoid.isNotEmpty() || clash.isNotEmpty()
            else -> instances(rung)?.isNotEmpty() ?: false
        }
    }

    private fun shadedSet(s: LitsState) = s.shaded.indices.filter { s.shaded[it] }.toSet()

    @Test fun `every practice is a real position whose next step is its technique, with no simpler step on it`() {
        assertTrue(LitsTraining.drill is MoveDrill)
        for ((rung, t) in LitsTraining.techniques.withIndex()) {
            val list = LitsTraining.practices(t).take(6).toList()
            assertEquals("practices for $t", 6, list.size)
            assertEquals("distinct boards for $t", 6, list.map { it.board }.distinct().size)
            for (p in list) {
                val b = p.board as LitsState
                val answer = b.solution.indices.filter { b.solution[it] }.toSet()
                assertEquals(t, p.step.technique)
                assertEquals("the hint agrees", t, Lits.teach(b)!!.technique)
                assertEquals("one answer, with no connectivity rule", listOf(answer), LitsOracle.answers(b.width, b.height, b.region, cap = 2))
                assertTrue("walked shading is the answer's", shadedSet(b).all { it in answer })
                val r = Rungs(b)
                val lowest = if (rung == 3) 2 else rung
                for (lower in 0 until lowest) assertFalse("$t board has a rung-$lower step", r.has(lower))
                if (rung < 5) assertTrue("$t board has its own step by counting", r.has(rung))
                val d = p.step as Deduction
                assertTrue(d.targets.isNotEmpty() && d.targets.all { !b.shaded[it] && it in answer })
            }
        }
    }

    @Test fun `a move is right when it makes the step, wrong when no answer keeps it, and otherwise plays on`() {
        for (t in LitsTraining.techniques) {
            for (p in LitsTraining.practices(t).take(3)) {
                val b = p.board as LitsState
                val d = p.step as Deduction
                val answer = b.solution.indices.filter { b.solution[it] }.toSet()
                assertEquals(true, LitsTraining.judge(p, d.apply(b)))
                // An empty square off the answer: no answer keeps it shaded (the oracle agrees), so wrong.
                val stray = b.shaded.indices.first { !b.shaded[it] && it !in answer }
                assertTrue(LitsOracle.answers(b.width, b.height, b.region, given = shadedSet(b) + stray, cap = 1).isEmpty())
                assertEquals(false, LitsTraining.judge(p, b.toggle(stray)))
                // One square of a step that shades several is on the way: neither.
                if (d.targets.size > 1) assertNull(LitsTraining.judge(p, b.toggle(d.targets.first())))
            }
        }
    }

    @Test fun `another instance of the same technique is right, and a true square no instance justifies plays on`() {
        var others = 0
        var unjustified = 0
        for ((rung, t) in LitsTraining.techniques.take(5).withIndex()) {
            for (p in LitsTraining.practices(t).take(12)) {
                val b = p.board as LitsState
                val answer = b.solution.indices.filter { b.solution[it] }.toSet()
                val found = Rungs(b).instances(rung)!!
                val mine = (p.step as Deduction).targets
                assertTrue("$t: the step is an instance by counting", mine in found)
                for (x in found - setOf(mine)) {
                    others++
                    assertEquals("$t: shading $x", true, LitsTraining.judge(p, b.paint(x, true)))
                }
                // A square of the answer that no instance of the technique shades by itself: no verdict.
                b.shaded.indices.firstOrNull { !b.shaded[it] && it in answer && setOf(it) !in found }?.let {
                    unjustified++
                    assertNull("$t: square $it", LitsTraining.judge(p, b.toggle(it)))
                }
            }
        }
        assertTrue("some practice has another instance ($others)", others > 0)
        assertTrue("some practice has an unjustified true square ($unjustified)", unjustified > 0)
    }

    @Test fun `the final board is a proved Expert board, the first whose walk needs every technique`() {
        assertEquals(techniqueNamesOf("lits").keys.toList(), LitsTraining.techniques)
        val index = (0 until 60).first { k ->
            LitsTraining.board(k, Difficulty.EXPERT)?.let { LitsTraining.techniquesUsed(it) } == LitsTraining.techniques.toSet()
        }
        assertEquals(Training.seed(index), LitsTraining.examSeed)
        val exam = Lits.generateVerified(LitsTraining.examSeed!!, Difficulty.EXPERT)
        assertNotNull(exam)
        assertEquals(exam, LitsTraining.exam())
        assertEquals(LitsTraining.techniques.toSet(), LitsTraining.techniquesUsed(exam!!))
    }

    @Test fun `practice boards are proved boards only`() {
        // Training skips a seed whose board generateVerified did not prove, rather than taking generate's floor.
        for (tier in Difficulty.entries) for (k in 0 until 8) {
            assertEquals(Lits.generateVerified(Training.seed(k), tier), LitsTraining.board(k, tier))
        }
    }
}
