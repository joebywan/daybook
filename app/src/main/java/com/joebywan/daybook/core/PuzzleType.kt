package com.joebywan.daybook.core

import androidx.compose.runtime.Composable

/**
 * A single in-progress puzzle. Implementations are immutable data classes: every interaction
 * returns a *new* state, which is what makes undo, hints and "show solution" fall out for free.
 */
interface PuzzleState {
    /** True once the board satisfies the puzzle's win condition. */
    val solved: Boolean

    /** Interactions so far. Shown on the results card and used for scoring. */
    val moves: Int

    /** Some puzzles (Tower) can be lost outright. Most never are. */
    val failed: Boolean get() = false
}

/**
 * One puzzle genre.
 *
 * ## Adding a puzzle
 * 1. Drop a file in `puzzles/` with a state class implementing [PuzzleState] and an object
 *    implementing [PuzzleType].
 * 2. Add that object to [PuzzleRegistry.all].
 *
 * That is the whole contract — the home screen, daily rotation, archive, streaks, stats, hints and
 * results card all pick the new puzzle up automatically. Nothing else in the app needs to know it
 * exists.
 *
 * [generate] **must** be a pure function of `(seed, difficulty)`. Daily puzzles are produced on the
 * device from the date, never fetched, so purity is what lets the entire back-catalogue be playable
 * offline and for free.
 */
interface PuzzleType {

    /** Stable identifier. Saved progress keys off this, so never rename one in place. */
    val id: String

    val displayName: String

    /** One line, shown on the home card. */
    val tagline: String

    /** How to play, shown before the first game and from the in-game menu. */
    val rules: List<String>

    /** ARGB accent used for this puzzle's card and board highlights. */
    val accent: Long

    fun generate(seed: Long, difficulty: Difficulty): PuzzleState

    /**
     * Draws the board and reports interactions back through [onState].
     * When [interactive] is false the board is being shown as a finished/preview state.
     */
    @Composable
    fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean)

    /**
     * Reveal one deducible step, or null if the puzzle offers no hints. Hints are unlimited and
     * free here; they cost a hint-count on the results card and nothing else.
     */
    fun hint(state: PuzzleState): PuzzleState? = null

    /** Fill in the full solution, for the "give up" path. Null if unsupported. */
    fun reveal(state: PuzzleState): PuzzleState? = null
}
