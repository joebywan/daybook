package com.joebywan.daybook

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.puzzles.TentsKeyAction
import com.joebywan.daybook.puzzles.TentsLogic
import com.joebywan.daybook.puzzles.Tents
import com.joebywan.daybook.puzzles.TentsState
import com.joebywan.daybook.puzzles.applyKey
import com.joebywan.daybook.puzzles.grassed
import com.joebywan.daybook.puzzles.tapped
import com.joebywan.daybook.puzzles.withTent
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
        assertTrue(first(board.withTent(1)))
        assertTrue(!first(board.withTent(3)))
        assertTrue(frames.last().freePlay)
    }

    @Test fun `a tap toggles grass, a double tap a tent that sprouts grass`() {
        val g = board.tapped(0)
        assertEquals(TentsLogic.GRASS, g.marks[0])
        assertEquals(0, g.tapped(0).marks[0])
        assertSame(board, board.tapped(2)) // a tree
        val t = board.withTent(1)
        assertEquals(TentsLogic.TENT, t.marks[1])
        assertEquals(listOf(0, 5, 6, 7), listOf(0, 5, 6, 7).filter { t.seen[it] == TentsLogic.GRASS })
        assertEquals(0, t.marks[0]) // shown, not stored
        assertSame(t, t.tapped(0)) // nothing to toggle there
        assertEquals(1, t.moves)
        assertEquals(0, t.withTent(1).marks[1]) // again takes it back
        assertSame(board, board.withTent(2))
    }

    @Test fun `taking a tent back takes its grass, but keeps the player's and another tent's`() {
        val mine = board.withMark(0, TentsLogic.GRASS) // beside tent 1, laid by hand
        val two = mine.withTent(1).withTent(3) // 7 touches both tents
        val back = two.withTent(1)
        assertEquals(TentsLogic.GRASS, back.seen[0]) // the player's own stays
        assertEquals(TentsLogic.GRASS, back.seen[7]) // tent 3 still touches it
        assertEquals(0, back.seen[5]) // only tent 1 touched it
        assertEquals(mine.marks, mine.withTent(1).withTent(1).marks)
    }

    @Test fun `a sweep lays grass on empty squares only, as one move`() {
        val t = board.withTent(1)
        val g = t.grassed(setOf(1, 2, 0, 10, 11))
        assertEquals(TentsLogic.TENT, g.marks[1]) // a tent stays
        assertEquals(TentsLogic.GRASS, g.marks[10])
        assertEquals(TentsLogic.GRASS, g.marks[11])
        assertEquals(t.moves + 1, g.moves)
        assertSame(t, t.grassed(setOf(1, 2, 0)))
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
