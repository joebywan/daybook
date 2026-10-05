package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key

/** What a physical key asks of the Tents square under the cursor (arrows are the board's). */
enum class TentsKeyAction {
    /** T or Enter: a tent there, or take it back. */
    Tent,

    /** X, G or Space: grass there, or take it back. */
    Grass,

    /** Backspace or Delete: empty the square. */
    Clear,
}

fun tentsKeyAction(key: Key): TentsKeyAction? = when (key) {
    Key.T, Key.Enter, Key.NumPadEnter -> TentsKeyAction.Tent
    Key.X, Key.G, Key.Spacebar -> TentsKeyAction.Grass
    Key.Backspace, Key.Delete -> TentsKeyAction.Clear
    else -> null
}

/** A tent on [cell], or taken back if one is there. Its grass is derived (see [TentsState.seen]), so nothing else changes. `this` on a tree. */
fun TentsState.withTent(cell: Int): TentsState =
    if (trees[cell]) this else withMark(cell, if (marks[cell] == TentsLogic.TENT) 0 else TentsLogic.TENT)

/** Grass on every empty non-tree square of [cells] (one a tent already sprouted grass on counts as grass), as one move. `this` when none qualifies. */
fun TentsState.grassed(cells: Set<Int>): TentsState {
    val shown = seen
    val add = cells.filter { !trees[it] && shown[it] == 0 }
    if (add.isEmpty()) return this
    val m = marks.toMutableList()
    add.forEach { m[it] = TentsLogic.GRASS }
    return copy(marks = m, moves = moves + 1)
}

/** What a single tap does: grass on an empty square, off again if it is grass or a tent. `this` on a tree or on grass a tent sprouted. */
fun TentsState.tapped(cell: Int): TentsState =
    if (marks[cell] == 0 && seen[cell] == TentsLogic.GRASS) this else withMark(cell, if (marks[cell] == 0) TentsLogic.GRASS else 0)

/** The board after [action] on [cell], or null when it changes nothing. Goes through [withMark], as a tap does. */
fun TentsState.applyKey(cell: Int, action: TentsKeyAction): TentsState? {
    val next = when (action) {
        TentsKeyAction.Tent -> withTent(cell)
        TentsKeyAction.Grass -> withMark(cell, if (marks[cell] == TentsLogic.GRASS) 0 else TentsLogic.GRASS)
        TentsKeyAction.Clear -> withMark(cell, 0)
    }
    return if (next === this) null else next
}
