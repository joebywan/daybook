package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.digitKey
import com.joebywan.daybook.core.isConfirmKey

/** What a physical key asks of a Mosaic board (arrows move the cursor and are the board's). */
sealed interface MosaicKeyAction {
    /** 1..N: pick the palette's N-th colour. Only the swatch moves; nothing is poured. */
    data class Pick(val colour: Int) : MosaicKeyAction

    /** Space or Enter: pour the picked colour over the area under the cursor. */
    data object Fill : MosaicKeyAction
}

/** The action for [key] on a board with [colours] colours, or null. Digits past [colours] are not keys. */
fun mosaicKeyAction(key: Key, colours: Int): MosaicKeyAction? {
    if (isConfirmKey(key)) return MosaicKeyAction.Fill
    val digit = digitKey(key) ?: return null
    return if (digit in 1..colours) MosaicKeyAction.Pick(digit - 1) else null
}

/**
 * The board after pouring [colour] over [cell]'s area, or null when the area is already that colour:
 * no state out means no move spent and no undo entry, as for a tap.
 */
fun MosaicState.fillKey(cell: Int, colour: Int): MosaicState? =
    if (cells[cell] == colour) null else flood(cell, colour)
