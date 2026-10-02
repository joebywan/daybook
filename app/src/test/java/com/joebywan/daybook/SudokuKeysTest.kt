package com.joebywan.daybook

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Sudoku
import com.joebywan.daybook.puzzles.SudokuKeyAction
import com.joebywan.daybook.puzzles.SudokuState
import com.joebywan.daybook.puzzles.applyKey
import com.joebywan.daybook.puzzles.sudokuKeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuKeysTest {
    private val board = Sudoku.generate(7L, Difficulty.HARD) as SudokuState
    private val open = board.cells.indexOf(0)
    private val given = board.givens.indexOf(true)

    @Test
    fun keysMapToActions() {
        val digits = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
        val pad = listOf(Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4, Key.NumPad5, Key.NumPad6, Key.NumPad7, Key.NumPad8, Key.NumPad9)
        for (d in 1..9) {
            assertEquals(SudokuKeyAction.Digit(d), sudokuKeyAction(digits[d - 1]))
            assertEquals(SudokuKeyAction.Digit(d), sudokuKeyAction(pad[d - 1]))
        }
        for (k in listOf(Key.Zero, Key.NumPad0, Key.Backspace, Key.Delete)) {
            assertEquals(SudokuKeyAction.Clear, sudokuKeyAction(k))
        }
        assertEquals(SudokuKeyAction.Move(-1, 0), sudokuKeyAction(Key.DirectionUp))
        assertEquals(SudokuKeyAction.Move(1, 0), sudokuKeyAction(Key.DirectionDown))
        assertEquals(SudokuKeyAction.Move(0, -1), sudokuKeyAction(Key.DirectionLeft))
        assertEquals(SudokuKeyAction.Move(0, 1), sudokuKeyAction(Key.DirectionRight))
        for (k in listOf(Key.A, Key.Enter, Key.Tab, Key.Spacebar, Key.Escape, Key.R, Key.N)) assertNull(sudokuKeyAction(k))
    }

    @Test
    fun digitKeyActsLikeThePad() {
        val s = board.select(open)
        val placed = s.applyKey(SudokuKeyAction.Digit(5), notesMode = false)!!
        assertEquals(s.withCell(open, 5), placed)
        // Same digit again clears, as a second tap on the pad does.
        assertEquals(s.withCell(open, 5).withCell(open, 0).cells, placed.applyKey(SudokuKeyAction.Digit(5), false)!!.cells)
    }

    @Test
    fun digitKeyInNotesModeTogglesANote() {
        val s = board.select(open)
        val noted = s.applyKey(SudokuKeyAction.Digit(3), notesMode = true)!!
        assertEquals(s.toggleNote(open, 3), noted)
        assertTrue(noted.hasNote(open, 3))
        assertEquals(0, noted.cells[open])
        assertEquals(s.cells, noted.applyKey(SudokuKeyAction.Digit(3), true)!!.cells)
        assertTrue(!noted.applyKey(SudokuKeyAction.Digit(3), true)!!.hasNote(open, 3))
    }

    @Test
    fun placingStrikesPeerNotesInTheSameState() {
        val peer = Sudoku.peers(open).first { board.cells[it] == 0 }
        val withNote = board.toggleNote(peer, 4).select(open)
        val next = withNote.applyKey(SudokuKeyAction.Digit(4), false)!!
        assertTrue(!next.hasNote(peer, 4))
    }

    @Test
    fun givenCellsRefuseEverythingButSelection() {
        val s = board.select(given)
        assertNull(s.applyKey(SudokuKeyAction.Digit(1), false))
        assertNull(s.applyKey(SudokuKeyAction.Digit(1), true))
        assertNull(s.applyKey(SudokuKeyAction.Clear, false))
    }

    @Test
    fun clearEmptiesDigitsAndNotesAndIsNoOpOnEmpty() {
        val s = board.select(open)
        assertNull(s.applyKey(SudokuKeyAction.Clear, false))
        val noted = s.toggleNote(open, 2)
        val cleared = noted.applyKey(SudokuKeyAction.Clear, false)!!
        assertEquals(0, cleared.visibleNotes(open))
        val filled = s.withCell(open, 6)
        assertEquals(0, filled.applyKey(SudokuKeyAction.Clear, false)!!.cells[open])
    }

    @Test
    fun nothingSelectedDigitsDoNothingAndArrowsSelectTheFirstSquare() {
        assertNull(board.selected)
        assertNull(board.applyKey(SudokuKeyAction.Digit(1), false))
        assertNull(board.applyKey(SudokuKeyAction.Clear, false))
        for (m in listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)) {
            assertEquals(0, board.applyKey(sudokuKeyAction(m)!!, false)!!.selected)
        }
    }

    @Test
    fun arrowsMoveOneAndStopAtTheEdge() {
        val centre = board.select(40)
        assertEquals(31, centre.applyKey(SudokuKeyAction.Move(-1, 0), false)!!.selected)
        assertEquals(49, centre.applyKey(SudokuKeyAction.Move(1, 0), false)!!.selected)
        assertEquals(39, centre.applyKey(SudokuKeyAction.Move(0, -1), false)!!.selected)
        assertEquals(41, centre.applyKey(SudokuKeyAction.Move(0, 1), false)!!.selected)
        assertNull(board.select(0).applyKey(SudokuKeyAction.Move(-1, 0), false))
        assertNull(board.select(0).applyKey(SudokuKeyAction.Move(0, -1), false))
        assertNull(board.select(80).applyKey(SudokuKeyAction.Move(1, 0), false))
        assertNull(board.select(80).applyKey(SudokuKeyAction.Move(0, 1), false))
        // A row's last square does not wrap into the next row.
        assertNull(board.select(8).applyKey(SudokuKeyAction.Move(0, 1), false))
        assertNotNull(board.select(8).applyKey(SudokuKeyAction.Move(1, 0), false))
    }
}
