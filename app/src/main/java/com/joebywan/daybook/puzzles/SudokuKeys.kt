package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.DigitKeyAction
import com.joebywan.daybook.core.applyDigitKey

/**
 * What pressing [digit] does to [cell]: the one rule the pad and the keyboard share. In notes mode it
 * toggles a pencil mark (nothing on a filled or given square); otherwise it places the digit, or clears
 * the cell if it already holds it. Returns `this` when nothing would change, so a caller does not push
 * an undo entry for a refusal.
 */
fun SudokuState.enter(cell: Int, digit: Int, notesMode: Boolean): SudokuState = when {
    notesMode -> toggleNote(cell, digit)
    else -> withCell(cell, if (cells[cell] == digit) 0 else digit)
}

internal enum class SquareTap { ENTER, DESELECT, SELECT }

/** What a tap on [cell] does: an armed digit enters only with no square selected, and the selected square again lets go, the way into digit-first play. */
internal fun squareTap(cell: Int, selected: Int?, armed: Int?): SquareTap = when {
    armed != null && selected == null -> SquareTap.ENTER
    selected == cell -> SquareTap.DESELECT
    else -> SquareTap.SELECT
}

/** Empties [cell] and its marks, as the clear key does; `this` if there was nothing to empty. */
fun SudokuState.clearCell(cell: Int): SudokuState =
    if (givens[cell] || (cells[cell] == 0 && notes[cell] == 0)) this else withCell(cell, 0)

/**
 * The board after [action] (from `digitKeyAction(key, 9)`), or null when it changes nothing (no cell
 * selected, a given, an edge). One state per key, the same ones a tap emits, so undo, notes, conflicts
 * and peer-striking behave alike. With nothing selected an arrow selects the first square.
 */
fun SudokuState.applyKey(action: DigitKeyAction, notesMode: Boolean): SudokuState? =
    applyDigitKey(action, selected, 9, { select(it) }, { cell, d -> enter(cell, d, notesMode) }, { clearCell(it) })
