package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.arrowStep
import com.joebywan.daybook.core.stepCursor

/** What a physical key asks of a Who's Who board. */
sealed interface WhosWhoKeyAction {
    /** An arrow: move the selection one square. */
    data class Move(val dRow: Int, val dCol: Int) : WhosWhoKeyAction

    /** 1-5: tick item [slot] in the selected square ([tick]), or with Shift cross it; again takes the mark off. */
    data class Mark(val slot: Int, val tick: Boolean) : WhosWhoKeyAction

    /** Backspace or Delete: every mark in the square off. */
    data object Clear : WhosWhoKeyAction
}

private val DIGITS = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five)
private val PAD_DIGITS = listOf(Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4, Key.NumPad5)

/** The action for [key], or null for a key the board does not use. The caller has already ruled out chords. */
fun whosWhoKeyAction(key: Key, shift: Boolean): WhosWhoKeyAction? {
    if (!shift) arrowStep(key)?.let { (r, c) -> return WhosWhoKeyAction.Move(r, c) }
    val digit = DIGITS.indexOf(key).takeIf { it >= 0 } ?: PAD_DIGITS.indexOf(key).takeIf { it >= 0 }
    if (digit != null) return WhosWhoKeyAction.Mark(digit, tick = !shift)
    if (shift) return null
    return when (key) {
        Key.Backspace, Key.Delete -> WhosWhoKeyAction.Clear
        else -> null
    }
}

/** The selection after a key and the board it leaves; [board] is the same instance when nothing changed. */
data class WhosWhoKeyResult(val cursor: Int?, val board: WhosWhoState)

/**
 * [action] applied with [cursor] selected (a table cell, `person * columns + column`; null: none yet, so an arrow
 * selects the first square and other keys do nothing). Marks go through the same [WhosWhoState.mark] a tap on the pad
 * uses, one state per key, so undo cannot differ. A digit past the board's size does nothing.
 */
fun WhosWhoState.applyKey(cursor: Int?, action: WhosWhoKeyAction): WhosWhoKeyResult {
    if (cursor == null) return WhosWhoKeyResult(if (action is WhosWhoKeyAction.Move) 0 else null, this)
    val person = cursor / columns
    val column = cursor % columns
    return when (action) {
        is WhosWhoKeyAction.Move -> WhosWhoKeyResult(stepCursor(cursor, action.dRow, action.dCol, n, columns), this)
        is WhosWhoKeyAction.Mark ->
            WhosWhoKeyResult(cursor, if (action.slot < n) mark(person, column, action.slot, action.tick) else this)
        WhosWhoKeyAction.Clear -> WhosWhoKeyResult(cursor, clearCell(person, column))
    }
}
