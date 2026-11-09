package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.digitKey
import com.joebywan.daybook.core.isEnterKey
import com.joebywan.daybook.core.isEraseKey
import com.joebywan.daybook.core.operatorKey

/**
 * The action for [key] on an Equation board ([shift] held: Shift+= is +, Shift+8 is ×), or null for a key the board does
 * not use: digits (top row or pad), + - * / (X is × too), = (the = key unshifted, or the pad's), Enter and Backspace.
 */
fun equationKeyAction(key: Key, shift: Boolean = false): GuessKeyAction? {
    operatorKey(key, shift)?.let { return GuessKeyAction.Letter(it) }
    if (!shift && (key == Key.Equals || key == Key.NumPadEquals)) return GuessKeyAction.Letter('=')
    digitKey(key)?.let { return if (shift) null else GuessKeyAction.Letter('0' + it) }
    return when {
        isEnterKey(key) -> GuessKeyAction.Enter
        isEraseKey(key) -> GuessKeyAction.Backspace
        else -> null
    }
}
