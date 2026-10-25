package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.digitKey
import com.joebywan.daybook.core.isEnterKey
import com.joebywan.daybook.core.isEraseKey

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
    if (isEraseKey(key)) return TowerKeyAction.Back
    if (isEnterKey(key)) return TowerKeyAction.Submit
    val digit = digitKey(key) ?: return null
    return if (digit in 1..colours) TowerKeyAction.Colour(digit - 1) else null
}

/** The board after [action], or null when it changes nothing (a full row, an empty one, not ready). */
fun TowerState.applyKey(action: TowerKeyAction): TowerState? = when (action) {
    is TowerKeyAction.Colour -> current.indexOf(-1).takeIf { it >= 0 }?.let { withPeg(it, action.index) }
    TowerKeyAction.Back -> current.indexOfLast { it >= 0 }.takeIf { it >= 0 }?.let { withPeg(it, -1) }
    TowerKeyAction.Submit -> if (ready) submit() else null
}
