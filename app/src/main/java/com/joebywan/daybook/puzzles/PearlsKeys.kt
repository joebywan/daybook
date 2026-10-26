package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.arrowStep
import com.joebywan.daybook.core.stepCursor

/** What a physical key asks of a Pearls board. */
sealed interface PearlsKeyAction {
    /** An arrow: move the cursor one square. With [draw], Shift was held: draw (or rub out) the line that way and move along it. */
    data class Move(val dRow: Int, val dCol: Int, val draw: Boolean) : PearlsKeyAction

    /** X: the next arrow crosses (or uncrosses) the edge that way, and the cursor stays. */
    data object Cross : PearlsKeyAction
}

/** The action for [key], or null for a key the board does not use. The caller has already ruled out chords. */
fun pearlsKeyAction(key: Key, shift: Boolean): PearlsKeyAction? {
    arrowStep(key)?.let { (r, c) -> return PearlsKeyAction.Move(r, c, shift) }
    if (shift) return null
    return if (key == Key.X) PearlsKeyAction.Cross else null
}

/** The cursor after a key, whether a cross is armed, and the board it leaves; [board] is the same instance when nothing changed. */
data class PearlsKeyResult(val cursor: Int?, val armed: Boolean, val board: PearlsState)

/**
 * [action] applied at [cursor] with a cross [armed] or not (null cursor: no cursor yet, so an arrow lands on
 * the first square and X does nothing). A line goes through the same [PearlsState.drag] a finger uses, and a
 * cross through [PearlsState.tap] (so X on a line rubs it out, as a tap does), one state per key, so undo cannot differ. A move off the board's edge
 * draws nothing.
 */
fun PearlsState.applyKey(cursor: Int?, armed: Boolean, action: PearlsKeyAction): PearlsKeyResult {
    if (cursor == null) return PearlsKeyResult(if (action is PearlsKeyAction.Move) 0 else null, false, this)
    return when (action) {
        PearlsKeyAction.Cross -> PearlsKeyResult(cursor, !armed, this)
        is PearlsKeyAction.Move -> {
            val to = stepCursor(cursor, action.dRow, action.dCol, size, size)
            val edge = if (to == cursor) -1 else PearlsGrid.of(size).between(cursor, to)
            when {
                armed -> PearlsKeyResult(cursor, false, if (edge >= 0) tap(edge) else this)
                action.draw -> PearlsKeyResult(to, false, if (edge >= 0) drag(listOf(cursor, to)) else this)
                else -> PearlsKeyResult(to, false, this)
            }
        }
    }
}

