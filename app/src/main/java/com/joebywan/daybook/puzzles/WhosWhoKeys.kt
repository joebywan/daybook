package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.arrowStep
import com.joebywan.daybook.core.isConfirmKey
import com.joebywan.daybook.core.nearestPiece

/**
 * The keyboard on a Who's Who board (web, or a hardware keyboard): the arrow keys move a cursor from pill to pill as they
 * are drawn, Enter or Space taps the pill under it through the same [WhosWhoGraphState.tap] as a finger, Escape lets go of
 * the chosen item. The cursor is an item, transient (`remember` in the board), never [PuzzleState]. No letter is used, so
 * H still asks for a hint.
 */
sealed interface WhosWhoKeyAction {
    data class Move(val dRow: Int, val dCol: Int) : WhosWhoKeyAction
    data object Tap : WhosWhoKeyAction
    data object Clear : WhosWhoKeyAction
}

fun whosWhoKeyAction(key: Key): WhosWhoKeyAction? {
    arrowStep(key)?.let { (dr, dc) -> return WhosWhoKeyAction.Move(dr, dc) }
    return when {
        isConfirmKey(key) -> WhosWhoKeyAction.Tap
        key == Key.Escape -> WhosWhoKeyAction.Clear
        else -> null
    }
}

/**
 * Where the cursor goes from item [from] (null: none yet, so the first item) one step towards ([dRow], [dCol]): the
 * nearest other pill that way as drawn, each item in its category's column and the row [row] gives it ([WhosWho.rows]),
 * a pill off to the side counting twice as far as one straight ahead. Stays put when nothing lies that way.
 */
internal fun WhosWhoGraphState.cursorAfter(from: Int?, dRow: Int, dCol: Int, row: IntArray): Int {
    if (from == null) return 0
    val cols = board.columns + 1
    val at = IntArray(cols * n) { -1 }
    for (i in 0 until size) at[row[i] * cols + i / n] = i
    return at[nearestPiece(row[from] * cols + from / n, dRow, dCol, cols, n) { at[it] }]
}
