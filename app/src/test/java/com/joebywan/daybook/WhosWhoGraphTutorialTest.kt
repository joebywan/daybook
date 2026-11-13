package com.joebywan.daybook

import com.joebywan.daybook.core.TutorialStep
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.puzzles.WhosWho
import com.joebywan.daybook.puzzles.WhosWhoGraph
import com.joebywan.daybook.puzzles.WhosWhoGraphLogic
import com.joebywan.daybook.puzzles.WhosWhoGraphState
import com.joebywan.daybook.puzzles.WhosWhoLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Logic Web's walkthrough, played by the board's own state functions ([WhosWhoGraphState.cycle] is a tap with an item
 * chosen, [WhosWhoGraphState.strike] a tap on a clue). Items: Ann 0, Ben 1, Cara 2, houses 1-3 are 3-5, cat 6, dog 7, fish 8.
 */
class WhosWhoGraphTutorialTest {

    private val frames = WhosWhoGraph.tutorial

    private fun board(i: Int) = frames[i].state as WhosWhoGraphState

    private fun judge(i: Int, s: PuzzleState) = frames[i].judge!!(s)

    private val ann = 0
    private val ben = 1
    private val cara = 2
    private val house3 = 5
    private val cat = 6
    private val dog = 7
    private val fish = 8

    @Test
    fun `the walkthrough board has exactly one answer, and it is the stored one`() {
        val s = WhosWhoGraph.tutorialBoard().board
        assertEquals(listOf(s.answer), WhosWhoOracle.answers(s.n, s.columns, s.clues))
        assertEquals(
            listOf("Ann lives in house 2.", "Ben does not have the dog.", "The cat owner lives in house 3.", "Ben does not live in house 3."),
            s.clues.indices.map { WhosWhoLogic.clueText(s, it) },
        )
        assertEquals(WhosWho.tutorialBoard(), s)
    }

    @Test
    fun `each frame takes its move, made by taps, refuses a near miss, and hands its board to the next`() {
        assertEquals(8, frames.size)
        assertNull(frames[0].judge)
        assertTrue(board(0).solved)
        assertTrue(frames.last().freePlay)
        frames.forEach { assertTrue("caption too long (${it.caption.length}): ${it.caption}", it.caption.length <= 170) }

        // 1: Ann, then house 2 (or house 2, then Ann: the same fact).
        assertEquals(TutorialStep.DONE, judge(1, board(1).cycle(ann, 4)))
        assertEquals(TutorialStep.DONE, judge(1, board(1).cycle(4, ann)))
        assertEquals(TutorialStep.REJECT, judge(1, board(1).cycle(ann, 3)))
        assertEquals(board(2), board(1).cycle(ann, 4))
        // 2: strike clue 1.
        assertEquals(TutorialStep.DONE, judge(2, board(2).strike(0)))
        assertEquals(TutorialStep.REJECT, judge(2, board(2).strike(1)))
        assertEquals(board(3), board(2).strike(0))
        // 3: Ben, then the cat and the fish, in either order.
        for ((x, y) in listOf(cat to fish, fish to cat)) {
            val one = board(3).cycle(ben, x)
            assertEquals(TutorialStep.PROGRESS, judge(3, one))
            assertEquals(TutorialStep.DONE, judge(3, one.cycle(ben, y)))
            assertEquals(marks(board(4)), marks(one.cycle(ben, y)))
        }
        assertEquals(TutorialStep.REJECT, judge(3, board(3).cycle(ben, dog)))
        assertEquals(TutorialStep.REJECT, judge(3, board(3).cycle(cat, ben)))
        // 4: Ben, then house 3 twice.
        val line = board(4).cycle(ben, house3)
        assertEquals(TutorialStep.PROGRESS, judge(4, line))
        assertEquals(TutorialStep.DONE, judge(4, line.cycle(ben, house3)))
        assertEquals(TutorialStep.REJECT, judge(4, board(4).cycle(ben, 3)))
        assertFalse(judge(4, board(4)) == TutorialStep.DONE)
        assertEquals(marks(board(5)), marks(line.cycle(ben, house3)))
        // 5: Cara, then the cat twice.
        val slipLine = board(5).cycle(cara, cat)
        assertEquals(TutorialStep.PROGRESS, judge(5, slipLine))
        assertEquals(TutorialStep.DONE, judge(5, slipLine.cycle(cara, cat)))
        assertEquals(TutorialStep.REJECT, judge(5, board(5).cycle(cara, fish)))
        assertEquals(marks(board(6)), marks(slipLine.cycle(cara, cat)))
        // 6: Cara, then the cat, clears it.
        assertEquals(TutorialStep.DONE, judge(6, board(6).cycle(cara, cat)))
        assertEquals(TutorialStep.REJECT, judge(6, board(6).cycle(cara, dog)))
        assertEquals(marks(board(7)), marks(board(6).cycle(cara, cat)))
    }

    private fun marks(s: WhosWhoGraphState) = s.links.trimEnd(WhosWhoGraphLogic.OPEN) to s.board.struck.trimEnd('0')

    @Test
    fun `what each caption says is true of its board`() {
        // Frame 3's two lines rule the dog out for Ben, and nothing else about him.
        val c4 = WhosWhoGraphLogic.close(board(4))
        assertEquals(-1, c4.at(ben, dog))
        assertEquals(0, c4.at(ben, cat))
        // Frame 4's anti-link leaves Ben only house 1: a link implied, not drawn.
        val c5 = WhosWhoGraphLogic.close(board(5))
        assertEquals(1, c5.at(ben, 3))
        assertFalse(WhosWhoGraphLogic.isLine(board(5).mark(ben, 3)))
        // Frame 5's slip breaks clue 3, and only clue 3; and the hint calls it a mistake.
        val slip = WhosWhoGraphLogic.table(board(6))
        val grid = WhosWhoLogic.gridOf(slip.n, slip.columns, slip.marks)
        val puzzle = WhosWhoLogic.puzzleOf(slip)
        assertEquals(listOf(true, true, false, true), slip.clues.map { WhosWhoLogic.possible(puzzle, grid, it) })
        assertTrue(WhosWhoGraph.teach(board(6))!!.mistake)
        // No other frame's board holds a mistake.
        frames.forEachIndexed { i, f -> if (i != 6) assertFalse("frame $i", WhosWhoGraph.teach(f.state)?.mistake ?: false) }
    }

    @Test
    fun `free play is finishable by hints alone, with no mistake on the way`() {
        var s = frames.last().state as WhosWhoGraphState
        assertFalse(s.solved)
        var guard = 0
        while (!s.solved) {
            assertTrue(guard++ < 30)
            val d = WhosWhoGraph.teach(s)
            assertNotNull(d)
            assertFalse(d!!.mistake)
            assertFalse(d.fallback)
            s = d.apply(s) as WhosWhoGraphState
        }
    }
}
