package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.TutorialStep
import com.joebywan.daybook.puzzles.Lexicon
import com.joebywan.daybook.puzzles.LexiconKeyAction
import com.joebywan.daybook.puzzles.LexiconRules
import com.joebywan.daybook.puzzles.LexiconState
import com.joebywan.daybook.puzzles.LexiconTeacher
import com.joebywan.daybook.puzzles.WordList
import com.joebywan.daybook.puzzles.lexiconKeyAction
import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The glue between [LexiconTeacher][com.joebywan.daybook.puzzles.LexiconTeacher] and the play screen:
 * the [Deduction][com.joebywan.daybook.core.Deduction] each hint becomes, what "Show me" does, when a
 * hint counts as done, and the walkthrough being playable frame by frame.
 */
class LexiconDeductionTest {

    @Test
    fun `Lexicon is registered, and its walkthrough, rules and preview contract hold`() {
        assertTrue(PuzzleRegistry.all.any { it === Lexicon })
        assertEquals(Lexicon, PuzzleRegistry.byId("words"))
        assertTrue(Lexicon.offersHints)
        assertTrue(Lexicon.rules.isNotEmpty())
    }

    @Test
    fun `a hint's move is made by Show me and seen as done by the board it makes`() {
        for (tier in Difficulty.entries) {
            var s = LexiconSupport.board(5, tier)
            repeat(s.maxGuesses) {
                if (!s.open) return@repeat
                val d = Lexicon.teach(s)!!
                assertFalse(d.isReached(s))
                val shown = d.apply(s) as LexiconState
                assertTrue("${d.technique} changed nothing", shown != s)
                assertTrue("${d.technique} is not done after Show me", d.isReached(shown))
                // The explanation names a word or a letter the player can see on the board.
                assertTrue(d.explanation.isNotBlank() && d.nudge.isNotBlank())
                s = shown.submit()
            }
            assertTrue("${tier.name}: hints should have walked the board to its end", !s.open)
            assertNull(Lexicon.teach(s))
        }
    }

    @Test
    fun `a pin counts as done once the letter is in its slot, however the word was typed`() {
        // Answer mole after lime and loom: L belongs in slot 3.
        val s = LexiconState(4, 8, "mole", listOf("lime", "loom"))
        val d = Lexicon.teach(s)!!
        assertEquals("pinned-letter", d.technique)
        assertFalse(d.isReached(s))
        assertFalse(d.isReached(s.copy(current = "mo")))
        assertTrue(d.isReached(s.copy(current = "mol")))
        assertTrue(d.isReached(s.copy(current = "mole")))
    }

    @Test
    fun `a mistake is drawn as one and goes away when the row is taken back`() {
        val s = LexiconState(4, 8, "coat", listOf("tape", "boat"), current = "xoat")
        val d = Lexicon.teach(s)!!
        assertTrue(d.mistake)
        assertFalse(d.isReached(s))
        assertTrue(d.isReached(s.copy(current = "xoa")))
        assertEquals("xoa", (d.apply(s) as LexiconState).current)
    }

    @Test
    fun `a board that is over takes no hint`() {
        assertNull(Lexicon.teach(LexiconState(4, 2, "coat", listOf("boat", "goat"))))
        assertNull(Lexicon.teach(LexiconState(4, 2, "coat", listOf("coat"))))
    }

    @Test
    fun `the walkthrough is playable frame by frame and ends in a board the hints can finish`() {
        val frames = Lexicon.tutorial
        assertEquals(5, frames.size)
        assertEquals("Next-only frames", listOf(0, 1), frames.indices.filter { frames[it].judge == null && !frames[it].freePlay })
        val moves = ArrayDeque(Lexicon.TUTORIAL_GUESSES.drop(1))
        var board: LexiconState? = null
        for (frame in frames) {
            val start = frame.state as LexiconState
            board?.let { assertEquals("each frame starts where the last ended", it.copy(moves = 0), start) }
            val judge = frame.judge
            board = start
            if (judge != null) {
                // The word typed a letter at a time is progress, each letter's key glowing in turn, and
                // scoring it is the move; a stray letter at any point is refused.
                val word = moves.removeFirst()
                var s = start
                for (c in word) {
                    assertEquals(setOf(LexiconTeacher.key(c)), frame.glow!!(s).strong)
                    assertEquals(TutorialStep.REJECT, judge(s.withLetter('z')))
                    s = s.withLetter(c)
                    assertEquals(TutorialStep.PROGRESS, judge(s))
                }
                assertEquals(setOf(LexiconTeacher.ENTER), frame.glow!!(s).strong)
                assertEquals("Backspace", TutorialStep.PROGRESS, judge(s.withoutLetter()))
                assertEquals(TutorialStep.DONE, judge(s.submit()))
                board = s.submit()
            }
        }
        assertTrue(moves.isEmpty())
        val last = frames.last()
        assertTrue(last.freePlay)
        // The free-play board is three guesses in; hints finish it.
        var s = board!!
        repeat(8) {
            if (!s.open) return@repeat
            s = (Lexicon.teach(s)!!.apply(s) as LexiconState).submit()
        }
        assertTrue(s.solved)
    }

    @Test
    fun `the walkthrough's claims hold - OAT leaves three answers, and CLOG, which cannot win, tells them apart`() {
        fun left(guesses: List<String>) = WordList.answers(4).filter { w ->
            LexiconRules.consistent(w, guesses, guesses.map { LexiconRules.mark(it, Lexicon.TUTORIAL_ANSWER) })
        }
        val (tape, boat, clog) = Lexicon.TUTORIAL_GUESSES
        assertEquals(listOf("coat", "goat", "moat"), left(listOf(tape, boat)))
        assertTrue(WordList.isWord(clog))
        // Each of the three marks CLOG differently, so one guess settles it whichever is the word.
        assertEquals(3, listOf("coat", "goat", "moat").map { LexiconRules.mark(clog, it) }.toSet().size)
        assertEquals(listOf("coat"), left(listOf(tape, boat, clog)))
    }

    @Test
    fun `physical keys map to letters, enter and backspace, and nothing else`() {
        assertEquals(LexiconKeyAction.Letter('a'), lexiconKeyAction(Key.A))
        assertEquals(LexiconKeyAction.Letter('z'), lexiconKeyAction(Key.Z))
        assertEquals(LexiconKeyAction.Enter, lexiconKeyAction(Key.Enter))
        assertEquals(LexiconKeyAction.Enter, lexiconKeyAction(Key.NumPadEnter))
        assertEquals(LexiconKeyAction.Backspace, lexiconKeyAction(Key.Backspace))
        assertEquals(LexiconKeyAction.Backspace, lexiconKeyAction(Key.Delete))
        assertNull(lexiconKeyAction(Key.One))
        assertNull(lexiconKeyAction(Key.DirectionLeft))
        assertNull(lexiconKeyAction(Key.Spacebar))
        assertNotNull(lexiconKeyAction(Key.M))
    }
}
