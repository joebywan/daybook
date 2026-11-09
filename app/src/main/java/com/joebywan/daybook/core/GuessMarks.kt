package com.joebywan.daybook.core

/**
 * How a guess at a hidden row is marked, over any alphabet: Lexicon's letters and Equation's digits and signs share
 * this one definition. [CORRECT] is the right symbol in the right place, [PRESENT] the right symbol in the wrong place,
 * [ABSENT] a symbol the answer has no (more) of. Pure functions of their arguments, so teachers, generators and tests
 * share one definition of a mark.
 */
object GuessMarks {
    const val ABSENT = 0
    const val PRESENT = 1
    const val CORRECT = 2

    /**
     * Where a marked row's note sits in the phrase (0 to 7): a symbol in place counts two, one elsewhere one, over a row
     * of [length], so a row that landed more sounds higher. [score] is the row's marks added up. Read from the marks the
     * row shows, so it tells nothing the row does not; Codebreaker's pegs use it too.
     */
    fun step(score: Int, length: Int): Int = (score * (Tones.Kind.STEP.count - 1) / (2 * length)).coerceIn(0, Tones.Kind.STEP.count - 1)

    /**
     * [guess] marked against [answer]. Greens first, then each remaining symbol of the guess is yellow only while the
     * answer still has an unmatched copy of it, left to right: "eerie" against "these" is yellow, grey, grey, grey,
     * green, because the answer's other e is already matched.
     */
    fun mark(guess: String, answer: String): List<Int> {
        var code = markCode(guess, answer)
        return List(guess.length) { (code % 3).also { code /= 3 } }
    }

    /**
     * [mark] packed base 3, first slot lowest: cheap to count and compare across a whole list. Each symbol left over is
     * matched against the first unmatched copy in the answer, so any alphabet works and no table is indexed by symbol.
     */
    fun markCode(guess: String, answer: String): Int {
        val n = guess.length
        val used = BooleanArray(n)
        for (i in 0 until n) if (guess[i] == answer[i]) used[i] = true
        var code = 0
        var weight = 1
        for (i in 0 until n) {
            val g = guess[i]
            var m = ABSENT
            if (g == answer[i]) {
                m = CORRECT
            } else {
                for (j in 0 until n) {
                    if (!used[j] && answer[j] == g) {
                        used[j] = true
                        m = PRESENT
                        break
                    }
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
}
