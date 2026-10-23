package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.arrowStep
import com.joebywan.daybook.core.stepCursor

/** What a physical key asks of a Regions board. No state, no focus. */
sealed interface RegionsKeyAction {
    /** 1..digits: the digit pad's key of that number. */
    data class Digit(val digit: Int) : RegionsKeyAction

    /** Backspace, Delete or 0: empty the selected square. */
    data object Clear : RegionsKeyAction

    /** An arrow: move the selection one square, never wrapping. */
    data class Move(val dRow: Int, val dCol: Int) : RegionsKeyAction
}

private val DIGIT_KEYS = listOf(
    Key.One to Key.NumPad1, Key.Two to Key.NumPad2, Key.Three to Key.NumPad3,
    Key.Four to Key.NumPad4, Key.Five to Key.NumPad5,
)

/** The action for [key] on a board whose pad goes up to [digits], or null for a key it does not use. The caller has ruled out chords. */
fun regionsKeyAction(key: Key, digits: Int): RegionsKeyAction? {
    arrowStep(key)?.let { (r, c) -> return RegionsKeyAction.Move(r, c) }
    if (key == Key.Zero || key == Key.NumPad0 || key == Key.Backspace || key == Key.Delete) return RegionsKeyAction.Clear
    val digit = DIGIT_KEYS.indexOfFirst { key == it.first || key == it.second } + 1
    return if (digit in 1..digits) RegionsKeyAction.Digit(digit) else null
}

/** What pressing [digit] does to [cell]: places it, or clears the square if it already holds it. `this` if nothing changes. */
fun RegionsState.enter(cell: Int, digit: Int): RegionsState =
    withCell(cell, if (cells[cell] == digit) 0 else digit)

/**
 * The board after [action], or null when it changes nothing. One state per key, the same ones a tap
 * emits. With nothing selected an arrow selects the first square.
 */
fun RegionsState.applyKey(action: RegionsKeyAction): RegionsState? {
    val at = selected
    val next = when (action) {
        is RegionsKeyAction.Move -> {
            if (at == null) return select(0)
            select(stepCursor(at, action.dRow, action.dCol, size, size))
        }
        is RegionsKeyAction.Digit -> if (at == null) this else enter(at, action.digit)
        RegionsKeyAction.Clear -> if (at == null) this else withCell(at, 0)
    }
    return if (next == this) null else next
}
