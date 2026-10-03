package com.joebywan.daybook

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.puzzles.Inequality
import com.joebywan.daybook.puzzles.InequalityKeyAction
import com.joebywan.daybook.puzzles.applyKey
import com.joebywan.daybook.puzzles.inequalityKeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InequalityKeysTest {
    private val board = Inequality.tutorialBoard() // 4x4; squares 0, 1, 4, 5, 14, 15 are open

    @Test
    fun `keys map, and digits past the size are not used`() {
        assertEquals(InequalityKeyAction.Digit(3), inequalityKeyAction(Key.Three, 4))
        assertEquals(InequalityKeyAction.Digit(3), inequalityKeyAction(Key.NumPad3, 4))
        assertNull(inequalityKeyAction(Key.Five, 4))
        assertEquals(InequalityKeyAction.Digit(6), inequalityKeyAction(Key.Six, 6))
        assertEquals(InequalityKeyAction.Clear, inequalityKeyAction(Key.Backspace, 4))
        assertEquals(InequalityKeyAction.Move(0, 1), inequalityKeyAction(Key.DirectionRight, 4))
        assertNull(inequalityKeyAction(Key.Q, 4))
    }

    @Test
    fun `an arrow selects and moves without wrapping, a digit places and a repeat clears`() {
        val first = board.applyKey(InequalityKeyAction.Move(1, 0))!!
        assertEquals(0, first.selected)
        assertNull(first.applyKey(InequalityKeyAction.Move(-1, 0))) // clamped at the edge: nothing changes
        assertEquals(1, first.applyKey(InequalityKeyAction.Move(0, 1))!!.selected)
        val placed = first.applyKey(InequalityKeyAction.Digit(1))!!
        assertEquals(1, placed.cells[0])
        assertEquals(1, placed.moves)
        assertEquals(0, placed.applyKey(InequalityKeyAction.Digit(1))!!.cells[0])
        assertEquals(0, placed.applyKey(InequalityKeyAction.Clear)!!.cells[0])
    }

    @Test
    fun `with nothing selected a digit does nothing, and a given cannot be changed`() {
        assertNull(board.applyKey(InequalityKeyAction.Digit(1)))
        assertNull(board.select(2).applyKey(InequalityKeyAction.Digit(3)))
        assertNull(board.select(2).applyKey(InequalityKeyAction.Clear))
    }
}
