package com.joebywan.daybook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Self-checks for [SandOracle] on hand-built boards; touches nothing in `SandLogic`, so it stands on its own. */
class SandOracleTest {

    @Test
    fun `each pour rule on its own refuses`() {
        val h = 3
        assertFalse("from an empty tube", SandOracle.canPour(listOf(emptyList(), listOf(0)), h, 0, 1))
        assertFalse("into a full tube", SandOracle.canPour(listOf(listOf(0), listOf(1, 0, 0)), h, 0, 1))
        assertFalse("onto a different colour", SandOracle.canPour(listOf(listOf(0), listOf(1)), h, 0, 1))
        assertFalse("onto itself", SandOracle.canPour(listOf(listOf(0), emptyList()), h, 0, 0))
        assertTrue("onto the same colour", SandOracle.canPour(listOf(listOf(1, 0), listOf(0)), h, 0, 1))
        assertTrue("into an empty tube", SandOracle.canPour(listOf(listOf(1, 0), emptyList()), h, 0, 1))
        assertNull(SandOracle.pour(listOf(listOf(0), listOf(1)), h, 0, 1))
    }

    @Test
    fun `a pour moves the whole top run, or as much as fits`() {
        assertEquals(listOf(listOf(1), listOf(0, 0)), SandOracle.pour(listOf(listOf(1, 0, 0), emptyList()), 3, 0, 1))
        assertEquals(listOf(listOf(0), listOf(1, 1, 1)), SandOracle.pour(listOf(listOf(0, 1, 1), listOf(1)), 3, 0, 1))
        // The target fills after one layer: the rest of the run stays behind.
        assertEquals(listOf(listOf(0), listOf(1, 0, 0)), SandOracle.pour(listOf(listOf(0, 0), listOf(1, 0)), 3, 0, 1))
        assertEquals(listOf(listOf(2), listOf(0, 0, 0, 0)), SandOracle.pour(listOf(listOf(2, 0, 0), listOf(0, 0)), 4, 0, 1))
        assertEquals(listOf(listOf(1, 0, 0), listOf(2, 2, 0, 0)), SandOracle.pour(listOf(listOf(1, 0, 0, 0), listOf(2, 2, 0)), 4, 0, 1))
    }

    @Test
    fun `solved is checked by the rule, in any tube order`() {
        assertTrue(SandOracle.isSolved(listOf(listOf(1, 1, 1), emptyList(), listOf(0, 0, 0), emptyList()), 3))
        assertTrue(SandOracle.isSolved(listOf(emptyList(), listOf(0, 0, 0), emptyList(), listOf(1, 1, 1)), 3))
        assertFalse("a mixed tube", SandOracle.isSolved(listOf(listOf(1, 1, 0), listOf(0, 0, 1), emptyList()), 3))
        assertFalse("a colour split over two tubes", SandOracle.isSolved(listOf(listOf(1, 1), listOf(1), listOf(0, 0, 0)), 3))
        val already = SandOracle.solve(listOf(listOf(1, 1, 1), emptyList(), listOf(0, 0, 0), emptyList()), 3)
        assertEquals(emptyList<Pair<Int, Int>>(), already.shortest)
    }

    @Test
    fun `a solvable board yields a shortest road that replays to solved`() {
        val start = listOf(listOf(0, 1), listOf(1, 0), emptyList())
        val r = SandOracle.solve(start, 2)
        val road = r.shortest!!
        // Each of the two mixed tubes must lose its top before it can hold one colour, and that alone is two pours,
        // after which the two colours are split, so three is the fewest.
        assertEquals(3, road.size)
        assertTrue(SandOracle.isSolved(SandOracle.replay(start, 2, road)!!, 2))

        val bigger = listOf(listOf(0, 1, 0), listOf(1, 0, 1), emptyList())
        val road2 = SandOracle.solve(bigger, 3).shortest!!
        assertTrue(SandOracle.isSolved(SandOracle.replay(bigger, 3, road2)!!, 3))
        assertNull("a road with an illegal step does not replay", SandOracle.replay(bigger, 3, listOf(0 to 1)))
    }

    @Test
    fun `unsortable boards are reported after every position is seen`() {
        val stuck = listOf(listOf(0, 1), listOf(1, 0))
        assertTrue(SandOracle.isStuck(stuck, 2))
        val r = SandOracle.solve(stuck, 2)
        assertFalse(r.solvable)
        assertEquals(1, r.positions)

        // Pours exist, but every road loops: three colours, one spare.
        val loops = listOf(listOf(2, 1, 1), listOf(2, 0, 0), listOf(2, 0, 1), emptyList())
        assertFalse(SandOracle.isStuck(loops, 3))
        val r2 = SandOracle.solve(loops, 3)
        assertFalse(r2.solvable)
        assertTrue(r2.positions > 1)
    }

    @Test
    fun `a solved board is not stuck even when nothing useful is left`() {
        assertFalse(SandOracle.isStuck(listOf(listOf(0, 0), listOf(1, 1)), 2))
        assertFalse(SandOracle.hasPour(listOf(listOf(0, 0), listOf(1, 1)), 2))
    }
}
