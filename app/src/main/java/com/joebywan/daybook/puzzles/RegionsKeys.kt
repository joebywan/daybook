package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.DigitKeyAction
import com.joebywan.daybook.core.applyDigitKey

/** What pressing [digit] does to [cell]: places it, or clears the square if it already holds it. `this` if nothing changes. */
fun RegionsState.enter(cell: Int, digit: Int): RegionsState =
    withCell(cell, if (cells[cell] == digit) 0 else digit)

/** The board after [action] (from `digitKeyAction(key, digits)`), or null when it changes nothing; see [applyDigitKey]. */
fun RegionsState.applyKey(action: DigitKeyAction): RegionsState? =
    applyDigitKey(action, selected, size, { select(it) }, { cell, d -> enter(cell, d) }, { withCell(it, 0) })
