package com.joebywan.daybook.puzzles

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.GuessMarks
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.TutorialStep

/**
 * Lexicon: Mastermind for words.
 *
 * Break a hidden word from scored guesses. A guess must be a real word; each letter comes back
 * green (right letter, right place), yellow (in the word, elsewhere) or grey (not in it, or no more
 * of it). The keys of the on-screen keyboard keep the best thing known about each letter.
 *
 * Like Tower there is nothing to prove about a board: the word is one pick from a fixed list, and a
 * guess that cannot be it is still a fair probe. The rules are in [LexiconRules], the lists in
 * [WordList], the hints in [LexiconTeacher]; this file is the board.
 */
object Lexicon : PuzzleType {

    override val id = "words"
    override val displayName = "Lexicon"
    override val tagline = "Find the hidden word"
    override val accent = 0xFF278E8E
    override val rules = listOf(
        "A word is hidden. Type a real word and press Enter to guess it.",
        "Green: the right letter in the right place.",
        "Yellow: the letter is in the word, but somewhere else.",
        "Grey: the word has no (more) of that letter.",
        "A letter is marked only as many times as the word holds it, so a second E can be grey while the first is yellow.",
        "The keys keep the best clue for each letter.",
        "Four-letter words have more look-alikes than five.",
    )

    override val keyboardHelp = listOf(
        "Type letters, Backspace deletes, Enter submits.",
    )

    override fun generate(seed: Long, difficulty: Difficulty) = LexiconRules.newBoard(seed, difficulty)

    // ---- the home tile ----------------------------------------------------------------------------

    /** Three scored rows, letters left out: the colours are the whole of what reads at 80dp. */
    private val previewRows = listOf(
        listOf(GuessMarks.ABSENT, GuessMarks.PRESENT, GuessMarks.ABSENT, GuessMarks.ABSENT, GuessMarks.PRESENT),
        listOf(GuessMarks.CORRECT, GuessMarks.ABSENT, GuessMarks.PRESENT, GuessMarks.CORRECT, GuessMarks.ABSENT),
        listOf(GuessMarks.CORRECT, GuessMarks.CORRECT, GuessMarks.CORRECT, GuessMarks.CORRECT, GuessMarks.CORRECT),
    )

    @Composable
    override fun Preview(modifier: Modifier) = GuessBoard.PreviewRows(previewRows, modifier)

    // ---- the walkthrough ---------------------------------------------------------------------------

    /**
     * The walkthrough's word: COAT, four letters. One guess is already on the board.
     *
     * ```
     * guess 1  tape   yellow yellow grey grey   -> t and a are in it, not there; p and e are out
     * guess 2  boat   grey green green green    -> made in the walkthrough: COAT, GOAT or MOAT
     * guess 3  clog   green grey yellow grey    -> made in the walkthrough: it can't be the answer,
     *                                              but C and G tell the three apart
     * ```
     *
     * Guessing COAT, GOAT and MOAT in turn could take three guesses; one guess that cannot win settles
     * it, which is the lesson of four-letter words (`LexiconBalanceTest`). The free-play frame hands
     * the board after CLOG to the hints.
     */
    internal const val TUTORIAL_ANSWER = "coat"
    internal val TUTORIAL_GUESSES = listOf("tape", "boat", "clog")

    private fun tutorialBoard(guesses: Int, current: String = "") = LexiconState(
        length = 4,
        maxGuesses = 7, // Expert's shape: no tier has four letters and six guesses
        answer = TUTORIAL_ANSWER,
        guesses = TUTORIAL_GUESSES.take(guesses),
        current = current,
    )

    /**
     * Guess [n] typed a letter at a time and then scored, in one frame: any start of the word is
     * progress (a Backspace included), the word scored is the move, and anything else is refused.
     */
    private fun typeGuess(n: Int): (PuzzleState) -> TutorialStep = { next ->
        val word = TUTORIAL_GUESSES[n]
        when {
            next !is LexiconState -> TutorialStep.REJECT
            next.guesses == TUTORIAL_GUESSES.take(n + 1) && next.current.isEmpty() -> TutorialStep.DONE
            next.guesses == TUTORIAL_GUESSES.take(n) && word.startsWith(next.current) -> TutorialStep.PROGRESS
            else -> TutorialStep.REJECT
        }
    }

    /** The next key to tap glows: the next letter of guess [n], then Enter. */
    private fun keyGlow(n: Int): (PuzzleState) -> BoardHighlight = { s ->
        val typed = (s as LexiconState).current
        val word = TUTORIAL_GUESSES[n]
        BoardHighlight(
            strong = setOf(if (typed.length < word.length) LexiconTeacher.key(word[typed.length]) else LexiconTeacher.ENTER),
        )
    }

    override val tutorial: List<TutorialFrame> by lazy {
        fun tiles(row: Int, vararg slots: Int) = slots.map { LexiconTeacher.tile(4, row, it) }.toSet()
        listOf(
            TutorialFrame(
                state = tutorialBoard(1),
                caption = "A word is hidden, here one of four letters. Every guess must be a real " +
                    "word, and each letter comes back marked. Green means right letter, right place.",
                highlight = BoardHighlight(soft = tiles(0, 0, 1, 2, 3)),
            ),
            TutorialFrame(
                state = tutorialBoard(1),
                caption = "Yellow means the letter is in the word, but not there: T and A are in it. " +
                    "Grey means the word has none of it: P and E are out.",
                highlight = BoardHighlight(strong = tiles(0, 0, 1), soft = tiles(0, 2, 3)),
            ),
            TutorialFrame(
                state = tutorialBoard(1),
                caption = "Use what you know: T and A are in the word, so move them. BOAT puts them in new " +
                    "places. Type BOAT on the keyboard, then tap Enter to score it.",
                glow = keyGlow(1),
                steps = typeGuess(1),
                retry = "Tap the glowing key.",
                done = "Scored: the word ends in OAT.",
            ),
            TutorialFrame(
                state = tutorialBoard(2),
                caption = "COAT, GOAT or MOAT: guess them in turn and it could take three. CLOG can't be the " +
                    "word, but its C and G tell all three apart. Type CLOG and score it.",
                glow = keyGlow(2),
                steps = typeGuess(2),
                retry = "Tap the glowing key.",
                done = "C is green and G grey: only COAT is left.",
            ),
            TutorialFrame(
                state = tutorialBoard(3),
                caption = "Your turn: find the word. Stuck? Hint shows you why.",
                freePlay = true,
                done = "Found it. That's all there is to it.",
            ),
        )
    }

    // ---- teaching ---------------------------------------------------------------------------------

    /**
     * A mistake to take back or a step to reason out; see [LexiconTeacher]. The teacher is handed
     * the guesses and their marks, never the word.
     */
    override fun teach(state: PuzzleState): Deduction? = LexiconTeacher.deduction(state as LexiconState)

    // ---- drawing -----------------------------------------------------------------------------------

    private val keyRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) = GuessBoard.Board(
        state as LexiconState, onState, interactive, accent,
        alphabet = LexiconTeacher.vocab.alphabet,
        keyRows = keyRows,
        label = { it.uppercase() },
        keyAction = ::lexiconKeyAction,
        problem = LexiconRules::problem,
        lost = { "Out of guesses. The word was ${it.answer.uppercase()}." },
    )
}
