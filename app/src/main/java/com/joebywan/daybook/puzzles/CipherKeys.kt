package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.arrowStep
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

private val LETTER_KEYS = listOf(
    Key.A, Key.B, Key.C, Key.D, Key.E, Key.F, Key.G, Key.H, Key.I, Key.J, Key.K, Key.L, Key.M,
    Key.N, Key.O, Key.P, Key.Q, Key.R, Key.S, Key.T, Key.U, Key.V, Key.W, Key.X, Key.Y, Key.Z,
)

private val DIGIT_KEYS = listOf(
    Key.Zero to Key.NumPad0, Key.One to Key.NumPad1, Key.Two to Key.NumPad2, Key.Three to Key.NumPad3,
    Key.Four to Key.NumPad4, Key.Five to Key.NumPad5, Key.Six to Key.NumPad6, Key.Seven to Key.NumPad7,
    Key.Eight to Key.NumPad8, Key.Nine to Key.NumPad9,
)

/** The action for [key], or null for a key the board does not use. The caller has ruled out chords. */
fun cipherKeyAction(key: Key): CipherKeyAction? {
    arrowStep(key)?.let { (r, c) -> return CipherKeyAction.Move(r + c) }
    if (key == Key.Backspace || key == Key.Delete) return CipherKeyAction.Clear
    val letter = LETTER_KEYS.indexOf(key)
    if (letter >= 0) return CipherKeyAction.Letter('a' + letter)
    val digit = DIGIT_KEYS.indexOfFirst { key == it.first || key == it.second }
    return if (digit >= 0) CipherKeyAction.Digit(digit) else null
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
