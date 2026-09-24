package com.joebywan.daybook

import androidx.compose.ui.geometry.Offset
import com.joebywan.daybook.puzzles.Atom
import com.joebywan.daybook.puzzles.Atoms
import com.joebywan.daybook.puzzles.AtomsState
import com.joebywan.daybook.puzzles.Pair2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Drag-to-bond, checked as geometry rather than through Compose.
 *
 * The board is written out by hand rather than generated, so the expected hits are arithmetic a
 * reader can do in their head: one lattice step is 100px, so atom (r, c) sits at
 * (100c + 50, 100r + 50).
 */
class AtomsDragTest {

    private val step = 100f

    /** An L: two atoms along row 0, a third hanging below the right-hand one. */
    private val ell = AtomsState(
        size = 3,
        atoms = listOf(Atom(0, 0, 1), Atom(0, 2, 2), Atom(2, 2, 1)),
        pairs = listOf(Pair2(0, 1, horizontal = true), Pair2(1, 2, horizontal = false)),
        counts = listOf(0, 0),
        solution = listOf(1, 1),
    )

    @Test
    fun `a touch on an atom finds it, and one between atoms finds none`() {
        assertEquals(0, Atoms.atomAt(ell, Offset(50f, 50f), step))
        assertEquals(2, Atoms.atomAt(ell, Offset(250f, 250f), step))
        // On the rim of the drawn circle (radius 0.34) but inside the 0.42 reach.
        assertEquals(1, Atoms.atomAt(ell, Offset(250f + 38f, 50f), step))
        // Halfway along a bond belongs to no atom — that is the tap-to-cycle target.
        assertNull(Atoms.atomAt(ell, Offset(150f, 50f), step))
    }

    @Test
    fun `dragging out along a line picks that bond, either way round`() {
        assertEquals(0, Atoms.dragTarget(ell, from = 0, point = Offset(150f, 50f), stepPx = step))
        assertEquals(1, Atoms.dragTarget(ell, from = 1, point = Offset(250f, 150f), stepPx = step))
        // From the far end of the same pair.
        assertEquals(0, Atoms.dragTarget(ell, from = 1, point = Offset(150f, 50f), stepPx = step))
        // Past the far atom still counts; the finger overshoots constantly.
        assertEquals(1, Atoms.dragTarget(ell, from = 1, point = Offset(250f, 290f), stepPx = step))
    }

    @Test
    fun `a wobble inside the atom reaches for nothing`() {
        assertNull(Atoms.dragTarget(ell, from = 0, point = Offset(70f, 50f), stepPx = step))
        // Nor does a drag off into empty space, away from every line out of the atom.
        assertNull(Atoms.dragTarget(ell, from = 0, point = Offset(150f, 200f), stepPx = step))
        // Straying more than a third of a step across the line drops the lock-on.
        assertEquals(0, Atoms.dragTarget(ell, from = 0, point = Offset(150f, 80f), stepPx = step))
        assertNull(Atoms.dragTarget(ell, from = 0, point = Offset(150f, 90f), stepPx = step))
        // Nor one heading away from the only neighbour.
        assertNull(Atoms.dragTarget(ell, from = 0, point = Offset(50f, 250f), stepPx = step))
    }

    @Test
    fun `a drag lays one bond and costs one move`() {
        val next = ell.link(0)!!
        assertEquals(listOf(1, 0), next.counts)
        assertEquals(1, next.moves)
    }

    @Test
    fun `a drag never takes a bond away`() {
        assertNull(ell.copy(counts = listOf(1, 0)).link(0))
        assertNull(ell.copy(counts = listOf(2, 0)).link(0))
    }

    @Test
    fun `a drag that would cross an existing bond is refused`() {
        // A vertical pair and a horizontal pair through the same middle cell.
        val plus = AtomsState(
            size = 3,
            atoms = listOf(Atom(0, 1, 1), Atom(2, 1, 1), Atom(1, 0, 1), Atom(1, 2, 1)),
            pairs = listOf(Pair2(0, 1, horizontal = false), Pair2(2, 3, horizontal = true)),
            counts = listOf(1, 0),
            solution = listOf(1, 0),
        )
        assertNull(plus.link(1))
        // With the vertical bond gone the same drag is fine.
        assertEquals(listOf(0, 1), plus.copy(counts = listOf(0, 0)).link(1)!!.counts)
    }
}
