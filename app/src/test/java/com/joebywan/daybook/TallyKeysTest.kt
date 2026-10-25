package com.joebywan.daybook

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.PenKeyAction
import com.joebywan.daybook.puzzles.TallyState
import com.joebywan.daybook.puzzles.tallyKeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class TallyKeysTest {
    private fun board(marks: String = ".".repeat(9)) = TallyState(3, "472935618", "101011110", marks)
    private fun act(key: Key, shift: Boolean = false) = tallyKeyAction(key, shift)!!

    @Test
    fun keysMapAndShiftOnlyMovesAndLays() {
        assertEquals(PenKeyAction.Pen('x'), tallyKeyAction(Key.X, false))
        assertEquals(PenKeyAction.Pen('x'), tallyKeyAction(Key.R, false))
        assertEquals(PenKeyAction.Pen('o'), tallyKeyAction(Key.K, false))
        assertEquals(PenKeyAction.Pen('o'), tallyKeyAction(Key.O, false))
        assertEquals(PenKeyAction.Move(0, 1, true), tallyKeyAction(Key.DirectionRight, true))
        assertNull(tallyKeyAction(Key.X, true))
        assertNull(tallyKeyAction(Key.Q, false))
    }

    @Test
    fun noCursorYetOnlyAnArrowStartsOne() {
        val b = board()
        assertEquals(0, b.applyKey(null, act(Key.DirectionDown)).cursor)
        val x = b.applyKey(null, act(Key.X))
        assertNull(x.cursor)
        assertSame(b, x.board)
    }

    @Test
    fun cursorClampsAndNeverWraps() {
        val b = board()
        assertEquals(0, b.applyKey(0, act(Key.DirectionLeft)).cursor)
        assertEquals(2, b.applyKey(2, act(Key.DirectionRight)).cursor)
        assertEquals(8, b.applyKey(8, act(Key.DirectionDown)).cursor)
        assertEquals(3, b.applyKey(0, act(Key.DirectionDown)).cursor)
    }

    @Test
    fun removeAndKeepToggleLikeATapAndSpaceCyclesThroughUntouched() {
        var b = board()
        b = b.applyKey(4, act(Key.X)).board
        assertEquals('x', b.marks[4])
        b = b.applyKey(4, act(Key.K)).board
        assertEquals('o', b.marks[4])
        b = b.applyKey(4, act(Key.K)).board
        assertEquals('.', b.marks[4])
        val seen = (1..4).scan(board()) { s, _ -> s.applyKey(2, act(Key.Spacebar)).board }.map { it.marks[2] }
        assertEquals(listOf('.', 'x', 'o', '.', 'x'), seen)
        val removed = board().applyKey(5, act(Key.X)).board
        assertEquals('.', removed.applyKey(5, act(Key.Delete)).board.marks[5])
        assertSame(b, b.applyKey(5, act(Key.Backspace)).board)
        assertEquals("one key, one move", 1, removed.moves)
    }

    @Test
    fun shiftArrowCarriesAMarkOverUntouchedSquaresOnly() {
        var b = board("x" + ".".repeat(8))
        var at = 0
        repeat(2) {
            val r = b.applyKey(at, act(Key.DirectionRight, shift = true))
            b = r.board
            at = r.cursor!!
        }
        assertEquals("xxx", b.marks.take(3))
        val blocked = board("xo" + ".".repeat(7)).applyKey(0, act(Key.DirectionRight, true))
        assertEquals("xo.", blocked.board.marks.take(3))
        val blank = board()
        assertSame(blank, blank.applyKey(0, act(Key.DirectionRight, true)).board)
    }
}
