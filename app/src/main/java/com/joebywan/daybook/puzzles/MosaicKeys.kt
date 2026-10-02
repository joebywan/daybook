package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key

/** What a physical key asks of a Mosaic board (arrows move the cursor and are the board's). */
sealed interface MosaicKeyAction {
    /** 1..N: pick the palette's N-th colour. Only the swatch moves; nothing is poured. */
    data class Pick(val colour: Int) : MosaicKeyAction

    /** Space or Enter: pour the picked colour over the area under the cursor. */
    data object Fill : MosaicKeyAction
}

/** The action for [key] on a board with [colours] colours, or null. Digits past [colours] are not keys. */
fun mosaicKeyAction(key: Key, colours: Int): MosaicKeyAction? {
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
        Key.Spacebar, Key.Enter, Key.NumPadEnter -> return MosaicKeyAction.Fill
        else -> return null
    }
    return if (digit <= colours) MosaicKeyAction.Pick(digit - 1) else null
}

/**
 * The board after pouring [colour] over [cell]'s area, or null when the area is already that colour:
 * no state out means no move spent and no undo entry, as for a tap.
 */
fun MosaicState.fillKey(cell: Int, colour: Int): MosaicState? =
    if (cells[cell] == colour) null else flood(cell, colour)
