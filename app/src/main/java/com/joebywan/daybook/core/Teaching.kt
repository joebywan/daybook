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
    applyTo: (PuzzleState) -> PuzzleState,
    reachedBy: (PuzzleState) -> Boolean,
) {
    /** The board after the move, made on the state given. "Show me" — never applied without that tap. */
    val apply = applyTo

    /** Whether the state given already contains this move's result, however the player got there. */
    val isReached = reachedBy
}

/**
 * A teacher's step that puts [digit] on square [cell] (Sudoku, Cages, Inequality, Regions), or, for a
 * mistake, takes the square's digit back ([digit] is then 0).
 */
class PlacementStep(
    val technique: String,
    val cell: Int,
    val digit: Int,
    val focus: Set<Int>,
    val cited: Set<Int>,
    val nudge: String,
    val explanation: String,
)

/**
 * Inequality's and Regions' honest fallback: [solution]'s digit in the first empty square of [cells], said
 * to come from the stored answer.
 */
fun answerFallback(technique: String, cells: List<Int>, solution: List<Int>): PlacementStep? {
    val i = cells.indices.firstOrNull { cells[it] == 0 } ?: return null
    val d = solution[i]
    return PlacementStep(
        technique, i, d, setOf(i), emptySet(),
        "Try this square.",
        "No short reason found: the stored answer has a $d here.",
    )
}

/** The one empty square of [members] whose mask in [m] has [d]; -1 when there are none or several, or one holds [d]. */
fun lonePlace(members: List<Int>, cells: List<Int>, m: IntArray, d: Int): Int {
    var at = -1
    for (i in members) {
        if (cells[i] == d) return -1
        if (cells[i] == 0 && has(m[i], d)) { if (at >= 0) return -1; at = i }
    }
    return at
}

/**
 * The [Deduction] for a placement on [state]: "Show me" places the digit and selects the square (one
 * state, so one undo entry, and the player sees where it went); a mistake is dealt with once that digit
 * is gone, whatever replaced it. [valueAt] reads a square; [place] writes one and selects it.
 */
inline fun <reified S : PuzzleState> placementDeduction(
    state: S,
    step: PlacementStep,
    mistake: Boolean,
    fallback: Boolean,
    targets: Set<Int> = setOf(step.cell),
    crossinline valueAt: (S, Int) -> Int,
    crossinline place: (S, Int, Int) -> S,
): Deduction {
    val cell = step.cell
    val digit = step.digit
    val wrong = valueAt(state, cell)
    return Deduction(
        technique = step.technique,
        nudge = step.nudge,
        explanation = step.explanation,
        focus = step.focus,
        cited = step.cited,
        targets = targets,
        mistake = mistake,
        fallback = fallback,
        applyTo = { now ->
            val t = now as S
            if (valueAt(t, cell) == digit) t else place(t, cell, digit)
        },
        reachedBy = { now ->
            val t = now as S
            if (mistake) valueAt(t, cell) != wrong else valueAt(t, cell) == digit
        },
    )
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

/** What a walkthrough frame makes of a state its board emits: see [TutorialFrame.steps]. */
enum class TutorialStep {
    /** Not what the frame asks for: not applied, and the frame's retry line is shown. */
    REJECT,
    /** On the way: applied to the board, and the frame waits for more. */
    PROGRESS,
    /** The move the frame asks for: applied, and the walkthrough moves on by itself. */
    DONE,
}

/**
 * A walkthrough move made of states in a fixed order (tap the square, then the digit; place it, then
 * clear it): [stages] test each state on the way, the last the move itself. A state stands at the last
 * stage it passes, so [steps] is PROGRESS on the way, DONE at the end and REJECT off the path, and
 * [stage] tells a frame's glow where the player has got to.
 */
class TutorialPath(private vararg val stages: (PuzzleState) -> Boolean) {
    /** The last stage [s] passes, -1 for none (the frame's start, or a state off the path). */
    fun stage(s: PuzzleState): Int = stages.indexOfLast { it(s) }

    val steps: (PuzzleState) -> TutorialStep = { s ->
        when (stage(s)) {
            -1 -> TutorialStep.REJECT
            stages.lastIndex -> TutorialStep.DONE
            else -> TutorialStep.PROGRESS
        }
    }
}

/**
 * One step of a walkthrough, played on the puzzle's real board.
 *
 * With neither [accepts] nor [steps] the frame is explanatory (a READ frame): the board is shown but
 * not playable and the player moves on with Next. Otherwise the board is live (a MOVE frame), and each
 * state it emits is judged: a rejected one is *not* applied and [retry] is shown instead, so a stray
 * tap cannot leave the walkthrough on a board its later frames were not written for. Once the move is
 * made the runner shows [done], holds a moment and moves on by itself, carrying [done] into the next
 * frame as a confirmation line; a move frame never needs Next.
 *
 * [accepts] takes one emitted state as the whole move. [steps] lets a move take several (select a
 * square, then a digit; type a word, then Enter): each [TutorialStep.PROGRESS] state is applied and
 * the frame waits, and a [TutorialStep.REJECT] leaves the progress made so far on the board. [steps]
 * is handed every state, including the frame's own start state after an undoing tap (a Backspace), so
 * it should call any state on the way to the move PROGRESS. [glow], when set, replaces [highlight]
 * with one read off the current board, so the glow can walk along with the progress.
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
    val steps: ((PuzzleState) -> TutorialStep)? = null,
    val glow: ((PuzzleState) -> BoardHighlight)? = null,
) {
    /** [steps], or [accepts] read as a one-state move; null on a READ or free-play frame. */
    val judge: ((PuzzleState) -> TutorialStep)? =
        steps ?: accepts?.let { a -> { next -> if (a(next)) TutorialStep.DONE else TutorialStep.REJECT } }
}
