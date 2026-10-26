package com.joebywan.daybook

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.puzzles.WhosWho
import com.joebywan.daybook.puzzles.WhosWhoKeyAction
import com.joebywan.daybook.puzzles.WhosWhoLogic
import com.joebywan.daybook.puzzles.applyKey
import com.joebywan.daybook.puzzles.whosWhoKeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class WhosWhoKeysTest {

    private val s = WhosWho.tutorialBoard()

    @Test
    fun `keys map to moves, ticks, crosses and clearing`() {
        assertEquals(WhosWhoKeyAction.Move(0, 1), whosWhoKeyAction(Key.DirectionRight, false))
        assertEquals(WhosWhoKeyAction.Mark(1, tick = true), whosWhoKeyAction(Key.Two, false))
        assertEquals(WhosWhoKeyAction.Mark(1, tick = false), whosWhoKeyAction(Key.Two, true))
        assertEquals(WhosWhoKeyAction.Mark(4, tick = true), whosWhoKeyAction(Key.NumPad5, false))
        assertEquals(WhosWhoKeyAction.Clear, whosWhoKeyAction(Key.Backspace, false))
        assertNull(whosWhoKeyAction(Key.Six, false))
        assertNull(whosWhoKeyAction(Key.Backspace, true))
        assertNull(whosWhoKeyAction(Key.DirectionUp, true))
    }

    @Test
    fun `a key does what the pad does, one state each, and the selection walks the table`() {
        val first = s.applyKey(null, WhosWhoKeyAction.Move(1, 0))
        assertEquals(0, first.cursor)
        assertSame(s, s.applyKey(null, WhosWhoKeyAction.Mark(0, true)).board)
        val right = s.applyKey(0, WhosWhoKeyAction.Move(0, 1))
        assertEquals(1, right.cursor)
        assertEquals(1, s.applyKey(1, WhosWhoKeyAction.Move(0, 1)).cursor) // clamped at the edge
        assertEquals(3, s.applyKey(1, WhosWhoKeyAction.Move(1, 0)).cursor) // Ben's pet
        val tick = s.applyKey(3, WhosWhoKeyAction.Mark(2, true)).board
        assertEquals(s.mark(1, 1, 2, tick = true), tick)
        val cross = s.applyKey(3, WhosWhoKeyAction.Mark(0, false)).board
        assertEquals(s.mark(1, 1, 0, tick = false), cross)
        assertEquals(s.marks, tick.applyKey(3, WhosWhoKeyAction.Clear).board.marks)
        assertSame(s, s.applyKey(3, WhosWhoKeyAction.Clear).board)
        assertSame("a digit past the board's size", s, s.applyKey(3, WhosWhoKeyAction.Mark(4, true)).board)
        assertEquals(WhosWhoLogic.TICK, tick.marks[tick.markIndex(1, 1, 2)])
    }
}
