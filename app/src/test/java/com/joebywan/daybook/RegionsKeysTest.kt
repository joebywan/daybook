package com.joebywan.daybook

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.puzzles.Regions
import com.joebywan.daybook.core.DigitKeyAction
import com.joebywan.daybook.puzzles.applyKey
import com.joebywan.daybook.core.digitKeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegionsKeysTest {
    private val board = Regions.tutorialBoard() // 4x4; squares 2, 4, 10, 14 are given

    @Test
    fun `keys map, and digits past the pad are not used`() {
        assertEquals(DigitKeyAction.Digit(3), digitKeyAction(Key.Three, 4))
        assertEquals(DigitKeyAction.Digit(3), digitKeyAction(Key.NumPad3, 4))
        assertNull(digitKeyAction(Key.Five, 4))
        assertEquals(DigitKeyAction.Digit(5), digitKeyAction(Key.Five, 5))
        assertNull(digitKeyAction(Key.Six, 5))
        assertEquals(DigitKeyAction.Clear, digitKeyAction(Key.Backspace, 4))
        assertEquals(DigitKeyAction.Move(0, 1), digitKeyAction(Key.DirectionRight, 4))
        assertNull(digitKeyAction(Key.Q, 4))
    }

    @Test
    fun `an arrow selects and moves without wrapping, a digit places and a repeat clears`() {
        val first = board.applyKey(DigitKeyAction.Move(1, 0))!!
        assertEquals(0, first.selected)
        assertNull(first.applyKey(DigitKeyAction.Move(-1, 0))) // clamped at the edge: nothing changes
        assertEquals(1, first.applyKey(DigitKeyAction.Move(0, 1))!!.selected)
        val placed = first.applyKey(DigitKeyAction.Digit(1))!!
        assertEquals(1, placed.cells[0])
        assertEquals(1, placed.moves)
        assertEquals(0, placed.applyKey(DigitKeyAction.Digit(1))!!.cells[0])
        assertEquals(0, placed.applyKey(DigitKeyAction.Clear)!!.cells[0])
    }

    @Test
    fun `with nothing selected a digit does nothing, and a given cannot be changed`() {
        assertNull(board.applyKey(DigitKeyAction.Digit(1)))
        assertNull(board.select(2).applyKey(DigitKeyAction.Digit(1)))
        assertNull(board.select(2).applyKey(DigitKeyAction.Clear))
    }
}
