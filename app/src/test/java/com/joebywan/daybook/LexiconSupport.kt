package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.SeedHash
import com.joebywan.daybook.puzzles.LexiconRules
import com.joebywan.daybook.puzzles.LexiconState
import com.joebywan.daybook.puzzles.LexiconTeacher
import com.joebywan.daybook.puzzles.LexiconWords
import com.joebywan.daybook.puzzles.WordList

/**
 * What the Lexicon tests share. Everything here is written on a different principle from the code
 * under test, so the two cannot share a blind spot: the reference marker removes letters from a
 * list where the real one counts them in an array, and candidates are filtered with that marker.
 */
internal object LexiconSupport {

    /** [guess] marked against [answer] by taking letters out of the answer as they are matched. */
    fun refMark(guess: String, answer: String): List<Int> {
        val out = MutableList(guess.length) { 0 }
        val unmatched = answer.filterIndexed { i, c -> guess[i] != c }.toMutableList()
        for (i in guess.indices) if (guess[i] == answer[i]) out[i] = 2
        for (i in guess.indices) {
            if (out[i] == 2) continue
            if (unmatched.remove(guess[i])) out[i] = 1
        }
        return out
    }

    /** Every answer-list word that would have drawn these marks, by the reference marker. */
    fun refCandidates(length: Int, guesses: List<String>, marks: List<List<Int>>): List<String> =
        WordList.answers(length).filter { w -> guesses.indices.all { refMark(guesses[it], w) == marks[it] } }

    /** Every accepted guess of this length, for claims that must hold beyond the answer list. */
    fun guessList(length: Int): List<String> = if (length == 4) LexiconWords.GUESSES_4 else LexiconWords.GUESSES_5

    fun dailySeed(day: Int, difficulty: Difficulty): Long =
        SeedHash.daily(DailySeed.EPOCH.toEpochDays() + day, "words", difficulty)

    fun board(day: Int, difficulty: Difficulty): LexiconState =
        LexiconRules.newBoard(dailySeed(day, difficulty), difficulty)

    class Walk(val answer: String, val guesses: List<String>, val steps: List<LexiconTeacher.Step>, val solved: Boolean)

    /**
     * A player who does exactly what the hints say, typing the word each asks for and submitting it,
     * until the word is found or the guesses run out. Every guess is checked to be a word on
     * the way, so a hint Enter would refuse fails here.
     */
    fun walkByHints(s: LexiconState): Walk {
        val guesses = ArrayList<String>()
        val marks = ArrayList<List<Int>>()
        val steps = ArrayList<LexiconTeacher.Step>()
        var solved = false
        while (guesses.size < s.maxGuesses && !solved) {
            val step = LexiconTeacher.teach(s.length, guesses, marks, "")
                ?: error("no hint for ${s.answer} after $guesses")
            steps += step
            val word = when (val m = step.move) {
                is LexiconTeacher.Move.Open -> m.word
                is LexiconTeacher.Move.Fill -> m.word
                is LexiconTeacher.Move.Pin -> m.word
                else -> error("a hint on an empty row asked for $m")
            }
            check(WordList.isWord(word)) { "$word is not a word" }
            guesses += word
            marks += refMark(word, s.answer)
            solved = word == s.answer
        }
        return Walk(s.answer, guesses, steps, solved)
    }
}
