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
 * That is the wiring — the home screen, daily rotation, archive, streaks, stats, hints and
 * results card all pick the new puzzle up automatically. The build and the tests then ask for
 * more, and a finished puzzle needs a few things beyond that:
 * - a branch in `ParityFingerprint.body` for its state class (the `when` is over the sealed
 *   [PuzzleState], so the build fails until it is there);
 * - a branch in `StateSerializationTest.mutate` (likewise exhaustive);
 * - a parity pin test, so the board is proved identical on Android and in the browser;
 * - to match the others: a teacher, a walkthrough and a proof that its boards have the answer
 *   they claim. `docs/PUZZLE_STANDARDS.md` has the full list and a step-by-step recipe.
 *
 * [generate] **must** be a pure function of `(seed, difficulty)`. Daily puzzles are produced on the
 * device from the date, never fetched, so purity is what lets the entire back-catalogue be playable
 * offline and for free. It is the one rule the compiler cannot check.
 */
interface PuzzleType {

    /** Stable identifier. Saved progress keys off this, so never rename one in place. */
    val id: String

    val displayName: String

    /** One line, shown on the home card. */
    val tagline: String

    /**
     * How to play, as a short summary. Shown from the in-game "How to play" button — directly for a
     * puzzle with no [tutorial], and from inside the walkthrough for one that has. Nothing shows it
     * unasked: first-time players of a puzzle with a walkthrough are offered that instead, as one
     * passive line under the board, once.
     */
    val rules: List<String>

    /**
     * Keys the board answers (web, or a hardware keyboard), as short sentences; empty for none. Shown
     * as one "Keyboard" line under the rules, followed by the play screen's own shortcuts.
     */
    val keyboardHelp: List<String> get() = emptyList()

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
     *
     * The old hint: the move goes straight onto the board. Only used when [teach] returns null, so
     * a puzzle that has not adopted teaching keeps exactly this behaviour.
     */
    fun hint(state: PuzzleState): PuzzleState? = null

    /**
     * The next thing to learn on this board: a mistake of the player's to take back, or a step
     * they could have reasoned from what they can see. Null means "not adopted" (the screen falls
     * back to [hint]) or "nothing left" on a solved board.
     *
     * Must reason only from what the player can see. The stored answer may decide what counts as a
     * mistake, and may supply a last-resort square flagged [Deduction.fallback], but a step
     * presented as reasoning must never lean on a fact only the answer knows.
     */
    fun teach(state: PuzzleState): Deduction? = null

    /**
     * A walkthrough played on this puzzle's own [Board], or empty for none. The "How to play"
     * button opens it when present, and a first-time player is offered it once.
     */
    val tutorial: List<TutorialFrame> get() = emptyList()

    /**
     * [state] with whatever the player has only *pointed at* dropped: a selected cell, a half-made
     * pick. Everything done stays, move count included. Undo restores states through this, so it
     * never lands on a highlight left over from the move it took back. Default: nothing to drop.
     */
    fun withoutSelection(state: PuzzleState): PuzzleState = state

    /**
     * Whether [a] and [b] are the same board as far as Undo is concerned: they differ, if at all,
     * only in selection. Undo skips history entries that are the same board as the one on screen,
     * since stepping onto them would change nothing the player can see but a highlight.
     */
    fun sameBoard(a: PuzzleState, b: PuzzleState): Boolean = withoutSelection(a) == withoutSelection(b)

    /** Whether this puzzle can offer a deducible next step. Drives whether the Hint button appears. */
    val offersHints: Boolean get() = true
}
