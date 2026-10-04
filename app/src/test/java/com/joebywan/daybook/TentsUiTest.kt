package com.joebywan.daybook

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.puzzles.TentsKeyAction
import com.joebywan.daybook.puzzles.TentsLogic
import com.joebywan.daybook.puzzles.Tents
import com.joebywan.daybook.puzzles.TentsState
import com.joebywan.daybook.puzzles.applyKey
import com.joebywan.daybook.puzzles.cycled
import com.joebywan.daybook.puzzles.tentsKeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TentsUiTest {
    private val board = Tents.tutorialBoard()

    @Test fun `the walkthrough board has one answer and logic finds it`() {
        val n = board.size
        assertEquals(1, TentsLogic.countSolutions(n, board.trees, board.rowCounts, board.colCounts))
        assertTrue(TentsLogic.solvesByLogic(n, board.trees, board.rowCounts, board.colCounts))
        assertTrue(Tents.tutorialBoard(tents = setOf(1, 3, 10, 17)).solved)
        assertTrue(board.solution.withIndex().all { (i, t) -> t == (i in setOf(1, 3, 10, 17)) })
    }

    @Test fun `walkthrough frames accept their own move only`() {
        val frames = Tents.tutorial
        val first = frames.first { it.accepts != null }.accepts!!
        assertTrue(first(board.withMark(1, TentsLogic.TENT)))
        assertTrue(!first(board.withMark(3, TentsLogic.TENT)))
        assertTrue(frames.last().freePlay)
    }

    @Test fun `a tap cycles empty tent grass empty and leaves a tree alone`() {
        val a = board.cycled(0)
        assertEquals(TentsLogic.TENT, a.marks[0])
        assertEquals(TentsLogic.GRASS, a.cycled(0).marks[0])
        assertEquals(0, a.cycled(0).cycled(0).marks[0])
        assertSame(board, board.cycled(2)) // a tree
    }

    @Test fun `keys map and apply`() {
        assertEquals(TentsKeyAction.Tent, tentsKeyAction(Key.T))
        assertEquals(TentsKeyAction.Grass, tentsKeyAction(Key.G))
        assertNull(tentsKeyAction(Key.Q))
        val t = board.applyKey(0, TentsKeyAction.Tent)!!
        assertEquals(TentsLogic.TENT, t.marks[0])
        assertEquals(0, t.applyKey(0, TentsKeyAction.Tent)!!.marks[0]) // again takes it back
        assertEquals(0, t.applyKey(0, TentsKeyAction.Clear)!!.marks[0])
        assertNull(board.applyKey(0, TentsKeyAction.Clear)) // nothing to clear
        assertNull(board.applyKey(2, TentsKeyAction.Tent)) // a tree
    }
}
