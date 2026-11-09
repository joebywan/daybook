package com.joebywan.daybook.puzzles

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.GuessMarks
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.TutorialFrame

/**
 * Equation: Lexicon with sums. A true sum such as `12+35=47` is hidden; every guess must be a true sum of the same
 * length, and each symbol comes back marked as Lexicon's letters are. The rules and the lists are [EquationLogic], the
 * hints [EquationTeacher] (Lexicon's ladder), the board Lexicon's ([GuessBoard]) with a keyboard of digits and signs.
 *
 * Nothing to prove about a board: the answer is one index into the sorted list of every answer of the tier's length,
 * enumerated on the device, and any true sum is a fair probe.
 */
object Equation : PuzzleType {

    override val id = "equation"
    override val displayName = "Equation"
    override val tagline = "Find the hidden equation"
    override val accent = 0xFFD062BA
    override val rules = listOf(
        "An equation such as 12+35=47 is hidden. Type a true one and press Enter to guess it.",
        "Green: the right symbol in the right place.",
        "Yellow: the symbol is in the equation, but somewhere else.",
        "Grey: the equation has no (more) of that symbol.",
        "× and ÷ come before + and −, and every ÷ comes out whole.",
        "No number starts with 0. The hidden one has no lone 0, and never × or ÷ by 1.",
        "The keys keep the best clue for each symbol.",
    )

    override val keyboardHelp = listOf(
        "Type digits, + − * / (or X) and =; Backspace deletes, Enter submits.",
    )

    override fun generate(seed: Long, difficulty: Difficulty) = EquationLogic.newBoard(seed, difficulty)

    // ---- the home tile ----------------------------------------------------------------------------

    /** Three scored rows, the last one found: 9×6=54 under two probes, marked by the real marker. */
    private val previewGuesses = listOf("4*7=28", "9*4=36", "9*6=54")
    private val previewRows = previewGuesses.map { GuessMarks.mark(it, previewGuesses.last()) }
    private val previewSymbols = previewGuesses.map(EquationTeacher::shown)

    @Composable
    override fun Preview(modifier: Modifier) = GuessBoard.PreviewRows(previewRows, modifier, previewSymbols)

    // ---- the walkthrough ---------------------------------------------------------------------------

    /**
     * The walkthrough's sum: 5+8=13, six symbols. One guess is already on the board.
     *
     * ```
     * guess 1  42÷7=6   grey grey grey grey yellow grey  -> = is in it, not fifth; 4 2 ÷ 7 6 are out
     * guess 2  9+9=18   grey green grey green green yellow  -> made in the walkthrough: 3+8=11, 5+8=13, 8+3=11, 8+5=13
     * guess 3  3×5=15   yellow grey yellow green green grey -> made in the walkthrough: it can't be the answer, but
     *                                                        its marks tell the four apart
     * ```
     *
     * Lexicon's lesson with sums: guessing the four in turn could take four guesses; one that cannot win settles it. The
     * free-play frame hands the board after 3×5=15 to the hints.
     */
    internal const val TUTORIAL_ANSWER = "5+8=13"
    internal val TUTORIAL_GUESSES = listOf("42/7=6", "9+9=18", "3*5=15")

    private fun tutorialBoard(guesses: Int) = EquationState(
        length = 6,
        maxGuesses = 6, // Standard's shape
        answer = TUTORIAL_ANSWER,
        guesses = TUTORIAL_GUESSES.take(guesses),
    )

    override val tutorial: List<TutorialFrame> by lazy {
        fun tiles(row: Int, vararg slots: Int) = slots.map { GuessTeacher.tile(6, row, it) }.toSet()
        listOf(
            TutorialFrame(
                state = tutorialBoard(1),
                caption = "An equation is hidden, here six symbols long. Every guess must be a true equation, " +
                    "and each symbol comes back marked.",
                highlight = BoardHighlight(soft = tiles(0, 0, 1, 2, 3, 4, 5)),
            ),
            TutorialFrame(
                state = tutorialBoard(1),
                caption = "Yellow means the symbol is in it, but not there: = is in it, not fifth. " +
                    "Grey means it has none of it: 4, 2, ÷, 7 and 6 are out.",
                highlight = BoardHighlight(strong = tiles(0, 4), soft = tiles(0, 0, 1, 2, 3, 5)),
            ),
            TutorialFrame(
                state = tutorialBoard(1),
                caption = "Use what you know: 9+9=18 is true, moves the = and tries new symbols. " +
                    "Type it on the keys, then tap Enter to score it.",
                glow = GuessTutorial.keyGlow(TUTORIAL_GUESSES, 1, EquationTeacher::key),
                steps = GuessTutorial.typeGuess(TUTORIAL_GUESSES, 1),
                retry = "Tap the glowing key.",
                done = "Green is the right symbol in the right place: +, = and 1.",
            ),
            TutorialFrame(
                state = tutorialBoard(2),
                caption = "3+8=11, 5+8=13, 8+3=11 or 8+5=13: guess them in turn and it could take four. " +
                    "3×5=15 can't be it, but its marks tell all four apart. Type it and score it.",
                glow = GuessTutorial.keyGlow(TUTORIAL_GUESSES, 2, EquationTeacher::key),
                steps = GuessTutorial.typeGuess(TUTORIAL_GUESSES, 2),
                retry = "Tap the glowing key.",
                done = "3 is not first and 5 is not third: only 5+8=13 is left.",
            ),
            TutorialFrame(
                state = tutorialBoard(3),
                caption = "Your turn: find the equation. Stuck? Hint shows you why.",
                freePlay = true,
                done = "Found it. That's all there is to it.",
            ),
        )
    }

    // ---- teaching ---------------------------------------------------------------------------------

    /** A mistake to take back or a step to reason out; see [EquationTeacher]. Handed the guesses and marks, never the sum. */
    override fun teach(state: PuzzleState): Deduction? = EquationTeacher.deduction(state as EquationState)

    // ---- drawing -----------------------------------------------------------------------------------

    /** Digits over the signs; the last row has Del and Enter at its ends. */
    private val keyRows = listOf("1234567890", "+-*/=")

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) = GuessBoard.Board(
        state as EquationState, onState, interactive, accent,
        alphabet = EquationLogic.ALPHABET,
        keyRows = keyRows,
        label = EquationTeacher::shown,
        keyAction = ::equationKeyAction,
        shift = true,
        problem = { EquationLogic.problem(it.current, it.length) },
        lost = { "Out of guesses. It was ${EquationTeacher.shown(it.answer)}." },
    )
}
