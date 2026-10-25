package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.isEnterKey
import com.joebywan.daybook.core.isEraseKey
import com.joebywan.daybook.core.letterKey

/** What a physical key asks of a Lexicon board. No state, no focus: that is [lexiconKeyAction]'s whole job. */
sealed interface LexiconKeyAction {
    data class Letter(val letter: Char) : LexiconKeyAction

    /** Enter: submit the row, or say why it cannot be. */
    data object Enter : LexiconKeyAction

    /** Backspace or Delete: take the last letter back. */
    data object Backspace : LexiconKeyAction
}

/** The action for [key], or null for a key the board does not use. The caller has already ruled out chords. */
fun lexiconKeyAction(key: Key): LexiconKeyAction? = letterKey(key)?.let { LexiconKeyAction.Letter(it) } ?: when {
    isEnterKey(key) -> LexiconKeyAction.Enter
    isEraseKey(key) -> LexiconKeyAction.Backspace
    else -> null
}
