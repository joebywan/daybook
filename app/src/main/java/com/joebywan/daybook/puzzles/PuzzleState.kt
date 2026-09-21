package com.joebywan.daybook.puzzles

import kotlinx.serialization.Serializable

/**
 * A single in-progress puzzle. Implementations are immutable data classes: every interaction
 * returns a *new* state, which is what makes undo, hints and "show solution" fall out for free.
 *
 * Sealed so that saving a part-finished game needs no hand-maintained table of serializers to keep
 * in step with the registry: the compiler enumerates the implementations and kotlinx.serialization
 * writes the class name into the JSON itself. The price is Kotlin's rule that implementations of a
 * sealed type share its *package*, not merely its module, which is why this interface lives in
 * `puzzles/` beside the boards rather than in `core/` with PuzzleType. Adding a puzzle is still one
 * file plus one registry line — the state class simply has to land in this package and be marked
 * `@Serializable`.
 */
@Serializable
sealed interface PuzzleState {
    /** True once the board satisfies the puzzle's win condition. */
    val solved: Boolean

    /** Interactions so far. Shown on the results card and used for scoring. */
    val moves: Int

    /** Some puzzles (Tower) can be lost outright. Most never are. */
    val failed: Boolean get() = false
}
