package com.joebywan.daybook

import com.joebywan.daybook.puzzles.Atom
import com.joebywan.daybook.puzzles.AtomsState
import com.joebywan.daybook.puzzles.Pair2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #422: every atom full but the molecule in two pieces looked finished and was refused with no
 * word why. Four 2s at the corners of a square: double bonds across the top and bottom fill every atom
 * in two pieces; single bonds all round fill every atom in one.
 */
class AtomsSplitTest {

    private fun square(top: Int, bottom: Int, left: Int, right: Int) = AtomsState(
        size = 3,
        atoms = listOf(Atom(0, 0, 2), Atom(0, 2, 2), Atom(2, 0, 2), Atom(2, 2, 2)),
        pairs = listOf(
            Pair2(0, 1, horizontal = true),
            Pair2(2, 3, horizontal = true),
            Pair2(0, 2, horizontal = false),
            Pair2(1, 3, horizontal = false),
        ),
        counts = listOf(top, bottom, left, right),
        solution = listOf(1, 1, 1, 1),
    )

    @Test
    fun `full atoms in two pieces are said to be split, and are not solved`() {
        val split = square(top = 2, bottom = 2, left = 0, right = 0)
        assertEquals(2, split.pieces())
        assertTrue(split.fullButSplit)
        assertFalse(split.solved)
    }

    @Test
    fun `one piece is solved and not split`() {
        val whole = square(top = 1, bottom = 1, left = 1, right = 1)
        assertEquals(1, whole.pieces())
        assertFalse(whole.fullButSplit)
        assertTrue(whole.solved)
    }

    @Test
    fun `a board still being filled is not called split`() {
        val partway = square(top = 2, bottom = 0, left = 0, right = 0)
        assertEquals(3, partway.pieces())
        assertFalse(partway.fullButSplit)
    }
}
