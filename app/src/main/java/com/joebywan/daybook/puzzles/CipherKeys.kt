package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.arrowStep
import com.joebywan.daybook.core.digitKey
import com.joebywan.daybook.core.isEraseKey
import com.joebywan.daybook.core.letterKey
import com.joebywan.daybook.puzzles.CipherLogic.EMPTY

/** What a physical key asks of a Cipher board. No state, no focus. */
sealed interface CipherKeyAction {
    /** a..z: pick that letter, if the sum has it. */
    data class Letter(val letter: Char) : CipherKeyAction

    /** 0..9: the digit pad's key of that number. */
    data class Digit(val digit: Int) : CipherKeyAction

    /** Backspace or Delete: take the selected letter's digit away. */
    data object Clear : CipherKeyAction

    /** An arrow: the next letter along the key (right or down) or the one before (left or up), never wrapping. */
    data class Move(val step: Int) : CipherKeyAction
}

/** The action for [key], or null for a key the board does not use. The caller has ruled out chords. */
fun cipherKeyAction(key: Key): CipherKeyAction? {
    arrowStep(key)?.let { (r, c) -> return CipherKeyAction.Move(r + c) }
    if (isEraseKey(key)) return CipherKeyAction.Clear
    letterKey(key)?.let { return CipherKeyAction.Letter(it) }
    return digitKey(key)?.let { CipherKeyAction.Digit(it) }
}

/** What pressing [digit] does to [letter]: gives it the digit, or takes it away if it already has it. */
fun CipherState.enter(letter: Int, digit: Int): CipherState =
    withDigit(letter, if (digits[letter] == digit) EMPTY else digit)

/**
 * The board after [action], or null when it changes nothing. One state per key, the same ones a tap emits.
 * With nothing selected an arrow picks the first letter.
 */
fun CipherState.applyKey(action: CipherKeyAction): CipherState? {
    val at = selected
    val next = when (action) {
        is CipherKeyAction.Letter -> sum.letters.indexOf(action.letter).let { if (it < 0) this else select(it) }
        is CipherKeyAction.Move -> if (at == null) select(0) else select((at + action.step).coerceIn(0, digits.size - 1))
        is CipherKeyAction.Digit -> if (at == null) this else enter(at, action.digit)
        CipherKeyAction.Clear -> if (at == null) this else withDigit(at, EMPTY)
    }
    return if (next == this) null else next
}
