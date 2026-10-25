package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.DigitKeyAction
import com.joebywan.daybook.core.applyDigitKey

/**
 * Cage Sudoku takes the keys Sudoku does (`digitKeyAction(key, 9)`); this applies them to a [CageState] through the
 * same state functions a tap uses, so notes, undo, conflicts and the striking of marks cannot differ.
 */

/** Empties [cell] and its marks, as the clear key does; `this` if there was nothing to empty. */
fun CageState.clearCell(cell: Int): CageState =
    if (grid.givens[cell] || (grid.cells[cell] == 0 && grid.notes[cell] == 0)) this else withCell(cell, 0)

/** The board after [action], or null when it changes nothing (no cell selected, a given, an edge); see [applyDigitKey]. */
fun CageState.applyKey(action: DigitKeyAction, notesMode: Boolean): CageState? =
    applyDigitKey(action, grid.selected, 9, { select(it) }, { cell, d -> enter(cell, d, notesMode) }, { clearCell(it) })
