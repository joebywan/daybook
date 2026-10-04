package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key

/** What a physical key asks of the Tents square under the cursor (arrows are the board's). */
enum class TentsKeyAction {
    /** T or Enter: a tent there, or take it back. */
    Tent,

    /** X or G: grass there, or take it back. */
    Grass,

    /** Space: the same cycle a tap makes: empty, tent, grass, empty. */
    Cycle,

    /** Backspace or Delete: empty the square. */
    Clear,
}

fun tentsKeyAction(key: Key): TentsKeyAction? = when (key) {
    Key.T, Key.Enter, Key.NumPadEnter -> TentsKeyAction.Tent
    Key.X, Key.G -> TentsKeyAction.Grass
    Key.Spacebar -> TentsKeyAction.Cycle
    Key.Backspace, Key.Delete -> TentsKeyAction.Clear
    else -> null
}

/** What a tap does: empty, tent, grass, empty. `this` on a tree. */
fun TentsState.cycled(cell: Int): TentsState = withMark(cell, (marks[cell] + 1) % 3)

/** The board after [action] on [cell], or null when it changes nothing. Goes through [withMark], as a tap does. */
fun TentsState.applyKey(cell: Int, action: TentsKeyAction): TentsState? {
    val next = when (action) {
        TentsKeyAction.Tent -> withMark(cell, if (marks[cell] == TentsLogic.TENT) 0 else TentsLogic.TENT)
        TentsKeyAction.Grass -> withMark(cell, if (marks[cell] == TentsLogic.GRASS) 0 else TentsLogic.GRASS)
        TentsKeyAction.Cycle -> cycled(cell)
        TentsKeyAction.Clear -> withMark(cell, 0)
    }
    return if (next === this) null else next
}
