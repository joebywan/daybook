package com.joebywan.daybook

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.puzzles.Pearls
import com.joebywan.daybook.puzzles.PearlsGrid
import com.joebywan.daybook.puzzles.PearlsKeyAction
import com.joebywan.daybook.puzzles.PearlsState
import com.joebywan.daybook.puzzles.applyKey
import com.joebywan.daybook.puzzles.pearlsKeyAction
import com.joebywan.daybook.ui.gridCellAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The keyboard, and the pure geometry the finger goes through (edge hit testing and the drag's trail). */
class PearlsKeysTest {
    private val g = PearlsGrid.of(4)
    private fun board() = PearlsState(4, "b" + ".".repeat(15), "")
    private fun act(key: Key, shift: Boolean = false) = pearlsKeyAction(key, shift)!!

    @Test
    fun keysMap() {
        assertEquals(PearlsKeyAction.Move(0, 1, true), pearlsKeyAction(Key.DirectionRight, true))
        assertEquals(PearlsKeyAction.Move(1, 0, false), pearlsKeyAction(Key.DirectionDown, false))
        assertEquals(PearlsKeyAction.Cross, pearlsKeyAction(Key.X, false))
        assertNull(pearlsKeyAction(Key.X, true))
        assertNull(pearlsKeyAction(Key.Q, false))
    }

    @Test
    fun noCursorYetOnlyAnArrowStartsOne() {
        val b = board()
        assertEquals(0, b.applyKey(null, false, act(Key.DirectionDown)).cursor)
        val x = b.applyKey(null, false, act(Key.X))
        assertNull(x.cursor)
        assertSame(b, x.board)
    }

    @Test
    fun shiftArrowDrawsAndRubsOutAndMoves() {
        val r = board().applyKey(0, false, act(Key.DirectionRight, true))
        assertEquals(1, r.cursor)
        assertEquals('-', r.board.edges[g.between(0, 1)])
        assertEquals(1, r.board.moves)
        val back = r.board.applyKey(1, false, act(Key.DirectionLeft, true))
        assertEquals('.', back.board.edges[g.between(0, 1)])
        // Off the board's edge: the cursor stays and nothing is drawn.
        val edge = board().applyKey(0, false, act(Key.DirectionUp, true))
        assertEquals(0, edge.cursor)
        assertSame(edge.board, edge.board)
    }

    @Test
    fun xThenAnArrowCrossesThatEdgeAndTheCursorStays() {
        val armed = board().applyKey(5, false, act(Key.X))
        assertTrue(armed.armed)
        val crossed = armed.board.applyKey(5, true, act(Key.DirectionRight))
        assertEquals(5, crossed.cursor)
        assertFalse(crossed.armed)
        assertEquals('x', crossed.board.edges[g.between(5, 6)])
        // X twice disarms.
        assertFalse(armed.board.applyKey(5, true, act(Key.X)).armed)
    }

    @Test
    fun aTapNamesTheEdgeWhoseMiddleIsNearest() {
        // Square units: square (r, c) spans [c, c+1] x [r, r+1].
        assertEquals(g.between(0, 1), Pearls.edgeNear(4, 1.05f, 0.45f))
        assertEquals(g.between(0, 4), Pearls.edgeNear(4, 0.5f, 1.1f))
        assertEquals(g.between(10, 14), Pearls.edgeNear(4, 2.6f, 3.0f))
        // The middle of a square, and the outer frame, name nothing.
        assertEquals(-1, Pearls.edgeNear(4, 1.5f, 1.5f))
        assertEquals(-1, Pearls.edgeNear(4, 0.02f, 0.5f))
        assertEquals(-1, Pearls.edgeNear(4, 3.98f, 3.5f))
    }

    @Test
    fun aDragTrailAddsNeighboursAndBacksUp() {
        var p = Pearls.trail(emptyList(), 0, 4)
        p = Pearls.trail(p, 1, 4)
        p = Pearls.trail(p, 5, 4)
        assertEquals(listOf(0, 1, 5), p)
        assertEquals(listOf(0, 1), Pearls.trail(p, 1, 4))
        // Not a neighbour of the last square: ignored.
        assertEquals(p, Pearls.trail(p, 15, 4))
        assertEquals(p, Pearls.trail(p, 5, 4))
        assertEquals(5, gridCellAt(Offset(1.2f, 1.9f), 1f, 4, 4))
        assertEquals(15, gridCellAt(Offset(9f, 9f), 1f, 4, 4))
    }
}
