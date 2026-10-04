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

/** A tent on [cell], or taken back if one is there. A new tent sprouts grass on every empty square around it, in one move. `this` on a tree. */
fun TentsState.withTent(cell: Int): TentsState {
    if (trees[cell]) return this
    val m = marks.toMutableList()
    if (m[cell] == TentsLogic.TENT) m[cell] = 0
    else {
        m[cell] = TentsLogic.TENT
        for (j in TentsLogic.around(size, cell)) if (m[j] == 0 && !trees[j]) m[j] = TentsLogic.GRASS
    }
    return copy(marks = m, moves = moves + 1)
}

/** Grass on every empty non-tree square of [cells], as one move. `this` when none qualifies. */
fun TentsState.grassed(cells: Set<Int>): TentsState {
    val add = cells.filter { !trees[it] && marks[it] == 0 }
    if (add.isEmpty()) return this
    val m = marks.toMutableList()
    add.forEach { m[it] = TentsLogic.GRASS }
    return copy(marks = m, moves = moves + 1)
}

/** What a single tap does: grass on an empty square, off again if it is grass or a tent. `this` on a tree. */
fun TentsState.tapped(cell: Int): TentsState = withMark(cell, if (marks[cell] == 0) TentsLogic.GRASS else 0)

/** The board after [action] on [cell], or null when it changes nothing. Goes through [withMark], as a tap does. */
fun TentsState.applyKey(cell: Int, action: TentsKeyAction): TentsState? {
    val next = when (action) {
        TentsKeyAction.Tent -> withTent(cell)
        TentsKeyAction.Grass -> withMark(cell, if (marks[cell] == TentsLogic.GRASS) 0 else TentsLogic.GRASS)
        TentsKeyAction.Clear -> withMark(cell, 0)
    }
    return if (next === this) null else next
}
