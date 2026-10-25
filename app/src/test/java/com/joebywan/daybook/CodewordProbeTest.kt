package com.joebywan.daybook

import com.joebywan.daybook.puzzles.Codeword
import com.joebywan.daybook.puzzles.CodewordLogic
import com.joebywan.daybook.puzzles.CodewordState
import com.joebywan.daybook.puzzles.CodewordTeacher
import org.junit.Test

class CodewordProbeTest {
    @Test
    fun probe() {
        var s: CodewordState = Codeword.tutorialBoard()
        println("PROBE solve ${CodewordLogic.solve(5, s.numbers, s.letters)} oracle ${CodewordOracle.count(5, s.numbers, s.letters)}")
        while (!s.solved) {
            val shape = CodewordTeacher.Shape(5, s.numbers, s.letters.size)
            val hits = CodewordTeacher.findSteps(shape, s.letters)
            for (h in hits) println("PROBE   hit ${h.technique} n=${h.number + 1} ${'A' + h.letter} run=${h.run} words=${h.words.size}")
            val step = CodewordTeacher.teach(s)!!
            println("PROBE ${step.technique} ${step.number + 1}=${'A' + step.letter}: ${step.nudge} / ${step.explanation}")
            s = s.withLetter(step.number + 1, step.letter)
        }
    }
}
