package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.arrowStep

/** What a physical key asks of an Inequality board. No state, no focus. */
sealed interface InequalityKeyAction {
    /** 1..size: the digit pad's key of that number. */
    data class Digit(val digit: Int) : InequalityKeyAction

    /** Backspace, Delete or 0: empty the selected square. */
    data object Clear : InequalityKeyAction

    /** An arrow: move the selection one square, never wrapping. */
    data class Move(val dRow: Int, val dCol: Int) : InequalityKeyAction
}

private val DIGIT_KEYS = listOf(
    Key.One to Key.NumPad1, Key.Two to Key.NumPad2, Key.Three to Key.NumPad3,
    Key.Four to Key.NumPad4, Key.Five to Key.NumPad5, Key.Six to Key.NumPad6,
)

/** The action for [key] on a [size] board, or null for a key it does not use. The caller has ruled out chords. */
fun inequalityKeyAction(key: Key, size: Int): InequalityKeyAction? {
    arrowStep(key)?.let { (r, c) -> return InequalityKeyAction.Move(r, c) }
    if (key == Key.Zero || key == Key.NumPad0 || key == Key.Backspace || key == Key.Delete) return InequalityKeyAction.Clear
    val digit = DIGIT_KEYS.indexOfFirst { key == it.first || key == it.second } + 1
    return if (digit in 1..size) InequalityKeyAction.Digit(digit) else null
}

/** What pressing [digit] does to [cell]: places it, or clears the square if it already holds it. `this` if nothing changes. */
fun InequalityState.enter(cell: Int, digit: Int): InequalityState =
    withCell(cell, if (cells[cell] == digit) 0 else digit)

/**
 * The board after [action], or null when it changes nothing. One state per key, the same ones a tap
 * emits. With nothing selected an arrow selects the first square.
 */
fun InequalityState.applyKey(action: InequalityKeyAction): InequalityState? {
    val at = selected
    val next = when (action) {
        is InequalityKeyAction.Move -> {
            if (at == null) return select(0)
            select((at / size + action.dRow).coerceIn(0, size - 1) * size + (at % size + action.dCol).coerceIn(0, size - 1))
        }
        is InequalityKeyAction.Digit -> if (at == null) this else enter(at, action.digit)
        InequalityKeyAction.Clear -> if (at == null) this else withCell(at, 0)
    }
    return if (next == this) null else next
}
