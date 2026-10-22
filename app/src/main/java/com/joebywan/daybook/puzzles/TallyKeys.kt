package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.arrowStep
import com.joebywan.daybook.core.stepCursor

/** What a physical key asks of a Tally board. */
sealed interface TallyKeyAction {
    /** An arrow: move the cursor one square. With [lay], Shift was held: carry the square's mark along. */
    data class Move(val dRow: Int, val dCol: Int, val lay: Boolean) : TallyKeyAction

    /** X (remove) or K (keep): that pen's mark on the cursor square, or an untouched square if it already has it. */
    data class Pen(val mark: Char) : TallyKeyAction

    /** Space: untouched, then removed, then kept, then untouched again. */
    data object Cycle : TallyKeyAction

    /** Backspace or Delete. */
    data object Clear : TallyKeyAction
}

/** The action for [key], or null for a key the board does not use. The caller has already ruled out chords. */
fun tallyKeyAction(key: Key, shift: Boolean): TallyKeyAction? {
    arrowStep(key)?.let { (r, c) -> return TallyKeyAction.Move(r, c, shift) }
    if (shift) return null
    return when (key) {
        Key.X, Key.R -> TallyKeyAction.Pen(TallyLogic.REMOVED)
        Key.K, Key.O -> TallyKeyAction.Pen(TallyLogic.KEPT)
        Key.Spacebar -> TallyKeyAction.Cycle
        Key.Backspace, Key.Delete -> TallyKeyAction.Clear
        else -> null
    }
}

/** The cursor after a key and the board it leaves; [board] is the same instance when nothing changed. */
data class TallyKeyResult(val cursor: Int?, val board: TallyState)

/**
 * [action] applied at [cursor] (null: no cursor yet, so an arrow lands on the first square and
 * other keys do nothing). Marks go through the same `tap` / `paint` / `sweep` a touch uses, one
 * state per key, so undo cannot differ.
 */
fun TallyState.applyKey(cursor: Int?, action: TallyKeyAction): TallyKeyResult {
    if (cursor == null) return TallyKeyResult(if (action is TallyKeyAction.Move) 0 else null, this)
    return when (action) {
        is TallyKeyAction.Move -> {
            val to = stepCursor(cursor, action.dRow, action.dCol, size, size)
            val mark = marks[cursor]
            val board = if (action.lay && to != cursor && mark != TallyLogic.UNMARKED) sweep(listOf(to), mark, mark) else this
            TallyKeyResult(to, board)
        }
        is TallyKeyAction.Pen -> TallyKeyResult(cursor, tap(cursor, action.mark))
        TallyKeyAction.Cycle -> {
            val next = when (marks[cursor]) {
                TallyLogic.UNMARKED -> TallyLogic.REMOVED
                TallyLogic.REMOVED -> TallyLogic.KEPT
                else -> TallyLogic.UNMARKED
            }
            TallyKeyResult(cursor, paint(listOf(cursor), next))
        }
        TallyKeyAction.Clear -> TallyKeyResult(cursor, paint(listOf(cursor), TallyLogic.UNMARKED))
    }
}
