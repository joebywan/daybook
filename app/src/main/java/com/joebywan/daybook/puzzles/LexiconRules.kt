package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.DailyCycle
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.SeedHash
import kotlinx.serialization.Serializable

/**
 * Lexicon's board: a hidden word, the guesses so far, and the row being typed.
 *
 * [current] is the row so far, lowercase, left to right: a letter is typed at the end and taken back from the end, so it never has gaps.
 *
 * Saved games hold the words themselves, so a state written today loads whatever the lists do later.
 *
 * [opener] picks the first-guess hint from [LexiconTeacher.openers], drawn from the seed apart from the answer, so the
 * opening clue changes from board to board and says nothing about the word. A save from before it was added loads as 0,
 * the one opener there was.
 */
@Serializable
data class LexiconState(
    override val length: Int,
    override val maxGuesses: Int,
    override val answer: String,
    override val guesses: List<String> = emptyList(),
    override val current: String = "",
    override val moves: Int = 0,
    val opener: Int = 0,
) : PuzzleState, GuessRows<LexiconState> {

    override val solved: Boolean get() = guesses.lastOrNull() == answer
    override val failed: Boolean get() = !solved && guesses.size >= maxGuesses

    override fun withRows(guesses: List<String>, current: String): LexiconState =
        copy(guesses = guesses, current = current, moves = moves + 1)
}

/**
 * Lexicon's rules, with no board and no screen: marking, what a row may be submitted as, and the word lists.
 * Everything here is a pure function of its arguments, so the teacher, the generator and the tests
 * share one definition of a mark.
 */
object LexiconRules {

    /** A tier's board: word length and guesses allowed. */
    class Shape(val length: Int, val guesses: Int)

    /**
     * Standard: five letters, six guesses. Hard: five letters, five. Expert: four letters, seven.
     *
     * Any word is a legal guess on every tier; there is no rule that forces a clue to be reused.
     * Four letters is the harder length, not the easier: a short word has far more look-alikes
     * (-ALE, -ILL, -ATE). Measured (`LexiconBalanceTest`), a player who always guesses a word still
     * possible wins every five-letter board in six and 98% in five, but only 94% of four-letter
     * boards in six and 98% in seven; one who also spends turns on words that cannot be the answer wins
     * them all. So four letters get a guess more, and are still the tier that asks the most.
     */
    fun shape(difficulty: Difficulty): Shape = when (difficulty) {
        Difficulty.STANDARD -> Shape(5, 6)
        Difficulty.HARD -> Shape(5, 5)
        Difficulty.EXPERT -> Shape(4, 7)
    }

    /**
     * The board for [seed]: the answer is one index into the sorted list for the tier's length. A daily seed from
     * [SeedHash.LEXICON_CYCLE_FROM] on carries its day, and the day walks [cycle]; any other seed draws the index.
     */
    fun newBoard(seed: Long, difficulty: Difficulty): LexiconState {
        val shape = shape(difficulty)
        val answers = WordList.answers(shape.length)
        val day = SeedHash.lexiconCycleDay(seed)
        val index = if (day == null) Rng(seed).nextInt(answers.size) else cycle(day, difficulty)
        val opener = Rng(seed xor OPENER_SALT).nextInt(LexiconTeacher.openers(shape.length).size)
        return LexiconState(shape.length, shape.guesses, answers[index], opener = opener)
    }

    private const val OPENER_SALT = 0x4F70656E6572L // "Opener"

    /**
     * The answer index of cycle day [day] on [difficulty]: round `day / n` of the tier's answer list (n words) is one
     * shuffle of every index, so no word comes back within a round. Round 0 puts the words the tier's length drew on
     * hashed daily seeds (2026-01-01 to the cutover, every tier of that length) at the back of the shuffle, so the
     * first cycle does not repeat the year before it either. Only the cycle's own boundary can bring a word back early.
     */
    fun cycle(day: Long, difficulty: Difficulty): Int {
        val length = shape(difficulty).length
        val n = WordList.answers(length).size
        return DailyCycle.pick(day, n, CYCLE_SALT, difficulty) {
            val used = BooleanArray(n)
            for (tier in Difficulty.entries) {
                if (shape(tier).length != length) continue
                for (d in SeedHash.epochDay(2026, 1, 1) until SeedHash.LEXICON_CYCLE_FROM) {
                    used[Rng(SeedHash.daily(d, "words", tier)).nextInt(n)] = true
                }
            }
            used
        }
    }

    private const val CYCLE_SALT = 0x4379636C65L // "Cycle"

    /**
     * Why the row in [state] cannot be submitted, or null if it can: too short, or not a word. Shown
     * briefly over the board; nothing is spent.
     */
    fun problem(state: LexiconState): String? {
        if (state.current.length < state.length) return "Not enough letters"
        if (!WordList.isWord(state.current)) return "Not in the word list"
        return null
    }
}

/**
 * The two lists per word length. Both are sorted and unique ([LexiconWords] is generated that way),
 * so the daily answer is an index into a fixed order and a guess is a binary search: no hash
 * container anywhere near the seed, which is what keeps Android and the browser on the same word.
 */
object WordList {

    fun answers(length: Int): List<String> = when (length) {
        4 -> LexiconWords.ANSWERS_4
        5 -> LexiconWords.ANSWERS_5
        else -> error("no $length-letter words")
    }

    private fun guesses(length: Int): List<String> = when (length) {
        4 -> LexiconWords.GUESSES_4
        5 -> LexiconWords.GUESSES_5
        else -> emptyList()
    }

    /** Whether [word] is accepted as a guess: every answer, and many more besides. */
    fun isWord(word: String): Boolean = guesses(word.length).binarySearch(word) >= 0

    // Probed by word, never iterated, so its order cannot reach a seed.
    private val clues: Map<String, String> by lazy {
        LexiconClueText.ALL.split("\n").associate { it.substringBefore(": ") to it.substringAfter(": ") }
    }

    /** A crossword-style clue for an answer-list [word]; every answer has one (`LexiconCluesTest`). */
    fun clue(word: String): String = clues.getValue(word)
}
