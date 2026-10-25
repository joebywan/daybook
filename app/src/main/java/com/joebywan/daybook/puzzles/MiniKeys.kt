package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.arrowStep
import com.joebywan.daybook.core.isEraseKey
import com.joebywan.daybook.core.letterKey
import com.joebywan.daybook.puzzles.MiniLogic.BLACK

/** What a physical key asks of a Mini crossword. No state, no focus. */
sealed interface MiniKeyAction {
    /** a..z: type it at the cursor and move on along the word. */
    data class Letter(val letter: Char) : MiniKeyAction

    /** Backspace or Delete: clear the cursor's letter, or step back and clear that one. */
    data object Erase : MiniKeyAction

    /** An arrow: turn first if it points the other way from the word, else move to the next white square that way. */
    data class Move(val dRow: Int, val dCol: Int) : MiniKeyAction

    /** Space: turn between Across and Down. */
    data object Turn : MiniKeyAction
}

/** The action for [key], or null for a key the board does not use. The caller has ruled out chords. */
fun miniKeyAction(key: Key): MiniKeyAction? {
    arrowStep(key)?.let { (r, c) -> return MiniKeyAction.Move(r, c) }
    if (isEraseKey(key)) return MiniKeyAction.Erase
    if (key == Key.Spacebar) return MiniKeyAction.Turn
    return letterKey(key)?.let { MiniKeyAction.Letter(it) }
}

/**
 * The board after [action], or null when it changes nothing. One state per key, the same ones a tap emits. With nothing
 * selected an arrow picks the first white square, and the rest do nothing.
 */
fun MiniState.applyKey(action: MiniKeyAction): MiniState? {
    val at = cursor
    val next = when (action) {
        is MiniKeyAction.Letter -> type(action.letter)
        MiniKeyAction.Erase -> erase()
        MiniKeyAction.Turn -> turn()
        is MiniKeyAction.Move -> when {
            at == null -> select(letters.indexOfFirst { it != BLACK })
            (action.dCol != 0) != across && hasSlot(at, action.dCol != 0) -> turn()
            else -> {
                var r = at / size + action.dRow
                var c = at % size + action.dCol
                while (r in 0 until size && c in 0 until size && letters[r * size + c] == BLACK) { r += action.dRow; c += action.dCol }
                if (r in 0 until size && c in 0 until size) copy(cursor = r * size + c, across = if (hasSlot(r * size + c, across)) across else !across) else this
            }
        }
    }
    return if (next == this) null else next
}
