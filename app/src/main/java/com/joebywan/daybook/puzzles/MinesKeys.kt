package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key

/** What a physical key asks of the Mines square under the cursor (arrows are the board's, through `movedCursor`). */
enum class MinesKeyAction {
    /** Space or Enter: reveal it. */
    Reveal,

    /** F: flag it, or take its flag off. */
    Flag,
}

fun minesKeyAction(key: Key): MinesKeyAction? = when (key) {
    Key.Spacebar, Key.Enter, Key.NumPadEnter -> MinesKeyAction.Reveal
    Key.F -> MinesKeyAction.Flag
    else -> null
}
