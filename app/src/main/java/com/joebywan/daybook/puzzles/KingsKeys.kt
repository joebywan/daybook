package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key

/** What a physical key asks of the Kings square under the cursor (arrows are the board's). */
enum class KingsKeyAction {
    /** Space or X: pencil the square out, or take the pencil back. */
    Pencil,

    /** K or Enter: crown the square, or take the crown back. */
    Crown,

    /** Backspace or Delete: empty the square, whatever is on it. */
    Clear,
}

fun kingsKeyAction(key: Key): KingsKeyAction? = when (key) {
    Key.Spacebar, Key.X -> KingsKeyAction.Pencil
    Key.K, Key.Enter, Key.NumPadEnter -> KingsKeyAction.Crown
    Key.Backspace, Key.Delete -> KingsKeyAction.Clear
    else -> null
}

/**
 * The board after [action] on [cell], or null when it changes nothing. These are the functions a tap
 * and a double tap end in, called directly: the keyboard has no double-tap window to wait out.
 */
fun KingsState.applyKey(cell: Int, action: KingsKeyAction): KingsState? {
    val next = when (action) {
        KingsKeyAction.Pencil -> toggleMark(cell)
        KingsKeyAction.Crown -> toggleKing(cell)
        KingsKeyAction.Clear -> when (marks[cell]) {
            Mark.EMPTY -> this
            Mark.BLOCKED -> toggleMark(cell)
            Mark.KING -> toggleKing(cell)
        }
    }
    return if (next === this) null else next
}
