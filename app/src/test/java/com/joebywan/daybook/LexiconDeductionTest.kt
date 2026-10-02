package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.puzzles.Lexicon
import com.joebywan.daybook.puzzles.LexiconKeyAction
import com.joebywan.daybook.puzzles.LexiconState
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
        assertTrue(frames.size >= 6)
        var board: LexiconState? = null
        for (frame in frames) {
            val start = frame.state as LexiconState
            val accepts = frame.accepts
            if (accepts != null) {
                // The one move it asks for is accepted; an unrelated one is not.
                val next = when {
                    start.current.length < 4 && frame.done.endsWith("goes in slot ${start.current.length + 1}.") ->
                        start.withLetter("boat"[start.current.length])
                    else -> start.submit()
                }
                assertTrue("frame '${frame.caption.take(30)}' refuses its own move", accepts(next))
                assertFalse("frame '${frame.caption.take(30)}' accepts a stray letter", accepts(start.withLetter('z')))
            }
            board = start
        }
        val last = frames.last()
        assertTrue(last.freePlay)
        // The free-play board is two guesses in; hints finish it.
        var s = board!!
        repeat(8) {
            if (!s.open) return@repeat
            s = (Lexicon.teach(s)!!.apply(s) as LexiconState).submit()
        }
        assertTrue(s.solved)
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
