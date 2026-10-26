package com.joebywan.daybook

import com.joebywan.daybook.puzzles.WhosWho
import com.joebywan.daybook.puzzles.WhosWhoLogic
import com.joebywan.daybook.puzzles.WhosWhoState
import com.joebywan.daybook.puzzles.WhosWhoTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Who's Who's walkthrough, and the glue that turns a teacher's step into a hint the screen can play. */
class WhosWhoTutorialTest {

    private val frames = WhosWho.tutorial

    private fun board(i: Int) = frames[i].state as WhosWhoState

    @Test
    fun `the walkthrough board has exactly one answer, and it is the stored one`() {
        val s = WhosWho.tutorialBoard()
        assertEquals(listOf(s.answer), WhosWhoOracle.answers(s.n, s.columns, s.clues))
        assertEquals(
            listOf("Ann lives in house 2.", "Ben does not have the dog.", "The cat owner lives in house 3.", "Ben does not live in house 3."),
            s.clues.indices.map { WhosWhoLogic.clueText(s, it) },
        )
    }

    @Test
    fun `each walkthrough frame accepts its move, made by the real gestures, and rejects a wrong one`() {
        assertEquals(9, frames.size)
        assertNull(frames[0].accepts)
        assertTrue(board(0).solved)
        assertTrue(frames.last().freePlay)
        // Every frame between is a move, and each starts where the one before it ended.
        val moves = listOf(
            board(1).mark(0, 0, 1, tick = true) to board(1).mark(0, 0, 2, tick = true),
            board(2).strike(0) to board(2).strike(1),
            board(3).mark(1, 1, 1, tick = false) to board(3).mark(1, 1, 1, tick = true),
            board(4).mark(1, 0, 2, tick = false) to board(4).mark(2, 0, 2, tick = false),
            board(5).mark(1, 0, 0, tick = true) to board(5).mark(1, 0, 0, tick = false),
            board(6).mark(2, 1, 1, tick = true) to board(6).mark(2, 1, 0, tick = true),
            board(7).mark(2, 1, 1, tick = true) to board(7).mark(2, 1, 0, tick = true),
        )
        for ((k, pair) in moves.withIndex()) {
            val i = k + 1
            val (right, wrong) = pair
            val accepts = frames[i].accepts!!
            assertTrue("frame $i takes its move", accepts(right))
            assertFalse("frame $i refuses a near miss", accepts(wrong))
            assertEquals("frame ${i + 1} continues from frame $i's move", board(i + 1).marks, right.marks)
            assertEquals(board(i + 1).struck.trimEnd('0'), right.struck.trimEnd('0'))
        }
    }

    @Test
    fun `what each caption says is true of its board`() {
        // Frame 5: after the cross, Ben's house square has only 1 left.
        val ben = WhosWhoLogic.gridOf(3, 2, board(5).marks).cand[1 * 2 + 0]
        assertEquals(1, ben)
        // Frame 7: the dog ticked for Cara breaks clue 3, and only clue 3.
        val slip = board(7)
        val bad = WhosWho.badClues(slip, WhosWhoLogic.gridOf(3, 2, slip.marks))
        assertEquals(listOf(false, false, true, false), bad.toList())
        assertEquals(WhosWhoTeacher.MISTAKE, WhosWhoTeacher.teach(slip)!!.technique)
        // No other frame's board holds a mistake.
        frames.forEachIndexed { i, f ->
            if (i != 7) assertNull("frame $i", WhosWhoTeacher.teach(f.state as WhosWhoState)?.takeIf { it.technique == WhosWhoTeacher.MISTAKE })
        }
        frames.forEach { assertTrue("caption too long (${it.caption.length}): ${it.caption}", it.caption.length <= 170) }
    }

    @Test
    fun `free play is finishable by hints alone, with no mistake on the way`() {
        var s = frames.last().state as WhosWhoState
        var guard = 0
        while (!s.solved) {
            assertTrue(guard++ < 30)
            val d = WhosWho.teach(s)
            assertNotNull(d)
            assertFalse(d!!.mistake)
            assertFalse(d.fallback)
            s = d.apply(s) as WhosWhoState
        }
    }

    @Test
    fun `a hint is reached by the player's own taps, not before`() {
        var s = frames.last().state as WhosWhoState
        while (!s.solved) {
            val d = WhosWho.teach(s)!!
            assertFalse(d.isReached(s))
            val lesson = WhosWhoTeacher.teach(s)!!
            // The player's way: a tap of each item on the pad, with the pen the step needs.
            var mine = s
            for (t in lesson.crosses) mine = mine.mark(t / s.n / s.columns, t / s.n % s.columns, t % s.n, tick = false)
            for (t in lesson.ticks) mine = mine.mark(t / s.n / s.columns, t / s.n % s.columns, t % s.n, tick = true)
            assertTrue(d.isReached(mine))
            s = mine
        }
    }
}
