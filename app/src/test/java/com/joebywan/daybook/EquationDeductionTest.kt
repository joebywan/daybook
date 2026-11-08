package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.GuessMarks
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.TutorialStep
import com.joebywan.daybook.puzzles.Equation
import com.joebywan.daybook.puzzles.EquationLogic
import com.joebywan.daybook.puzzles.EquationState
import com.joebywan.daybook.puzzles.EquationTeacher
import com.joebywan.daybook.puzzles.GuessTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The glue between Equation's teacher and the play screen, and the walkthrough frame by frame. */
class EquationDeductionTest {

    @Test
    fun `a hint's move is made by Show me and seen as done by the board it makes`() {
        for (tier in Difficulty.entries) for (day in 0 until 5) {
            var s = EquationSupport.board(day, tier)
            repeat(s.maxGuesses) {
                if (!s.open) return@repeat
                val d = Equation.teach(s)!!
                assertFalse(d.isReached(s))
                val shown = d.apply(s) as EquationState
                assertTrue("${d.technique} changed nothing", shown != s)
                assertTrue("${d.technique} is not done after Show me", d.isReached(shown))
                assertTrue(d.explanation.isNotBlank() && d.nudge.isNotBlank())
                s = shown.submit()
            }
            assertTrue("${tier.name}: hints should have walked the board to its end", !s.open)
            assertNull(Equation.teach(s))
        }
    }

    @Test
    fun `a mistake is drawn as one and goes away when the row is taken back`() {
        val s = EquationState(6, 6, "5+8=13", listOf("42/7=6"), current = "5+8=14")
        val d = Equation.teach(s)!!
        assertTrue(d.mistake)
        assertFalse(d.isReached(s))
        assertTrue(d.isReached(s.copy(current = "5+8=1")))
        assertEquals("5+8=1", (d.apply(s) as EquationState).current)
    }

    @Test
    fun `the walkthrough is playable frame by frame and ends in a board the hints can finish`() {
        val frames = Equation.tutorial
        assertEquals(5, frames.size)
        assertEquals("Next-only frames", listOf(0, 1), frames.indices.filter { frames[it].judge == null && !frames[it].freePlay })
        val moves = ArrayDeque(Equation.TUTORIAL_GUESSES.drop(1))
        var board: EquationState? = null
        for (frame in frames) {
            val start = frame.state as EquationState
            board?.let { assertEquals("each frame starts where the last ended", it.copy(moves = 0), start) }
            assertTrue(frame.caption, frame.caption.length <= 170)
            val judge = frame.judge
            board = start
            if (judge != null) {
                val word = moves.removeFirst()
                var s = start
                for (c in word) {
                    assertEquals(setOf(EquationTeacher.key(c)), frame.glow!!(s).strong)
                    assertEquals(TutorialStep.REJECT, judge(s.withLetter(if (c == '0') '7' else '0')))
                    s = s.withLetter(c)
                    assertEquals(TutorialStep.PROGRESS, judge(s))
                }
                assertEquals(setOf(GuessTeacher.ENTER), frame.glow!!(s).strong)
                assertEquals("Backspace", TutorialStep.PROGRESS, judge(s.withoutLetter()))
                assertEquals(TutorialStep.DONE, judge(s.submit()))
                board = s.submit()
            }
        }
        assertTrue(moves.isEmpty())
        assertTrue(frames.last().freePlay)
        var s = board!!
        val techniques = ArrayList<String>()
        repeat(6) {
            if (!s.open) return@repeat
            val d = Equation.teach(s)!!
            techniques += d.technique
            assertFalse(d.mistake)
            s = (d.apply(s) as EquationState).submit()
        }
        assertTrue(s.solved)
        assertEquals("the free play opens on a pin", EquationTeacher.PIN, techniques.first())
    }

    @Test
    fun `the walkthrough's claims hold`() {
        fun left(guesses: List<String>) = EquationLogic.answers(6).filter { w ->
            GuessMarks.consistent(w, guesses, guesses.map { GuessMarks.mark(it, Equation.TUTORIAL_ANSWER) })
        }
        val (g1, g2, g3) = Equation.TUTORIAL_GUESSES
        Equation.TUTORIAL_GUESSES.forEach { assertTrue(it, EquationOracle.isTrue(it)) }
        // = is yellow in slot 5 and everything else of 42/7=6 grey.
        assertEquals(listOf(0, 0, 0, 0, 1, 0), GuessMarks.mark(g1, Equation.TUTORIAL_ANSWER))
        // 9+9=18 moves the = and makes +, = and 1 green.
        assertEquals(listOf(0, 2, 0, 2, 2, 1), GuessMarks.mark(g2, Equation.TUTORIAL_ANSWER))
        assertEquals(listOf("3+8=11", "5+8=13", "8+3=11", "8+5=13"), left(listOf(g1, g2)))
        // 3*5=15 can't be it, and each of the four marks it differently.
        assertFalse(g3 in left(listOf(g1, g2)))
        assertEquals(4, left(listOf(g1, g2)).map { GuessMarks.mark(g3, it) }.toSet().size)
        // 3 is yellow first, 5 yellow third: only the answer is left.
        val m3 = GuessMarks.mark(g3, Equation.TUTORIAL_ANSWER)
        assertEquals(GuessMarks.PRESENT, m3[0])
        assertEquals(GuessMarks.PRESENT, m3[2])
        assertEquals(listOf(Equation.TUTORIAL_ANSWER), left(Equation.TUTORIAL_GUESSES))
    }

    @Test
    fun `Equation is registered and its rules say what the walkthrough does not`() {
        assertTrue(PuzzleRegistry.all.any { it === Equation })
        assertTrue(Equation.offersHints)
        assertTrue(Equation.rules.any { it.contains("×") && it.contains("whole") })
    }
}
