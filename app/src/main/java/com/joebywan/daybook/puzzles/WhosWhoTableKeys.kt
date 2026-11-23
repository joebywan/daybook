package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.arrowStep
import com.joebywan.daybook.core.digitKey
import com.joebywan.daybook.core.isEraseKey
import com.joebywan.daybook.core.stepCursor

/** What a physical key asks of the test table view of Who's Who ([WhosWhoTable]). */
sealed interface TableKeyAction {
    /** An arrow: move the selection one square. */
    data class Move(val dRow: Int, val dCol: Int) : TableKeyAction

    /** 1-5: tick item [slot] in the selected square ([tick]), or with Shift cross it; again takes the mark off. */
    data class Mark(val slot: Int, val tick: Boolean) : TableKeyAction

    /** Backspace or Delete: every mark in the square off. */
    data object Clear : TableKeyAction
}

/** The action for [key], or null for a key the board does not use. The caller has already ruled out chords. */
fun tableKeyAction(key: Key, shift: Boolean): TableKeyAction? {
    if (!shift) arrowStep(key)?.let { (r, c) -> return TableKeyAction.Move(r, c) }
    val digit = digitKey(key)?.takeIf { it in 1..5 }
    if (digit != null) return TableKeyAction.Mark(digit - 1, tick = !shift)
    return if (!shift && isEraseKey(key)) TableKeyAction.Clear else null
}

/** The selection after a key and the board it leaves; [board] is the same instance when nothing changed. */
data class TableKeyResult(val cursor: Int?, val board: WhosWhoState)

/**
 * [action] applied with [cursor] selected (a table cell, `person * columns + column`; null: none yet, so an arrow
 * selects the first square and other keys do nothing). Marks go through the same [WhosWhoState.mark] a tap on the pad
 * uses, one state per key, so undo cannot differ. A digit past the board's size does nothing.
 */
fun WhosWhoState.applyTableKey(cursor: Int?, action: TableKeyAction): TableKeyResult {
    if (cursor == null) return TableKeyResult(if (action is TableKeyAction.Move) 0 else null, this)
    val person = cursor / columns
    val column = cursor % columns
    return when (action) {
        is TableKeyAction.Move -> TableKeyResult(stepCursor(cursor, action.dRow, action.dCol, n, columns), this)
        is TableKeyAction.Mark ->
            TableKeyResult(cursor, if (action.slot < n) mark(person, column, action.slot, action.tick) else this)
        TableKeyAction.Clear -> TableKeyResult(cursor, clearCell(person, column))
    }
}
