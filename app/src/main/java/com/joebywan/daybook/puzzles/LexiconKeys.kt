package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key

/** What a physical key asks of a Lexicon board. No state, no focus: that is [lexiconKeyAction]'s whole job. */
sealed interface LexiconKeyAction {
    data class Letter(val letter: Char) : LexiconKeyAction

    /** Enter: submit the row, or say why it cannot be. */
    data object Enter : LexiconKeyAction

    /** Backspace or Delete: take the last letter back. */
    data object Backspace : LexiconKeyAction
}

private val LETTER_KEYS = listOf(
    Key.A, Key.B, Key.C, Key.D, Key.E, Key.F, Key.G, Key.H, Key.I, Key.J, Key.K, Key.L, Key.M,
    Key.N, Key.O, Key.P, Key.Q, Key.R, Key.S, Key.T, Key.U, Key.V, Key.W, Key.X, Key.Y, Key.Z,
)

/** The action for [key], or null for a key the board does not use. The caller has already ruled out chords. */
fun lexiconKeyAction(key: Key): LexiconKeyAction? {
    val at = LETTER_KEYS.indexOf(key)
    return when {
        at >= 0 -> LexiconKeyAction.Letter('a' + at)
        key == Key.Enter || key == Key.NumPadEnter -> LexiconKeyAction.Enter
        key == Key.Backspace || key == Key.Delete -> LexiconKeyAction.Backspace
        else -> null
    }
}
