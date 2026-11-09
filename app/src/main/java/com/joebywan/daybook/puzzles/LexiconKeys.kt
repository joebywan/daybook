package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.isEnterKey
import com.joebywan.daybook.core.isEraseKey
import com.joebywan.daybook.core.letterKey

/** The action for [key], or null for a key the board does not use. The caller has already ruled out chords. */
fun lexiconKeyAction(key: Key): GuessKeyAction? = letterKey(key)?.let { GuessKeyAction.Letter(it) } ?: when {
    isEnterKey(key) -> GuessKeyAction.Enter
    isEraseKey(key) -> GuessKeyAction.Backspace
    else -> null
}
