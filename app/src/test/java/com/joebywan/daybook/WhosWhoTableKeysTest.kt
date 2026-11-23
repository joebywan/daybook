package com.joebywan.daybook

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.puzzles.WhosWhoTable
import com.joebywan.daybook.puzzles.TableKeyAction
import com.joebywan.daybook.puzzles.WhosWhoLogic
import com.joebywan.daybook.puzzles.applyTableKey
import com.joebywan.daybook.puzzles.tableKeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class WhosWhoTableKeysTest {

    private val s = WhosWhoTable.tutorialBoard()

    @Test
    fun `keys map to moves, ticks, crosses and clearing`() {
        assertEquals(TableKeyAction.Move(0, 1), tableKeyAction(Key.DirectionRight, false))
        assertEquals(TableKeyAction.Mark(1, tick = true), tableKeyAction(Key.Two, false))
        assertEquals(TableKeyAction.Mark(1, tick = false), tableKeyAction(Key.Two, true))
        assertEquals(TableKeyAction.Mark(4, tick = true), tableKeyAction(Key.NumPad5, false))
        assertEquals(TableKeyAction.Clear, tableKeyAction(Key.Backspace, false))
        assertNull(tableKeyAction(Key.Six, false))
        assertNull(tableKeyAction(Key.Backspace, true))
        assertNull(tableKeyAction(Key.DirectionUp, true))
    }

    @Test
    fun `a key does what the pad does, one state each, and the selection walks the table`() {
        val first = s.applyTableKey(null, TableKeyAction.Move(1, 0))
        assertEquals(0, first.cursor)
        assertSame(s, s.applyTableKey(null, TableKeyAction.Mark(0, true)).board)
        val right = s.applyTableKey(0, TableKeyAction.Move(0, 1))
        assertEquals(1, right.cursor)
        assertEquals(1, s.applyTableKey(1, TableKeyAction.Move(0, 1)).cursor) // clamped at the edge
        assertEquals(3, s.applyTableKey(1, TableKeyAction.Move(1, 0)).cursor) // Ben's pet
        val tick = s.applyTableKey(3, TableKeyAction.Mark(2, true)).board
        assertEquals(s.mark(1, 1, 2, tick = true), tick)
        val cross = s.applyTableKey(3, TableKeyAction.Mark(0, false)).board
        assertEquals(s.mark(1, 1, 0, tick = false), cross)
        assertEquals(s.marks, tick.applyTableKey(3, TableKeyAction.Clear).board.marks)
        assertSame(s, s.applyTableKey(3, TableKeyAction.Clear).board)
        assertSame("a digit past the board's size", s, s.applyTableKey(3, TableKeyAction.Mark(4, true)).board)
        assertEquals(WhosWhoLogic.TICK, tick.marks[tick.markIndex(1, 1, 2)])
    }
}
