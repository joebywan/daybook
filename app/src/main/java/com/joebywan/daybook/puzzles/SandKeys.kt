package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key

/** What a physical key asks of a Sand board. */
sealed interface SandKeyAction {
    /** 1-9 pick tubes 1-9 and 0 picks tube 10: the first press lifts, the second pours. */
    data class Tube(val index: Int) : SandKeyAction

    /** Escape: put the lifted tube down. */
    data object PutDown : SandKeyAction
}

/** The action for [key], or null. */
fun sandKeyAction(key: Key): SandKeyAction? = when (key) {
    Key.One, Key.NumPad1 -> SandKeyAction.Tube(0)
    Key.Two, Key.NumPad2 -> SandKeyAction.Tube(1)
    Key.Three, Key.NumPad3 -> SandKeyAction.Tube(2)
    Key.Four, Key.NumPad4 -> SandKeyAction.Tube(3)
    Key.Five, Key.NumPad5 -> SandKeyAction.Tube(4)
    Key.Six, Key.NumPad6 -> SandKeyAction.Tube(5)
    Key.Seven, Key.NumPad7 -> SandKeyAction.Tube(6)
    Key.Eight, Key.NumPad8 -> SandKeyAction.Tube(7)
    Key.Nine, Key.NumPad9 -> SandKeyAction.Tube(8)
    Key.Zero, Key.NumPad0 -> SandKeyAction.Tube(9)
    Key.Escape -> SandKeyAction.PutDown
    else -> null
}

/**
 * What one tap or key did: the tube lifted after it (transient, `remember` in the board, never [PuzzleState]), the board
 * after a pour if one was made, and the tube to shake if a pour was refused.
 */
class SandStep(val lifted: Int?, val state: SandState? = null, val refused: Int? = null)

/**
 * A tap on [tube] with [lifted] in the air. Nothing lifted: lift it, if it has sand. The lifted tube: put it down.
 * Another tube: pour, or shake it when the rules refuse (the lifted tube stays up).
 */
fun SandState.tap(lifted: Int?, tube: Int): SandStep {
    if (tube !in tubes.indices || solved) return SandStep(lifted)
    if (lifted == null) return SandStep(if (tubes[tube].isNotEmpty()) tube else null)
    if (lifted == tube) return SandStep(null)
    val next = pour(lifted, tube) ?: return SandStep(lifted, refused = tube)
    return SandStep(null, next)
}

/** One key, through [tap], so a key and a tap cannot differ. */
fun SandState.press(lifted: Int?, action: SandKeyAction): SandStep = when (action) {
    is SandKeyAction.Tube -> if (action.index in tubes.indices) tap(lifted, action.index) else SandStep(lifted)
    SandKeyAction.PutDown -> SandStep(null)
}
