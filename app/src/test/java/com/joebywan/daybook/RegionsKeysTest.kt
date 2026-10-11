package com.joebywan.daybook

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.puzzles.Regions
import com.joebywan.daybook.puzzles.RegionsKeyAction
import com.joebywan.daybook.puzzles.applyKey
import com.joebywan.daybook.puzzles.regionsKeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegionsKeysTest {
    private val board = Regions.tutorialBoard() // 4x4; squares 2, 4, 10, 14 are given

    @Test
    fun `keys map, and digits past the pad are not used`() {
        assertEquals(RegionsKeyAction.Digit(3), regionsKeyAction(Key.Three, 4))
        assertEquals(RegionsKeyAction.Digit(3), regionsKeyAction(Key.NumPad3, 4))
        assertNull(regionsKeyAction(Key.Five, 4))
        assertEquals(RegionsKeyAction.Digit(5), regionsKeyAction(Key.Five, 5))
        assertNull(regionsKeyAction(Key.Six, 5))
        assertEquals(RegionsKeyAction.Clear, regionsKeyAction(Key.Backspace, 4))
        assertEquals(RegionsKeyAction.Move(0, 1), regionsKeyAction(Key.DirectionRight, 4))
        assertNull(regionsKeyAction(Key.Q, 4))
    }

    @Test
    fun `an arrow selects and moves without wrapping, a digit places and a repeat clears`() {
        val first = board.applyKey(RegionsKeyAction.Move(1, 0))!!
        assertEquals(0, first.selected)
        assertNull(first.applyKey(RegionsKeyAction.Move(-1, 0))) // clamped at the edge: nothing changes
        assertEquals(1, first.applyKey(RegionsKeyAction.Move(0, 1))!!.selected)
        val placed = first.applyKey(RegionsKeyAction.Digit(1))!!
        assertEquals(1, placed.cells[0])
        assertEquals(1, placed.moves)
        assertEquals(0, placed.applyKey(RegionsKeyAction.Digit(1))!!.cells[0])
        assertEquals(0, placed.applyKey(RegionsKeyAction.Clear)!!.cells[0])
    }

    @Test
    fun `with nothing selected a digit does nothing, and a given cannot be changed`() {
        assertNull(board.applyKey(RegionsKeyAction.Digit(1)))
        assertNull(board.select(2).applyKey(RegionsKeyAction.Digit(1)))
        assertNull(board.select(2).applyKey(RegionsKeyAction.Clear))
    }
}
