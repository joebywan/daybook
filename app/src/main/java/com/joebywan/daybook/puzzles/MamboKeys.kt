package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key

/** What a physical key asks of a Mambo cell (arrows move the cursor and are handled by the board). */
sealed interface MamboKeyAction {
    /** Space or Enter: empty, then moon, then sun, as a tap does. */
    data object Cycle : MamboKeyAction

    /** M, S or Backspace/Delete/0: put that symbol (or nothing) in the square. */
    data class Put(val sym: Sym) : MamboKeyAction
}

fun mamboKeyAction(key: Key): MamboKeyAction? = when (key) {
    Key.Spacebar, Key.Enter, Key.NumPadEnter -> MamboKeyAction.Cycle
    Key.M -> MamboKeyAction.Put(Sym.MOON)
    Key.S -> MamboKeyAction.Put(Sym.SUN)
    Key.Backspace, Key.Delete, Key.Zero, Key.NumPad0 -> MamboKeyAction.Put(Sym.NONE)
    else -> null
}

/** The board after [action] on [cell], or null when it changes nothing (a given, or already that symbol). */
fun MamboState.applyKey(cell: Int, action: MamboKeyAction): MamboState? {
    if (givens[cell]) return null
    val to = when (action) {
        MamboKeyAction.Cycle -> when (cells[cell]) {
            Sym.NONE -> Sym.MOON
            Sym.MOON -> Sym.SUN
            Sym.SUN -> Sym.NONE
        }
        is MamboKeyAction.Put -> action.sym
    }
    return if (to == cells[cell]) null else withCell(cell, to)
}
