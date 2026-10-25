package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.digitKey

/** What a physical key asks of a Sand board. */
sealed interface SandKeyAction {
    /** 1-9 pick tubes 1-9 and 0 picks tube 10: the first press lifts, the second pours. */
    data class Tube(val index: Int) : SandKeyAction

    /** Escape: put the lifted tube down. */
    data object PutDown : SandKeyAction
}

/** The action for [key], or null. */
fun sandKeyAction(key: Key): SandKeyAction? =
    if (key == Key.Escape) SandKeyAction.PutDown
    else digitKey(key)?.let { SandKeyAction.Tube((it + 9) % 10) }

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
