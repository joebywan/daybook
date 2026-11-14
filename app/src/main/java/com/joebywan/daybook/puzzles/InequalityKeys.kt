package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.DigitKeyAction
import com.joebywan.daybook.core.applyDigitKey

/**
 * What pressing [digit] does to [cell]: in notes mode toggles its pencil mark, else places it, or clears the square if it
 * already holds it. `this` if nothing changes.
 */
fun InequalityState.enter(cell: Int, digit: Int, notesMode: Boolean = false): InequalityState =
    if (notesMode) toggleNote(cell, digit) else withCell(cell, if (cells[cell] == digit) 0 else digit)

/** Empties [cell] and its notes, as Backspace does; `this` if there was nothing to empty. */
fun InequalityState.clearCell(cell: Int): InequalityState = when {
    givens[cell] -> this
    cells[cell] != 0 -> withCell(cell, 0)
    notes[cell] != 0 -> copy(notes = notes.toMutableList().also { it[cell] = 0 }, moves = moves + 1)
    else -> this
}

/** The board after [action] (from `digitKeyAction(key, size)`), or null when it changes nothing; see [applyDigitKey]. */
fun InequalityState.applyKey(action: DigitKeyAction, notesMode: Boolean = false): InequalityState? =
    applyDigitKey(action, selected, size, { select(it) }, { cell, d -> enter(cell, d, notesMode) }, { clearCell(it) })
