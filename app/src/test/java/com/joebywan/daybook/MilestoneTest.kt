package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Block
import com.joebywan.daybook.puzzles.MinesState
import com.joebywan.daybook.puzzles.Pearls
import com.joebywan.daybook.puzzles.PearlsState
import com.joebywan.daybook.puzzles.Pipes
import com.joebywan.daybook.puzzles.PipesState
import com.joebywan.daybook.puzzles.Shikaku
import com.joebywan.daybook.puzzles.ShikakuState
import com.joebywan.daybook.puzzles.Snap
import com.joebywan.daybook.puzzles.SnapState
import com.joebywan.daybook.puzzles.WhosWho
import com.joebywan.daybook.puzzles.WhosWhoLogic
import com.joebywan.daybook.ui.lineUnits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The small milestones that glow and play a note mid-game ([com.joebywan.daybook.ui.rememberMoveFeel]'s units), on
 * boards written out by hand so every expected answer is worked out here, not by the code under test.
 */
class MilestoneTest {

    @Test
    fun `a Shikaku number fits when its rectangle has its area and no other number`() {
        // One row of three: a 2 at the left, a 1 at the right.
        fun board(vararg blocks: Block) = ShikakuState(3, 1, listOf(2, null, 1), blocks.toList(), emptyList())
        assertTrue(Shikaku.fits(board(Block(0, 0, 0, 1)), 0))
        assertFalse("area 1 round a 2", Shikaku.fits(board(Block(0, 0, 0, 0)), 0))
        assertFalse("two numbers in one", Shikaku.fits(board(Block(0, 0, 0, 2)), 0))
        assertFalse("no rectangle", Shikaku.fits(board(), 0))
        assertTrue(Shikaku.fits(board(Block(0, 2, 0, 2)), 2))
    }

    @Test
    fun `a Mines square is settled once open, flagged or hit, never by what is under it`() {
        val s = MinesState(4, 1, "0101", 0, "of..", bumps = listOf(3))
        assertEquals(listOf(true, true, false, true), (0 until 4).map { s.settled(it) })
    }

    @Test
    fun `a pearl is kept when its own lines keep its rule`() {
        // 3x3, the loop round the border: a black pearl in the top-left corner, a white one beside it.
        // Edges: horizontal r * 2 + c, then vertical 6 + r * 3 + c.
        fun board(lines: Set<Int>) = PearlsState(3, "bw.......", "", String(CharArray(12) { if (it in lines) '-' else '.' }))
        val ring = setOf(0, 1, 4, 5, 6, 8, 9, 11)
        val full = board(ring)
        assertEquals(setOf(1, 0, 3), Pearls.keptPearl(full, 0)?.toSet())
        assertEquals(setOf(0, 1, 2), Pearls.keptPearl(full, 1)?.toSet())
        // Without (1,0)-(2,0) the black pearl's lower arm stops short; the white still has both corners turning.
        val open = board(ring - 9)
        assertNull(Pearls.keptPearl(open, 0))
        assertEquals(setOf(0, 1, 2), Pearls.keptPearl(open, 1)?.toSet())
        // A white pearl with one line is not kept.
        assertNull(Pearls.keptPearl(board(setOf(0)), 1))
    }

    @Test
    fun `a pipe tile is joined when every opening meets one that opens back`() {
        // 1 up, 2 right, 4 down, 8 left.
        val joined = PipesState(2, 1, listOf(2, 8), 0)
        assertTrue(Pipes.joined(joined, 0) && Pipes.joined(joined, 1))
        val loose = PipesState(2, 1, listOf(2, 1), 0)
        assertFalse(Pipes.joined(loose, 0))
        assertFalse("open to the wall", Pipes.joined(loose, 1))
    }

    @Test
    fun `a Trail leg is reached when the line meets the numbers up to it in order`() {
        // 2x2: 1 top-left, 3 top-right, 2 bottom-right.
        val wp = listOf(1, 3, 0, 2)
        fun legs(vararg path: Int) = Snap.legs(SnapState(2, 2, wp, path.toList())).map { it?.toList() }
        assertEquals(listOf(null, null), legs(0, 1))
        assertEquals(listOf(listOf(0, 2, 3), null), legs(0, 2, 3))
        assertEquals(listOf(listOf(0, 2, 3), listOf(3, 1)), legs(0, 2, 3, 1))
    }
}
