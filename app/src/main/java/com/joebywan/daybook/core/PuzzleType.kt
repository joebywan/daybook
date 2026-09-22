package com.joebywan.daybook.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.joebywan.daybook.puzzles.PuzzleState

/**
 * One puzzle genre.
 *
 * ## Adding a puzzle
 * 1. Drop a file in `puzzles/` with a `@Serializable` state class implementing [PuzzleState]
 *    and an object implementing [PuzzleType]. The state class has to be in the `puzzles` package
 *    itself: [PuzzleState] is sealed so that saved games serialize without a registry, and Kotlin
 *    only allows implementations of a sealed type in its own package.
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
     * A small static motif for the home grid: a few cells of this puzzle's own board, enough to
     * recognise it by shape rather than by reading its name.
     *
     * Must be cheap, and must NOT call [generate]. The home screen draws one of these for every
     * registered puzzle on every composition, and generating real boards for eleven of them would
     * cost hundreds of milliseconds on a screen that has to appear instantly. Hand-pick a fixed
     * arrangement instead — it is an illustration, not a playable board, and it never changes.
     *
     * The default is a plain accent block, so a new puzzle appears in the grid before anyone has
     * drawn its motif.
     */
    @Composable
    fun Preview(modifier: Modifier) {
        Box(modifier.background(Color(accent).copy(alpha = 0.55f)))
    }

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

    /** Whether this puzzle can offer a deducible next step. Drives whether the Hint button appears. */
    val offersHints: Boolean get() = true
}
