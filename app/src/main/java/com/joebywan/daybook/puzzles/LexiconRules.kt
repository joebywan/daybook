package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import kotlinx.serialization.Serializable

/**
 * One guess's letters, marked. [CORRECT] is the right letter in the right place, [PRESENT] the
 * right letter in the wrong place, [ABSENT] a letter the word has no (more) of.
 */
object LexiconMark {
    const val ABSENT = 0
    const val PRESENT = 1
    const val CORRECT = 2
}

/**
 * Lexicon's board: a hidden word, the guesses so far, and the row being typed.
 *
 * [current] is the row so far, lowercase, left to right: a letter is typed at the end and taken back from the end, so it never has gaps.
 *
 * Saved games hold the words themselves, so a state written today loads whatever the lists do later.
 */
@Serializable
data class LexiconState(
    val length: Int,
    val maxGuesses: Int,
    val answer: String,
    val guesses: List<String> = emptyList(),
    val current: String = "",
    override val moves: Int = 0,
) : PuzzleState {

    override val solved: Boolean get() = guesses.lastOrNull() == answer
    override val failed: Boolean get() = !solved && guesses.size >= maxGuesses

    /** Whether the board still takes letters. */
    val open: Boolean get() = !solved && !failed

    fun marks(guess: String): List<Int> = LexiconRules.mark(guess, answer)

    /** Every submitted guess's marks, in order. */
    val allMarks: List<List<Int>> get() = guesses.map(::marks)

    fun withLetter(letter: Char): LexiconState =
        if (!open || current.length >= length) this else copy(current = current + letter, moves = moves + 1)

    fun withoutLetter(): LexiconState =
        if (!open || current.isEmpty()) this else copy(current = current.dropLast(1), moves = moves + 1)

    /** The row typed so far, whole, as a guess. Callers have already asked [LexiconRules.problem]. */
    fun submit(): LexiconState =
        if (!open || current.length != length) this
        else copy(guesses = guesses + current, current = "", moves = moves + 1)
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
     * possible wins every five-letter board in six and 98% in five, but only 92% of four-letter
     * boards in six and 96% in seven; one who also spends turns on words that cannot be the answer wins
     * them all. So four letters get a guess more, and are still the tier that asks the most.
     */
    fun shape(difficulty: Difficulty): Shape = when (difficulty) {
        Difficulty.STANDARD -> Shape(5, 6)
        Difficulty.HARD -> Shape(5, 5)
        Difficulty.EXPERT -> Shape(4, 7)
    }

    /** The board for [seed]: the answer is one index into the sorted list for the tier's length. */
    fun newBoard(seed: Long, difficulty: Difficulty): LexiconState {
        val shape = shape(difficulty)
        val answers = WordList.answers(shape.length)
        return LexiconState(shape.length, shape.guesses, answers[Rng(seed).nextInt(answers.size)])
    }

    /**
     * [guess] marked against [answer]. Greens first, then each remaining letter of the guess is
     * yellow only while the answer still has an unmatched copy of it, left to right: "eerie" against
     * "these" is yellow, grey, grey, grey, green, because the answer's other e is already matched.
     */
    fun mark(guess: String, answer: String): List<Int> {
        val n = guess.length
        val out = IntArray(n)
        val left = IntArray(26)
        for (i in 0 until n) {
            if (guess[i] == answer[i]) out[i] = LexiconMark.CORRECT else left[answer[i] - 'a']++
        }
        for (i in 0 until n) {
            if (out[i] == LexiconMark.CORRECT) continue
            val k = guess[i] - 'a'
            if (left[k] > 0) {
                left[k]--
                out[i] = LexiconMark.PRESENT
            }
        }
        return out.toList()
    }

    /** [mark] packed base 3, first slot lowest: cheap to count and compare across a whole list. */
    fun markCode(guess: String, answer: String): Int {
        val n = guess.length
        val left = IntArray(26)
        val out = IntArray(n)
        for (i in 0 until n) {
            if (guess[i] == answer[i]) out[i] = LexiconMark.CORRECT else left[answer[i] - 'a']++
        }
        var code = 0
        var weight = 1
        for (i in 0 until n) {
            var m = out[i]
            if (m != LexiconMark.CORRECT) {
                val k = guess[i] - 'a'
                if (left[k] > 0) {
                    left[k]--
                    m = LexiconMark.PRESENT
                }
            }
            code += m * weight
            weight *= 3
        }
        return code
    }

    /** Whether [word] would have drawn exactly these marks for every guess so far. */
    fun consistent(word: String, guesses: List<String>, marks: List<List<Int>>): Boolean =
        guesses.indices.all { mark(guesses[it], word) == marks[it] }

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
}
