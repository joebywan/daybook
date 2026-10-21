package com.joebywan.daybook.puzzles

import kotlinx.serialization.Serializable

/**
 * A Sand board: [tubes] bottom to top, colour indices `0 until colours`, each tube holding up to [height] layers.
 * Nothing else is stored: no solution path, no par. Field names and meanings are fixed once shipped (saves).
 */
@Serializable
data class SandState(
    val tubes: List<List<Int>>,
    val height: Int,
    val colours: Int,
    override val moves: Int = 0,
) : PuzzleState {
    override val solved: Boolean get() = SandLogic.isSolved(tubes, height)

    /** Not solved and no pour left. Derived, never stored. */
    val stuck: Boolean get() = SandLogic.isStuck(tubes, height)

    /** The board after pouring [from] onto [to], one move, or null when the rules refuse it. */
    fun pour(from: Int, to: Int): SandState? =
        if (SandLogic.canPour(tubes, height, from, to)) copy(tubes = SandLogic.pour(tubes, height, from, to), moves = moves + 1) else null
}
