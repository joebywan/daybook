package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.arrowStep
import com.joebywan.daybook.core.stepCursor

/** What a physical key asks of a Nonogram board. */
sealed interface NonogramKeyAction {
    /** An arrow: move the cursor one square. With [lay], Shift was held: carry the square's mark along. */
    data class Move(val dRow: Int, val dCol: Int, val lay: Boolean) : NonogramKeyAction

    /** F or X: that pen's mark on the cursor square, or a clean square if it already has it (a tap). */
    data class Pen(val mark: Char) : NonogramKeyAction

    /** Space: clean, then fill, then cross, then clean again. */
    data object Cycle : NonogramKeyAction

    /** Backspace or Delete. */
    data object Clear : NonogramKeyAction
}

/** The action for [key], or null for a key the board does not use. The caller has already ruled out chords. */
fun nonogramKeyAction(key: Key, shift: Boolean): NonogramKeyAction? {
    arrowStep(key)?.let { (r, c) -> return NonogramKeyAction.Move(r, c, shift) }
    if (shift) return null
    return when (key) {
        Key.F -> NonogramKeyAction.Pen(NonogramLogic.FILLED)
        Key.X -> NonogramKeyAction.Pen(NonogramLogic.CROSSED)
        Key.Spacebar -> NonogramKeyAction.Cycle
        Key.Backspace, Key.Delete -> NonogramKeyAction.Clear
        else -> null
    }
}

/** The cursor after a key and the board it leaves; [board] is the same instance when nothing changed. */
data class NonogramKeyResult(val cursor: Int?, val board: NonogramState)

/**
 * [action] applied at [cursor] (null: no cursor yet, so an arrow lands on the first square and
 * other keys do nothing). Marks go through the same `tap` / `paint` / `sweep` a touch uses, one
 * state per key, so undo and the crossed-square rules cannot differ.
 */
fun NonogramState.applyKey(cursor: Int?, action: NonogramKeyAction): NonogramKeyResult {
    if (cursor == null) return NonogramKeyResult(if (action is NonogramKeyAction.Move) 0 else null, this)
    return when (action) {
        is NonogramKeyAction.Move -> {
            val to = stepCursor(cursor, action.dRow, action.dCol, height, width)
            val mark = cells[cursor]
            val board = if (action.lay && to != cursor && mark != NonogramLogic.UNMARKED) sweep(listOf(to), mark, mark) else this
            NonogramKeyResult(to, board)
        }
        is NonogramKeyAction.Pen -> NonogramKeyResult(cursor, tap(cursor, action.mark))
        NonogramKeyAction.Cycle -> {
            val next = when (cells[cursor]) {
                NonogramLogic.UNMARKED -> NonogramLogic.FILLED
                NonogramLogic.FILLED -> NonogramLogic.CROSSED
                else -> NonogramLogic.UNMARKED
            }
            NonogramKeyResult(cursor, paint(listOf(cursor), next))
        }
        NonogramKeyAction.Clear -> NonogramKeyResult(cursor, paint(listOf(cursor), NonogramLogic.UNMARKED))
    }
}
