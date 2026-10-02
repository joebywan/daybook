package com.joebywan.daybook

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.puzzles.NonogramKeyAction
import com.joebywan.daybook.puzzles.NonogramState
import com.joebywan.daybook.puzzles.applyKey
import com.joebywan.daybook.puzzles.nonogramKeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class NonogramKeysTest {
    private fun board(cells: String = ".".repeat(12)) = NonogramState(4, 3, "1".repeat(12), cells)
    private fun act(key: Key, shift: Boolean = false) = nonogramKeyAction(key, shift)!!

    @Test
    fun keysMapAndShiftOnlyMovesAndLays() {
        assertEquals(NonogramKeyAction.Pen('#'), nonogramKeyAction(Key.F, false))
        assertEquals(NonogramKeyAction.Pen('x'), nonogramKeyAction(Key.X, false))
        assertEquals(NonogramKeyAction.Move(0, 1, true), nonogramKeyAction(Key.DirectionRight, true))
        assertNull(nonogramKeyAction(Key.F, true))
        assertNull(nonogramKeyAction(Key.Q, false))
    }

    @Test
    fun noCursorYetOnlyAnArrowStartsOne() {
        val b = board()
        assertEquals(0, b.applyKey(null, act(Key.DirectionDown)).cursor)
        val f = b.applyKey(null, act(Key.F))
        assertNull(f.cursor)
        assertSame(b, f.board)
    }

    @Test
    fun cursorClampsAndNeverWraps() {
        val b = board()
        assertEquals(0, b.applyKey(0, act(Key.DirectionLeft)).cursor)
        assertEquals(3, b.applyKey(3, act(Key.DirectionRight)).cursor)
        assertEquals(11, b.applyKey(11, act(Key.DirectionDown)).cursor)
        assertEquals(4, b.applyKey(0, act(Key.DirectionDown)).cursor)
    }

    @Test
    fun fillAndCrossToggleLikeATapAndSpaceCyclesThroughClean() {
        var b = board()
        b = b.applyKey(5, act(Key.F)).board
        assertEquals('#', b.cells[5])
        b = b.applyKey(5, act(Key.X)).board // the other pen replaces
        assertEquals('x', b.cells[5])
        b = b.applyKey(5, act(Key.X)).board
        assertEquals('.', b.cells[5])
        val seen = (1..4).scan(board()) { s, _ -> s.applyKey(2, act(Key.Spacebar)).board }.map { it.cells[2] }
        assertEquals(listOf('.', '#', 'x', '.', '#'), seen)
        assertEquals('.', b.applyKey(5, act(Key.Delete)).board.cells[5])
        assertSame(b, b.applyKey(5, act(Key.Backspace)).board)
    }

    @Test
    fun shiftArrowCarriesAMarkOverUntouchedSquaresOnly() {
        var b = board("#...")
        var at = 0
        repeat(2) {
            val r = b.applyKey(at, act(Key.DirectionRight, shift = true))
            b = r.board
            at = r.cursor!!
        }
        assertEquals("###.", b.cells.substring(0, 4))
        // a cross in the way is left alone, and so is nothing-to-carry
        val crossed = board("#x..").applyKey(0, act(Key.DirectionRight, true))
        assertEquals("#x..", crossed.board.cells.substring(0, 4))
        val blank = board()
        assertSame(blank, blank.applyKey(0, act(Key.DirectionRight, true)).board)
    }
}
