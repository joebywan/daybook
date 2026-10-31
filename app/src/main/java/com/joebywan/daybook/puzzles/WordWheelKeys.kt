package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.isEnterKey
import com.joebywan.daybook.core.isEraseKey
import com.joebywan.daybook.core.letterKey

/** What a physical key asks of a Word Wheel board. No state, no focus: that is [wordWheelKeyAction]'s whole job. */
sealed interface WheelKeyAction {
    data class Letter(val letter: Char) : WheelKeyAction

    /** Enter: try the word spelled so far. */
    data object Enter : WheelKeyAction

    /** Backspace or Delete: take the last letter back. */
    data object Backspace : WheelKeyAction

    /** Escape: clear the word. */
    data object Clear : WheelKeyAction
}

/** The action for [key], or null for a key the board does not use. The caller has already ruled out chords. */
fun wordWheelKeyAction(key: Key): WheelKeyAction? = letterKey(key)?.let { WheelKeyAction.Letter(it) } ?: when {
    isEnterKey(key) -> WheelKeyAction.Enter
    isEraseKey(key) -> WheelKeyAction.Backspace
    key == Key.Escape -> WheelKeyAction.Clear
    else -> null
}

/**
 * The word being spelled, as wheel positions, after [letter] is typed: the first copy of it on the [wheel] not already
 * used, so a wheel with two Es takes two. Null when the wheel has no copy left: the key does nothing.
 */
fun typeWheelLetter(wheel: String, picked: List<Int>, letter: Char): List<Int>? {
    val i = wheel.indices.firstOrNull { wheel[it] == letter && it !in picked } ?: return null
    return picked + i
}

/**
 * The word being spelled after a tap on wheel letter [index]: added if it is not in the word yet, taken back if it is the
 * last letter (a second tap undoes the first), and otherwise ignored, since each letter is used once.
 */
fun tapWheelLetter(picked: List<Int>, index: Int): List<Int> = when {
    picked.lastOrNull() == index -> picked.dropLast(1)
    index in picked -> picked
    else -> picked + index
}

/**
 * The swipe after the finger reaches wheel letter [index]: added if new, the last one taken back if the finger has returned
 * to the letter before it (so a swipe can be backed out of), otherwise unchanged.
 */
fun swipeWheelLetter(path: List<Int>, index: Int): List<Int> = when {
    path.lastOrNull() == index -> path
    path.size >= 2 && path[path.size - 2] == index -> path.dropLast(1)
    index in path -> path
    else -> path + index
}

/** Which step of the scale a word going from [before] to [after] letters sounds: the new length's; nothing if it did not change or emptied. */
fun wheelToneStep(before: Int, after: Int): Int? = if (after == before || after == 0) null else after - 1
