package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.Sand
import com.joebywan.daybook.puzzles.SandLogic
import com.joebywan.daybook.puzzles.SandState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SandRulesTest {

    private val sorted = listOf(listOf(0, 0, 0), listOf(1, 1, 1), emptyList())

    // ---- the rules, one at a time ----------------------------------------------------------

    @Test
    fun `each pour rule on its own refuses a pour`() {
        val t = listOf(listOf(0, 1), listOf(0, 1, 1), listOf(1), emptyList(), listOf(0))
        assertTrue(SandLogic.canPour(t, 3, 0, 2)) // onto the same colour
        assertTrue(SandLogic.canPour(t, 3, 0, 3)) // into an empty tube
        assertFalse("from an empty tube", SandLogic.canPour(t, 3, 3, 2))
        assertFalse("into a full tube", SandLogic.canPour(t, 3, 0, 1))
        assertFalse("onto a different colour", SandLogic.canPour(t, 3, 0, 4))
        assertFalse("onto itself", SandLogic.canPour(t, 3, 0, 0))
        // The state refuses the same pours, and a refusal is no move.
        val s = SandState(t, 3, 2)
        assertNull(s.pour(0, 1))
        assertNull(s.pour(3, 2))
        assertEquals(1, s.pour(0, 2)!!.moves)
    }

    @Test
    fun `a pour moves the whole top run, or as much as fits`() {
        val t = listOf(listOf(2, 1, 1, 1), listOf(0, 1), emptyList())
        // Three blue onto a tube with room for two: two go, one stays.
        assertEquals(listOf(listOf(2, 1), listOf(0, 1, 1, 1), emptyList()), SandLogic.pour(t, 4, 0, 1))
        // Into an empty tube the whole run goes.
        assertEquals(listOf(listOf(2), listOf(0, 1), listOf(1, 1, 1)), SandLogic.pour(t, 4, 0, 2))
        // Both agree with the test's own one-layer-at-a-time rule.
        for ((a, b) in SandSupport.pours(t, 4)) assertEquals(SandSupport.pour(t, 4, a, b), SandLogic.pour(t, 4, a, b))
    }

    @Test
    fun `solved is checked by the rule, in any tube order`() {
        assertTrue(SandLogic.isSolved(sorted, 3))
        assertTrue(SandLogic.isSolved(sorted.reversed(), 3))
        assertTrue(SandLogic.isSolved(listOf(emptyList(), listOf(1, 1, 1), emptyList(), listOf(0, 0, 0)), 3))
        assertFalse("a mixed full tube", SandLogic.isSolved(listOf(listOf(0, 0, 1), listOf(1, 1, 0), emptyList()), 3))
        assertFalse("one colour split between two tubes", SandLogic.isSolved(listOf(listOf(0, 0), listOf(0), listOf(1, 1, 1)), 3))
        assertTrue(SandState(sorted, 3, 2).solved)
    }

    @Test
    fun `stuck is no pour at all on an unsorted board`() {
        val stuck = listOf(listOf(0, 1, 0), listOf(1, 0, 1))
        assertTrue(SandLogic.isStuck(stuck, 3))
        assertTrue(SandLogic.pours(stuck, 3).isEmpty())
        assertFalse("a sorted board is not stuck", SandLogic.isStuck(listOf(listOf(0, 0, 0), listOf(1, 1, 1)), 3))
        assertFalse("an empty tube always takes a pour", SandLogic.isStuck(stuck + listOf(emptyList()), 3))
        assertFalse(SandLogic.isStuck(listOf(listOf(0, 1), listOf(0, 1)), 3))
        assertTrue(SandLogic.isStuck(listOf(listOf(0, 1), listOf(1, 0, 0)), 3))
    }

    // ---- the search ------------------------------------------------------------------------

    @Test
    fun `a Solvable verdict carries a path that replays to sorted by the test's own rules`() {
        for (d in Difficulty.entries) for (day in 0 until 60) {
            val s = SandLogic.generateVerified(daySeed(day, Sand.id, d), d)!!
            val v = SandLogic.solve(s.tubes, s.height, SandLogic.GEN_BUDGET)
            assertTrue(v is SandLogic.Verdict.Solvable)
            assertTrue("${d.name} day $day", SandSupport.replays(s.tubes, s.height, (v as SandLogic.Verdict.Solvable).path))
        }
    }

    @Test
    fun `a solved board is solvable with no pours, and a small budget is never read as unsortable`() {
        assertEquals(SandLogic.Verdict.Solvable(emptyList()), SandLogic.solve(sorted, 3, 1))
        val s = Sand.generate(DailySeed.seedFor(TEST_START, Sand.id, Difficulty.EXPERT), Difficulty.EXPERT) as SandState
        assertEquals(SandLogic.Verdict.Truncated, SandLogic.solve(s.tubes, s.height, 1))
        assertEquals(SandLogic.Verdict.Truncated, SandLogic.solve(s.tubes, s.height, 5))
    }

    @Test
    fun `unsortable is said only when the test's own search exhausts every position`() {
        // Small random positions: fresh deals and positions reached by random pours, 2 to 4 colours, height 3 and 4.
        val rng = Rng(11)
        var unsortable = 0
        var sortable = 0
        repeat(400) {
            val colours = 2 + rng.nextInt(3)
            val h = 3 + rng.nextInt(2)
            val spares = 1 + rng.nextInt(2)
            var t = rng.shuffled((0 until colours).flatMap { c -> List(h) { c } }).chunked(h) + List(spares) { emptyList() }
            repeat(rng.nextInt(12)) {
                val ps = SandSupport.pours(t, h)
                if (ps.isNotEmpty()) t = rng.pick(ps).let { (a, b) -> SandSupport.pour(t, h, a, b) }
            }
            val truth = SandSupport.sortable(t, h)!!
            when (val v = SandLogic.solve(t, h, 1_000_000)) {
                is SandLogic.Verdict.Solvable -> { assertTrue("$t", truth); assertTrue(SandSupport.replays(t, h, v.path)); sortable++ }
                SandLogic.Verdict.Unsolvable -> { assertFalse("$t", truth); unsortable++ }
                SandLogic.Verdict.Truncated -> error("a small board ran out of budget: $t")
            }
        }
        println("sortable $sortable, unsortable $unsortable")
        assertTrue("the sample must have both", sortable > 50 && unsortable > 20)
    }

    @Test
    fun `every position one pour back pours forward to this one, and the real one is among them`() {
        val rng = Rng(3)
        for (d in Difficulty.entries) for (day in 0 until 20) {
            var t = (Sand.generate(daySeed(day, Sand.id, d), d) as SandState).tubes
            val h = 4
            repeat(30) {
                val ps = SandSupport.pours(t, h)
                if (ps.isEmpty()) return@repeat
                val (a, b) = rng.pick(ps)
                val next = SandSupport.pour(t, h, a, b)
                val back = SandLogic.unpours(next, h)
                for ((before, from, to) in back) assertEquals(next, SandSupport.pour(before, h, from, to))
                assertTrue("the position before is one of them", back.any { it.first == t && it.second == a && it.third == b })
                t = next
            }
        }
    }

    // ---- generation -------------------------------------------------------------------------

    @Test
    fun `a deal gives nothing away and keeps two spare tubes on every tier`() {
        assertTrue("a tube of one colour", SandLogic.givesAway(listOf(listOf(1, 1, 1, 1), listOf(0, 1, 0, 0)), 4))
        assertTrue("a run of three in a tube of four", SandLogic.givesAway(listOf(listOf(0, 1, 1, 1)), 4))
        assertTrue("a run anywhere in the tube, not only on top", SandLogic.givesAway(listOf(listOf(1, 1, 1, 0)), 4))
        assertFalse(SandLogic.givesAway(listOf(listOf(1, 1, 0, 0), listOf(0, 1, 0, 1)), 4))
        for (d in Difficulty.entries) for (day in 0 until 365) {
            val s = SandLogic.generateVerified(daySeed(day, Sand.id, d), d)!!
            val spec = SandLogic.specFor(d)
            assertEquals(spec.colours + 2, s.tubes.size)
            assertEquals(listOf(emptyList<Int>(), emptyList()), s.tubes.takeLast(2))
            assertFalse(SandLogic.givesAway(s.tubes, s.height))
            for (c in 0 until spec.colours) assertEquals(spec.height, s.tubes.sumOf { t -> t.count { it == c } })
            assertTrue(s.tubes.dropLast(2).all { it.size == spec.height })
        }
    }

    @Test
    fun `each last resort is unsolved, has every colour in full, and sorts`() {
        for (d in Difficulty.entries) {
            val s = SandLogic.lastResort(d)
            val spec = SandLogic.specFor(d)
            assertFalse(s.solved)
            assertEquals(spec.colours + 2, s.tubes.size)
            for (c in 0 until spec.colours) assertEquals(spec.height, s.tubes.sumOf { t -> t.count { it == c } })
            val v = SandLogic.solve(s.tubes, s.height, SandLogic.GEN_BUDGET) as SandLogic.Verdict.Solvable
            assertTrue(SandSupport.replays(s.tubes, s.height, v.path))
        }
    }

    @Test
    fun `generation is deterministic and a different seed makes a different board`() {
        for (d in Difficulty.entries) {
            val a = Sand.generate(42L, d)
            assertEquals(a, Sand.generate(42L, d))
            // Seeds far apart: attempt k of seed n draws Rng(n + k), as every generator here does.
            assertTrue(a != Sand.generate(1042L, d))
        }
    }

    @Test
    fun `no board the naive player always sorts is shipped`() {
        for (d in Difficulty.entries) for (day in 0 until 60) {
            val seed = daySeed(day, Sand.id, d)
            val s = SandLogic.generateVerified(seed, d)!!
            val attempt = (0 until 40).first { SandLogic.deal(seed, it, SandLogic.specFor(d)) == s.tubes }
            assertFalse(SandLogic.trivial(s.tubes, s.height, seed + attempt))
        }
    }
}
