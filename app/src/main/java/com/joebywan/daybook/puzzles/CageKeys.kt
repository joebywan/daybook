package com.joebywan.daybook.puzzles

/**
 * Cage Sudoku takes the keys Sudoku does ([sudokuKeyAction]); this applies them to a [CageState] through the
 * same state functions a tap uses, so notes, undo, conflicts and the striking of marks cannot differ.
 */

/** Empties [cell] and its marks, as the clear key does; `this` if there was nothing to empty. */
fun CageState.clearCell(cell: Int): CageState =
    if (grid.givens[cell] || (grid.cells[cell] == 0 && grid.notes[cell] == 0)) this else withCell(cell, 0)

/**
 * The board after [action], or null when it changes nothing (no cell selected, a given, an edge). One state
 * per key. With nothing selected an arrow selects the first square.
 */
fun CageState.applyKey(action: SudokuKeyAction, notesMode: Boolean): CageState? {
    val at = grid.selected
    val next = when (action) {
        is SudokuKeyAction.Move -> {
            if (at == null) return select(0)
            val row = (at / 9 + action.dRow).coerceIn(0, 8)
            val col = (at % 9 + action.dCol).coerceIn(0, 8)
            select(row * 9 + col)
        }
        is SudokuKeyAction.Digit -> if (at == null) this else enter(at, action.digit, notesMode)
        SudokuKeyAction.Clear -> if (at == null) this else clearCell(at)
    }
    return if (next == this) null else next
}
