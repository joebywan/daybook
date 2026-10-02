package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key

/** What a physical key asks of a Sudoku board. No state, no focus: that is [sudokuKeyAction]'s whole job. */
sealed interface SudokuKeyAction {
    /** 1-9: the digit pad's key of that number. */
    data class Digit(val digit: Int) : SudokuKeyAction

    /** Backspace, Delete or 0: empty the selected cell. */
    data object Clear : SudokuKeyAction

    /** An arrow: move the selection one square, never wrapping. */
    data class Move(val dRow: Int, val dCol: Int) : SudokuKeyAction
}

/** The action for [key], or null for a key the board does not use. The caller has already ruled out chords. */
fun sudokuKeyAction(key: Key): SudokuKeyAction? = when (key) {
    Key.One, Key.NumPad1 -> SudokuKeyAction.Digit(1)
    Key.Two, Key.NumPad2 -> SudokuKeyAction.Digit(2)
    Key.Three, Key.NumPad3 -> SudokuKeyAction.Digit(3)
    Key.Four, Key.NumPad4 -> SudokuKeyAction.Digit(4)
    Key.Five, Key.NumPad5 -> SudokuKeyAction.Digit(5)
    Key.Six, Key.NumPad6 -> SudokuKeyAction.Digit(6)
    Key.Seven, Key.NumPad7 -> SudokuKeyAction.Digit(7)
    Key.Eight, Key.NumPad8 -> SudokuKeyAction.Digit(8)
    Key.Nine, Key.NumPad9 -> SudokuKeyAction.Digit(9)
    Key.Zero, Key.NumPad0, Key.Backspace, Key.Delete -> SudokuKeyAction.Clear
    Key.DirectionUp -> SudokuKeyAction.Move(-1, 0)
    Key.DirectionDown -> SudokuKeyAction.Move(1, 0)
    Key.DirectionLeft -> SudokuKeyAction.Move(0, -1)
    Key.DirectionRight -> SudokuKeyAction.Move(0, 1)
    else -> null
}

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

/** Empties [cell] and its marks, as the clear key does; `this` if there was nothing to empty. */
fun SudokuState.clearCell(cell: Int): SudokuState =
    if (givens[cell] || (cells[cell] == 0 && notes[cell] == 0)) this else withCell(cell, 0)

/**
 * The board after [action], or null when it changes nothing (no cell selected, a given, an edge).
 * One state per key, the same ones a tap emits, so undo, notes, conflicts and peer-striking behave alike.
 * With nothing selected an arrow selects the first square.
 */
fun SudokuState.applyKey(action: SudokuKeyAction, notesMode: Boolean): SudokuState? {
    val at = selected
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
