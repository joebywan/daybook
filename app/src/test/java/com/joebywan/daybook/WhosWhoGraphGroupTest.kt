package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.WhosWhoGraph
import com.joebywan.daybook.puzzles.WhosWhoGraphLogic
import com.joebywan.daybook.puzzles.WhosWhoGraphState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Group view's layout must place every item, whatever the player's marks say, clashes included. */
class WhosWhoGraphGroupTest {

    private fun assertLaidOut(s: WhosWhoGraphState) {
        val (row, _) = WhosWhoGraph.rows(s, WhosWhoGraphLogic.close(s), grouped = true)
        val n = s.n
        for (c in 0..s.board.columns) {
            val rows = (c * n until c * n + n).map { row[it] }
            assertTrue("column $c rows $rows", rows.all { it in 0 until n })
            assertEquals("column $c rows $rows", n, rows.toSet().size)
        }
    }

    @Test
    fun `an item linked to two people does not crash the grouping`() {
        // The reported board: Expert, house 3 linked to both Eve and Uma, with harp too, and piano to house 4.
        val s0 = WhosWhoGraph.generate(1L, Difficulty.EXPERT) as WhosWhoGraphState
        val n = s0.n
        val house = { slot: Int -> n + slot }
        val music = { slot: Int -> 2 * n + slot }
        val s = s0
            .withMark(0, house(2), WhosWhoGraphLogic.LINK)
            .withMark(4, house(2), WhosWhoGraphLogic.LINK)
            .withMark(0, music(4), WhosWhoGraphLogic.LINK)
            .withMark(4, music(4), WhosWhoGraphLogic.LINK)
            .withMark(house(3), music(0), WhosWhoGraphLogic.LINK)
        assertLaidOut(s)
    }

    @Test
    fun `random marks always lay out`() {
        for (d in Difficulty.entries) for (seed in 1L..20L) {
            var s = WhosWhoGraph.generate(seed, d) as WhosWhoGraphState
            val rng = Rng(seed * 31 + d.ordinal)
            repeat(12) {
                val a = rng.nextInt(s.size)
                val b = rng.nextInt(s.size)
                if (a / s.n != b / s.n) {
                    s = s.withMark(a, b, if (rng.nextInt(3) == 0) WhosWhoGraphLogic.ANTI else WhosWhoGraphLogic.LINK)
                }
                assertLaidOut(s)
            }
        }
    }
}
