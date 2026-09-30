package com.joebywan.daybook.core

import androidx.compose.runtime.compositionLocalOf
import com.joebywan.daybook.puzzles.PuzzleState

/**
 * One step of reasoning a player could have made from what is on their board, or one of their own
 * moves that needs taking back.
 *
 * A hint used to be "here is your answer": the square came straight out of the stored solution and
 * went onto the board. That fills the square without showing why, so the next time the player is
 * stuck in the same place they are just as stuck. A deduction carries the *why* as well as the
 * *what*, and the play screen shows it in two taps — [nudge] with [focus] glowing, then
 * [explanation] with [cited] marked — and then waits for the player to make the move themselves.
 *
 * Cell indices mean whatever the puzzle's own `Board` means by them; nothing outside the puzzle
 * interprets them, it only hands them back through [LocalBoardHighlight].
 *
 * The move is held as functions rather than as a finished board because the player keeps playing
 * while a hint is open: "Show me" has to land on the board as it is *then*, and "have they done it
 * yet" has to be asked of every board they produce, including ones that reached the same result a
 * different way (Kings counts a square a new king rules out as crossed).
 */
class Deduction(
    /** The named technique, for tests and coverage counts. Not shown to the player. */
    val technique: String,
    /** One short line for the first tap: where to look, not what to do. */
    val nudge: String,
    /** The reasoning, in a sentence or two, for the second tap. */
    val explanation: String,
    /** Cells to look at. Glow on the first tap. */
    val focus: Set<Int>,
    /** Cells the reasoning leans on. Marked, more quietly, on the second tap. */
    val cited: Set<Int>,
    /** Cells the move changes. Glow on the second tap. */
    val targets: Set<Int>,
    /** A move of the player's that is wrong, rather than a new step. Drawn in the error colour. */
    val mistake: Boolean = false,
    /**
     * Nothing short enough to explain applied, so this points at a square from the stored answer.
     * Kept distinguishable so a test can count how often players would see it — measure, don't
     * assume.
     */
    val fallback: Boolean = false,
    private val applyTo: (PuzzleState) -> PuzzleState,
    private val reachedBy: (PuzzleState) -> Boolean,
) {
    /** The board after the move, made on [state]. "Show me" — never applied without that tap. */
    fun apply(state: PuzzleState): PuzzleState = applyTo(state)

    /** Whether [state] already contains this move's result, however the player got there. */
    fun isReached(state: PuzzleState): Boolean = reachedBy(state)
}

/**
 * Cells a board should draw attention to. [strong] glows; [soft] is marked more quietly; every
 * other cell is dimmed while anything is highlighted, which is what makes a glow findable on a
 * busy 9x9 board. [warning] draws the glow in the error colour, for a mistake.
 */
data class BoardHighlight(
    val strong: Set<Int> = emptySet(),
    val soft: Set<Int> = emptySet(),
    val warning: Boolean = false,
) {
    val isEmpty: Boolean get() = strong.isEmpty() && soft.isEmpty()

    companion object {
        val None = BoardHighlight()
    }
}

/**
 * How a board learns what to highlight. A CompositionLocal rather than a parameter on
 * [PuzzleType.Board] so that adopting it is one board at a time: a board that never reads this
 * compiles and behaves exactly as before, and nobody porting a board elsewhere has to touch a
 * signature that eleven files implement.
 */
val LocalBoardHighlight = compositionLocalOf { BoardHighlight.None }

/**
 * One step of a walkthrough, played on the puzzle's real board.
 *
 * With [accepts] null the frame is explanatory: the board is shown but not playable and the player
 * moves on with Next. Otherwise the board is live, and each state it emits is offered to [accepts];
 * a state it rejects is *not* applied and [retry] is shown instead, so a stray tap cannot leave the
 * walkthrough on a board its later frames were not written for. [done] replaces the caption once
 * the move is made.
 *
 * With [freePlay] set, every move is applied, the puzzle's hints are available, and the frame ends
 * when the board is solved — the "your turn" frame at the end.
 */
class TutorialFrame(
    val state: PuzzleState,
    val caption: String,
    val highlight: BoardHighlight = BoardHighlight.None,
    val accepts: ((PuzzleState) -> Boolean)? = null,
    val retry: String = "Try the glowing square.",
    val done: String = "That's it.",
    val freePlay: Boolean = false,
)
