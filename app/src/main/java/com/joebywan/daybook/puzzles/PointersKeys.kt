package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.arrowStep
import com.joebywan.daybook.core.nearestPiece

/**
 * The keyboard on a Pointers board (web, or a hardware keyboard): the arrow keys move a cursor from arrow to arrow,
 * Enter or Space taps the arrow under it, through the same [PointersState.tap] as a finger. The cursor is a square,
 * transient (`remember` in the board), never [PuzzleState].
 */
sealed interface PointersKeyAction {
    data class Move(val dRow: Int, val dCol: Int) : PointersKeyAction
    data object Tap : PointersKeyAction
}

fun pointersKeyAction(key: Key): PointersKeyAction? {
    arrowStep(key)?.let { (dr, dc) -> return PointersKeyAction.Move(dr, dc) }
    return when (key) {
        Key.Enter, Key.NumPadEnter, Key.Spacebar -> PointersKeyAction.Tap
        else -> null
    }
}

/**
 * Where the cursor goes from [from] (a square, or null for none yet) one step towards ([dRow], [dCol]): the nearest
 * square of another arrow still on the board that way, counting a square off to the side as twice as far as one
 * straight ahead (ties to the lower square). With no cursor yet, the head of the first arrow left. Stays put when
 * nothing lies that way; null only on an empty board.
 */
fun PointersState.cursorAfter(from: Int?, dRow: Int, dCol: Int): Int? {
    if (from == null) return arrows.indices.firstOrNull { it !in removed }?.let { arrows[it].last() }
    return nearestPiece(from, dRow, dCol, width, height) { arrowAt(it) }
}
