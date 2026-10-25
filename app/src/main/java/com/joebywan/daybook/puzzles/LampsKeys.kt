package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key

/** What a physical key asks of the Lamps square under the cursor (arrows are the board's). */
enum class LampsKeyAction {
    /** L or Enter: a lamp there, or take it back. */
    Lamp,

    /** X or Space: a cross there, or take it back. */
    Cross,

    /** Backspace or Delete: empty the square. */
    Clear,
}

fun lampsKeyAction(key: Key): LampsKeyAction? = when (key) {
    Key.L, Key.Enter, Key.NumPadEnter -> LampsKeyAction.Lamp
    Key.X, Key.Spacebar -> LampsKeyAction.Cross
    Key.Backspace, Key.Delete -> LampsKeyAction.Clear
    else -> null
}

/** A lamp on [cell], or taken back if one is there. `this` on a wall. */
fun LampsState.withLamp(cell: Int): LampsState =
    withMark(cell, if (marks[cell] == LampsLogic.LAMP_MARK) LampsLogic.NONE else LampsLogic.LAMP_MARK)

/** What a single tap does: a cross on an empty square, off again if it is a cross or a lamp. `this` on a wall. */
fun LampsState.tapped(cell: Int): LampsState =
    withMark(cell, if (marks[cell] == LampsLogic.NONE) LampsLogic.CROSS else LampsLogic.NONE)

/** A cross on every empty white square of [cells], as one move. `this` when none qualifies. */
fun LampsState.crossed(cells: Set<Int>): LampsState {
    val add = cells.filter { !isWall(it) && marks[it] == LampsLogic.NONE }
    if (add.isEmpty()) return this
    val m = marks.toCharArray()
    add.forEach { m[it] = LampsLogic.CROSS }
    return copy(marks = m.concatToString(), moves = moves + 1)
}

/** The board after [action] on [cell], or null when it changes nothing. Goes through [withMark], as a tap does. */
fun LampsState.applyKey(cell: Int, action: LampsKeyAction): LampsState? {
    val next = when (action) {
        LampsKeyAction.Lamp -> withLamp(cell)
        LampsKeyAction.Cross -> withMark(cell, if (marks[cell] == LampsLogic.CROSS) LampsLogic.NONE else LampsLogic.CROSS)
        LampsKeyAction.Clear -> withMark(cell, LampsLogic.NONE)
    }
    return if (next === this) null else next
}
