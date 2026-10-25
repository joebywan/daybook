package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.CageLogic
import com.joebywan.daybook.puzzles.CageState
import com.joebywan.daybook.puzzles.Cages
import com.joebywan.daybook.core.DigitKeyAction
import com.joebywan.daybook.puzzles.applyKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cage Sudoku answers the keys Sudoku does, through its own state functions (the cage's marks are struck too). */
class CageKeysTest {
    private val board = Cages.generate(7L, Difficulty.HARD) as CageState
    private val open = board.cells.indexOf(0)
    private val given = board.grid.givens.indexOf(true)

    @Test
    fun `a digit key acts like the pad`() {
        val s = board.select(open)
        val placed = s.applyKey(DigitKeyAction.Digit(5), notesMode = false)!!
        assertEquals(s.withCell(open, 5), placed)
        // The same digit again clears, as a second tap on the pad does.
        assertEquals(s.cells, placed.applyKey(DigitKeyAction.Digit(5), false)!!.cells)
    }

    @Test
    fun `a digit key in notes mode toggles a note`() {
        val s = board.select(open)
        val noted = s.applyKey(DigitKeyAction.Digit(3), notesMode = true)!!
        assertTrue(noted.grid.hasNote(open, 3))
        assertEquals(0, noted.cells[open])
        assertFalse(noted.applyKey(DigitKeyAction.Digit(3), true)!!.grid.hasNote(open, 3))
    }

    @Test
    fun `placing a digit strikes the notes of its cage-mates in the same state`() {
        val cage = CageLogic.cageCells(board.cageOf).first { c -> c.size >= 2 && c.all { board.cells[it] == 0 } }
        val s = board.toggleNote(cage[1], 4).select(cage[0])
        val next = s.applyKey(DigitKeyAction.Digit(4), false)!!
        assertFalse(next.grid.hasNote(cage[1], 4))
        assertEquals(s.moves + 1, next.moves)
    }

    @Test
    fun `givens refuse everything but selection`() {
        val s = board.select(given)
        assertNull(s.applyKey(DigitKeyAction.Digit(1), false))
        assertNull(s.applyKey(DigitKeyAction.Digit(1), true))
        assertNull(s.applyKey(DigitKeyAction.Clear, false))
    }

    @Test
    fun `clear empties digits and notes and does nothing to an empty square`() {
        val s = board.select(open)
        assertNull(s.applyKey(DigitKeyAction.Clear, false))
        assertEquals(0, s.toggleNote(open, 2).applyKey(DigitKeyAction.Clear, false)!!.grid.notes[open])
        assertEquals(0, s.withCell(open, 6).applyKey(DigitKeyAction.Clear, false)!!.cells[open])
    }

    @Test
    fun `arrows move the selection, never wrapping, and start at the first square`() {
        assertEquals(0, board.applyKey(DigitKeyAction.Move(1, 0), false)!!.grid.selected)
        val corner = board.select(0)
        assertNull(corner.applyKey(DigitKeyAction.Move(-1, 0), false))
        assertNull(corner.applyKey(DigitKeyAction.Move(0, -1), false))
        assertEquals(9, corner.applyKey(DigitKeyAction.Move(1, 0), false)!!.grid.selected)
        assertEquals(1, corner.applyKey(DigitKeyAction.Move(0, 1), false)!!.grid.selected)
    }

    @Test
    fun `selection alone is not a move`() {
        assertEquals(0, board.select(open).moves)
        assertEquals(board, Cages.withoutSelection(board.select(open)))
    }
}
