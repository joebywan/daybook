package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.GuessMarks
import com.joebywan.daybook.core.TutorialStep

/**
 * A board of guesses at a hidden row of symbols, each guess marked by [GuessMarks]: Lexicon's words and Equation's
 * sums. The state keeps the guesses and the row being typed; everything else (marks, whether it still takes symbols)
 * is derived here, so the two puzzles cannot drift apart. [S] is the state's own type, so the mutators return it.
 *
 * [current] is the row so far, left to right: a symbol is typed at the end and taken back from the end, so it never
 * has gaps.
 */
interface GuessRows<S : GuessRows<S>> {
    val length: Int
    val maxGuesses: Int
    val answer: String
    val guesses: List<String>
    val current: String
    val solved: Boolean
    val failed: Boolean

    /** The same board with these rows, one move on. */
    fun withRows(guesses: List<String>, current: String): S

    /** Whether the board still takes symbols. */
    val open: Boolean get() = !solved && !failed

    fun marks(guess: String): List<Int> = GuessMarks.mark(guess, answer)

    /** Every submitted guess's marks, in order. */
    val allMarks: List<List<Int>> get() = guesses.map(::marks)

    fun withLetter(letter: Char): S =
        if (!open || current.length >= length) self() else withRows(guesses, current + letter)

    fun withoutLetter(): S =
        if (!open || current.isEmpty()) self() else withRows(guesses, current.dropLast(1))

    /** The row typed so far, whole, as a guess. Callers have already asked whether the row may be submitted. */
    fun submit(): S =
        if (!open || current.length != length) self() else withRows(guesses + current, "")

    @Suppress("UNCHECKED_CAST")
    private fun self(): S = this as S
}

/** What a physical key asks of a guess board. No state, no focus: each puzzle maps its keys to one of these. */
sealed interface GuessKeyAction {
    /** A symbol to type (a letter, a digit, a sign). */
    data class Letter(val letter: Char) : GuessKeyAction

    /** Enter: submit the row, or say why it cannot be. */
    data object Enter : GuessKeyAction

    /** Backspace or Delete: take the last symbol back. */
    data object Backspace : GuessKeyAction
}

/** A guess game's walkthrough frames that have the player type a guess: Lexicon's and Equation's. */
object GuessTutorial {
    /**
     * Guess [n] of [guesses] typed a symbol at a time and then scored, in one frame: any start of the row is progress
     * (a Backspace included), the row scored is the move, and anything else is refused.
     */
    fun typeGuess(guesses: List<String>, n: Int): (PuzzleState) -> TutorialStep = { next ->
        val word = guesses[n]
        when {
            next !is GuessRows<*> -> TutorialStep.REJECT
            next.guesses == guesses.take(n + 1) && next.current.isEmpty() -> TutorialStep.DONE
            next.guesses == guesses.take(n) && word.startsWith(next.current) -> TutorialStep.PROGRESS
            else -> TutorialStep.REJECT
        }
    }

    /** The next key to tap glows: the next symbol of guess [n] ([key] numbers it), then Enter. */
    fun keyGlow(guesses: List<String>, n: Int, key: (Char) -> Int): (PuzzleState) -> BoardHighlight = { s ->
        val typed = (s as GuessRows<*>).current
        val word = guesses[n]
        BoardHighlight(strong = setOf(if (typed.length < word.length) key(word[typed.length]) else GuessTeacher.ENTER))
    }
}
