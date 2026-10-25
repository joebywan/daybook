package com.joebywan.daybook

import com.joebywan.daybook.DominoRulesTest.Companion.oracle
import com.joebywan.daybook.DominoRulesTest.Companion.planted
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.DominoLogic
import com.joebywan.daybook.puzzles.DominoState
import com.joebywan.daybook.puzzles.DominoTeacher
import com.joebywan.daybook.puzzles.Placement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Dominoes hint solver. On generated boards every step must be the answer's (the answer is
 * unique, so that is soundness); because on a one-answer board every true fact is derivable, which
 * cannot tell reasoning from peeking, on boards with *several* answers every step must hold for every
 * answer still possible ([DominoOracle]).
 */
class DominoTeachingTest {

    private fun same(s: DominoState, d: Int, a: Placement, b: Placement) =
        a == b || (s.tray[d].isDouble && a == Placement(b.second, b.first))

    /** Walks [start] by hints alone, asserting each step is the answer's; the steps taken. */
    private fun walk(start: DominoState, label: String): List<DominoTeacher.Step> {
        var s = start
        val steps = ArrayList<DominoTeacher.Step>()
        while (!s.solved) {
            assertTrue("$label: walk did not finish", steps.size <= s.tray.size)
            val step = DominoTeacher.teach(s)
            assertNotNull("$label: no step on an unsolved board", step)
            step!!
            assertFalse("$label: fell back", step.fallback)
            assertFalse("$label: a correct board is never a mistake", step.mistake)
            assertTrue(step.technique in DominoTeacher.TECHNIQUES)
            assertTrue("$label: ${step.technique} is the answer's", same(s, step.domino, step.placement!!, s.answer[step.domino]))
            assertEquals(null, s.placed[step.domino])
            val next = DominoTeacher.applyTo(s, step)
            assertTrue(DominoTeacher.reached(next, step))
            assertFalse(DominoTeacher.reached(s, step))
            s = next
            steps += step
        }
        return steps
    }

    @Test
    fun `hints alone finish every daily board of a year, with no fallback and no mistake`() {
        for (d in Difficulty.entries) for (seed in seeds(365, d, "dominoes")) {
            walk(DominoLogic.generateVerified(seed, d)!!, "$d/$seed")
        }
    }

    @Test
    fun `hints stay sound and available from boards a player made, not just the solver's own path`() {
        val rng = Rng(5150)
        var checked = 0
        for (d in Difficulty.entries) for (seed in seeds(40, d, "dominoes-player")) {
            val full = DominoLogic.generateVerified(seed, d)!!
            repeat(6) {
                var s = full
                for (k in s.tray.indices) if (rng.nextInt(10) < rng.nextInt(1, 9)) s = DominoLogic.place(s, k, s.answer[k])
                if (s.solved) return@repeat
                walk(s, "$d/$seed from ${s.placed}")
                checked++
            }
        }
        assertTrue("barely checked anything: $checked", checked > 500)
    }

    @Test
    fun `on boards with several answers, a step holds for every answer still possible`() {
        val rng = Rng(8675309)
        var checked = 0
        var ambiguous = 0
        repeat(3000) {
            val base = planted(rng, rng.nextInt(2, 5), rng.nextInt(2, 4)) ?: return@repeat
            val all = oracle(base, cap = 500)
            if (all.isEmpty() || all.size == 500) return@repeat
            if (all.size > 1) ambiguous++
            // Some of one answer's dominoes down, so the steps start from a part-played board.
            val pick = all[rng.nextInt(all.size)]
            var s = base
            for (k in s.tray.indices) if (rng.nextInt(3) == 0) s = DominoLogic.place(s, k, pick[k])
            val possible = all.filter { a -> s.placed.indices.all { k -> s.placed[k] == null || same(s, k, s.placed[k]!!, a[k]) } }
            val step = DominoTeacher.deduce(s.layout, s.placed) ?: return@repeat
            for (a in possible) assertTrue("${step.technique} on $base with ${s.placed}: ${step.placement} vs $a", same(s, step.domino, step.placement!!, a[step.domino]))
            checked++
        }
        println("several-answer sample: $checked steps, $ambiguous ambiguous boards")
        assertTrue("barely checked anything: $checked", checked > 300)
        assertTrue("the sample must hold boards with several answers: $ambiguous", ambiguous > 300)
    }

    // ---- mistakes ----------------------------------------------------------------------------------

    @Test
    fun `a wrong domino, a right one turned round and a right one on wrong squares are each a mistake, and Show me lifts it`() {
        var flipped = 0
        var moved = 0
        var swapped = 0
        for (d in Difficulty.entries) for (seed in seeds(30, d, "dominoes-mistake")) {
            val s = DominoLogic.generateVerified(seed, d)!!
            val k = s.tray.indices.first { !s.tray[it].isDouble }
            val p = s.answer[k]
            fun check(b: DominoState, what: String) {
                val step = DominoTeacher.teach(b)!!
                assertTrue("$d/$seed $what: ${step.technique}", step.mistake)
                val fixed = DominoTeacher.applyTo(b, step)
                assertTrue(DominoTeacher.reached(fixed, step))
                assertFalse("$d/$seed: after lifting, no mistake", DominoTeacher.teach(fixed)!!.mistake)
            }
            check(DominoLogic.place(s, k, Placement(p.second, p.first)), "turned round"); flipped++
            // The same domino on another free pair.
            val other = (0 until s.width * s.height).flatMap { a -> (a + 1 until s.width * s.height).map { Placement(a, it) } }
                .firstOrNull { it != p && Placement(it.second, it.first) != p && DominoLogic.canPlace(s, k, it) }
            if (other != null) { check(DominoLogic.place(s, k, other), "moved"); moved++ }
            // Another domino on this one's squares.
            val j = s.tray.indices.first { it != k }
            check(DominoLogic.place(s, j, p), "swapped"); swapped++
            // And the right one, the right way round, is never a mistake.
            assertFalse(DominoTeacher.teach(DominoLogic.place(s, k, p))!!.mistake)
        }
        assertTrue(flipped > 50 && moved > 50 && swapped > 50)
    }

    @Test
    fun `a board the ladder cannot finish gets the honest fallback, never a guess`() {
        // Strip every rule from a generated board but keep its answer: not ours, several answers.
        val s = DominoLogic.generateVerified(seeds(1, Difficulty.HARD, "dominoes").first(), Difficulty.HARD)!!
        val bare = s.copy(regionOf = List(s.regionOf.size) { -1 }, rules = emptyList())
        val step = DominoTeacher.teach(bare)!!
        assertTrue(step.fallback)
        assertEquals(DominoTeacher.FALLBACK, step.technique)
        assertEquals(s.answer[step.domino], step.placement)
        assertEquals(null, DominoTeacher.deduce(bare.layout, bare.placed))
    }

    // ---- text and highlight ------------------------------------------------------------------------

    @Test
    fun `every nudge and explanation fits the panel, and highlights point at the board`() {
        var longest = 0
        for (d in Difficulty.entries) for (seed in seeds(150, d, "dominoes-text")) {
            val s = DominoLogic.generateVerified(seed, d)!!
            val steps = walk(s, "$d/$seed") + listOfNotNull(DominoTeacher.teach(DominoLogic.place(s, 0, Placement(s.answer[0].second, s.answer[0].first))))
            for (step in steps) {
                assertTrue("nudge ${step.nudge.length}: ${step.nudge}", step.nudge.length <= DominoTeacher.MAX_NUDGE)
                assertTrue("explanation ${step.explanation.length}: ${step.explanation}", step.explanation.length <= DominoTeacher.MAX_EXPLANATION)
                assertFalse("no 'Pips' anywhere the player reads", (step.nudge + step.explanation).contains("Pips"))
                longest = maxOf(longest, step.explanation.length)
                val n = s.width * s.height
                for (i in step.focus + step.cited + step.targets) {
                    assertTrue("index $i", i in 0 until n || i - DominoTeacher.TRAY in s.tray.indices || i - DominoTeacher.RULE in s.rules.indices)
                }
            }
        }
        println("longest explanation: $longest")
    }

    // ---- coverage ----------------------------------------------------------------------------------

    /**
     * A measurement, printed and written to `app/build/reports/dominoes-teaching-coverage.txt`: which
     * technique a player walking a board by hints alone needs, per tier. Nothing can fall back on a
     * generated board (the walk test above asserts it), so this records the mix.
     */
    @Test
    fun `coverage - which techniques boards need, per difficulty`() {
        val perTier = 200
        val report = StringBuilder()
        report.appendLine("Dominoes teaching coverage: $perTier boards per tier, walked from empty by hints alone")
        for (d in Difficulty.entries) {
            val stepCounts = DominoTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            val boardCounts = DominoTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            var total = 0
            for (seed in seeds(perTier, d, "dominoes-coverage")) {
                val steps = walk(DominoLogic.generateVerified(seed, d)!!, "$d/$seed")
                steps.forEach { stepCounts[it.technique] = stepCounts.getValue(it.technique) + 1 }
                steps.map { it.technique }.distinct().forEach { boardCounts[it] = boardCounts.getValue(it) + 1 }
                total += steps.size
            }
            report.appendLine()
            report.appendLine("${d.name}: $total steps, ${"%.1f".format(total / perTier.toDouble())} per board")
            for (t in DominoTeacher.TECHNIQUES) {
                report.appendLine(
                    "  %-13s %5d steps (%5.1f%%)   needed on %3d/%d boards (%5.1f%%)".format(
                        t, stepCounts.getValue(t), 100.0 * stepCounts.getValue(t) / total,
                        boardCounts.getValue(t), perTier, 100.0 * boardCounts.getValue(t) / perTier,
                    )
                )
            }
        }
        println(report)
        writeReport("dominoes-teaching-coverage.txt", report.toString())
    }
}
