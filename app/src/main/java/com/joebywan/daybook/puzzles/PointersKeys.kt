package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.arrowStep
import kotlin.math.abs

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
    val mine = arrowAt(from)
    val fr = from / width
    val fc = from % width
    var best = from
    var bestScore = Int.MAX_VALUE
    for (cell in 0 until width * height) {
        val a = arrowAt(cell)
        if (a < 0 || a == mine) continue
        val dr = cell / width - fr
        val dc = cell % width - fc
        val ahead = dr * dRow + dc * dCol
        if (ahead <= 0) continue
        val score = ahead + 2 * abs(dr * dCol + dc * dRow)
        if (score < bestScore) {
            bestScore = score
            best = cell
        }
    }
    return best
}
