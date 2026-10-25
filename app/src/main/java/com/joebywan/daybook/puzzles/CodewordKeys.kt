package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.arrowStep
import com.joebywan.daybook.core.isEraseKey
import com.joebywan.daybook.core.letterKey
import com.joebywan.daybook.puzzles.CodewordLogic.EMPTY

/** What a physical key asks of a Codeword board. No state, no focus. */
sealed interface CodewordKeyAction {
    /** a..z, as 0..25: give the selected number that letter (again: take it away). */
    data class Letter(val letter: Int) : CodewordKeyAction

    /** Backspace or Delete: take the selected number's letter away. */
    data object Clear : CodewordKeyAction

    /** An arrow: the next number that is not given (right or down) or the one before (left or up), never wrapping. */
    data class Move(val step: Int) : CodewordKeyAction
}

/** The action for [key], or null for a key the board does not use. The caller has ruled out chords. */
fun codewordKeyAction(key: Key): CodewordKeyAction? {
    arrowStep(key)?.let { (r, c) -> return CodewordKeyAction.Move(r + c) }
    if (isEraseKey(key)) return CodewordKeyAction.Clear
    return letterKey(key)?.let { CodewordKeyAction.Letter(it - 'a') }
}

/** What pressing [letter] does to [number]: gives it the letter, or takes it away if it already has it. */
fun CodewordState.enter(number: Int, letter: Int): CodewordState =
    withLetter(number, if (letters[number - 1] == letter) EMPTY else letter)

/**
 * The board after [action], or null when it changes nothing. One state per key, the same ones a tap emits. With
 * nothing selected an arrow picks the first open number, and a letter does nothing (so the play screen keeps H).
 */
fun CodewordState.applyKey(action: CodewordKeyAction): CodewordState? {
    val at = selected
    val next = when (action) {
        is CodewordKeyAction.Letter -> if (at == null) this else enter(at, action.letter)
        is CodewordKeyAction.Move -> {
            val open = (1..letters.size).filter { it !in given }
            val i = if (at == null) 0 else (open.indexOf(at) + action.step).coerceIn(0, open.size - 1)
            open.getOrNull(i)?.let { select(it) } ?: this
        }
        CodewordKeyAction.Clear -> if (at == null) this else withLetter(at, EMPTY)
    }
    return if (next == this) null else next
}
