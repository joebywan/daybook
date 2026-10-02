package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key

/** What a physical key asks of a Tower board. There is no cursor: pegs fill left to right. */
sealed interface TowerKeyAction {
    /** 1..N: pick that colour (the swatch row's N-th) and put it in the next empty peg. */
    data class Colour(val index: Int) : TowerKeyAction

    /** Backspace or Delete: empty the last filled peg. */
    data object Back : TowerKeyAction

    /** Enter: submit the guess, when every peg is filled. */
    data object Submit : TowerKeyAction
}

/** The action for [key] on a board with [colours] colours, or null. Digits past [colours] are not keys. */
fun towerKeyAction(key: Key, colours: Int): TowerKeyAction? {
    val digit = when (key) {
        Key.One, Key.NumPad1 -> 1
        Key.Two, Key.NumPad2 -> 2
        Key.Three, Key.NumPad3 -> 3
        Key.Four, Key.NumPad4 -> 4
        Key.Five, Key.NumPad5 -> 5
        Key.Six, Key.NumPad6 -> 6
        Key.Seven, Key.NumPad7 -> 7
        Key.Eight, Key.NumPad8 -> 8
        Key.Nine, Key.NumPad9 -> 9
        Key.Backspace, Key.Delete -> return TowerKeyAction.Back
        Key.Enter, Key.NumPadEnter -> return TowerKeyAction.Submit
        else -> return null
    }
    return if (digit <= colours) TowerKeyAction.Colour(digit - 1) else null
}

/** The board after [action], or null when it changes nothing (a full row, an empty one, not ready). */
fun TowerState.applyKey(action: TowerKeyAction): TowerState? = when (action) {
    is TowerKeyAction.Colour -> current.indexOf(-1).takeIf { it >= 0 }?.let { withPeg(it, action.index) }
    TowerKeyAction.Back -> current.indexOfLast { it >= 0 }.takeIf { it >= 0 }?.let { withPeg(it, -1) }
    TowerKeyAction.Submit -> if (ready) submit() else null
}
