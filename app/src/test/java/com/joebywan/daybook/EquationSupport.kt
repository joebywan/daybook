package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.EquationLogic
import com.joebywan.daybook.puzzles.EquationState
import com.joebywan.daybook.puzzles.EquationTeacher
import com.joebywan.daybook.puzzles.GuessTeacher

/**
 * What the Equation tests share. Marks are read with [LexiconSupport.refMark] (symbols taken out of a list as they are
 * matched, not the shared marker's scan), so the two cannot share a blind spot.
 */
internal object EquationSupport {

    fun refCandidates(length: Int, guesses: List<String>, marks: List<List<Int>>): List<String> =
        EquationLogic.answers(length).filter { w -> guesses.indices.all { LexiconSupport.refMark(guesses[it], w) == marks[it] } }

    fun board(day: Int, difficulty: Difficulty): EquationState =
        EquationLogic.newBoard(daySeed(day, "equation", difficulty), difficulty)

    class Walk(val answer: String, val guesses: List<String>, val steps: List<GuessTeacher.Step>, val solved: Boolean)

    /**
     * A player who does exactly what the hints say, typing the sum each asks for and submitting it, until the sum is
     * found or the guesses run out. Every guess is checked to be a true sum by the oracle on the way.
     */
    fun walkByHints(s: EquationState): Walk {
        val guesses = ArrayList<String>()
        val marks = ArrayList<List<Int>>()
        val steps = ArrayList<GuessTeacher.Step>()
        var solved = false
        while (guesses.size < s.maxGuesses && !solved) {
            val step = EquationTeacher.teach(s.length, guesses, marks, "", s.opener)
                ?: error("no hint for ${s.answer} after $guesses")
            steps += step
            val word = when (val m = step.move) {
                is GuessTeacher.Move.Open -> m.word
                is GuessTeacher.Move.Fill -> m.word
                is GuessTeacher.Move.Pin -> m.word
                else -> error("a hint on an empty row asked for $m")
            }
            check(EquationOracle.isTrue(word)) { "$word is not a true sum" }
            guesses += word
            marks += LexiconSupport.refMark(word, s.answer)
            solved = word == s.answer
        }
        return Walk(s.answer, guesses, steps, solved)
    }
}
