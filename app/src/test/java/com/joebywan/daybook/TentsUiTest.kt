package com.joebywan.daybook

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.TutorialStep
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
import org.junit.Assert.assertFalse
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

    @Test fun `each walkthrough frame accepts its move, made by the real gestures, and rejects a wrong one`() {
        val frames = Tents.tutorial
        assertEquals(6, frames.size)
        assertEquals(listOf(true, true, false, false, false, false), frames.map { it.judge == null && !it.freePlay })
        fun at(i: Int) = frames[i].state as TentsState

        // 3: one tap marks the corner grass; it has no tree beside it.
        val tap = frames[2].accepts!!
        assertTrue(tap(at(2).tapped(0)))
        assertFalse("a tap elsewhere", tap(at(2).tapped(4)))
        assertFalse("a tent instead of grass", tap(at(2).withTent(0)))
        assertTrue(TentsLogic.orth(5, 0).none { at(2).trees[it] })
        assertEquals(at(3).marks, at(2).tapped(0).marks)

        // 4: double-tap both top-row tents, in either order; taking one back is still on the way.
        val pair = frames[3].steps!!
        val glow = frames[3].glow!!
        assertEquals(setOf(1, 3), glow(at(3)).strong)
        for ((a, b) in listOf(1 to 3, 3 to 1)) {
            val one = at(3).withTent(a)
            assertEquals(TutorialStep.PROGRESS, pair(one))
            assertEquals(setOf(b), glow(one).strong)
            assertEquals(TutorialStep.PROGRESS, pair(one.withTent(a)))
            assertEquals(TutorialStep.REJECT, pair(one.tapped(b)))
            assertEquals(TutorialStep.REJECT, pair(one.withTent(4)))
            assertEquals(TutorialStep.DONE, pair(one.withTent(b)))
            assertEquals(at(4).marks, one.withTent(b).marks)
        }
        // Its claims: the top row needs two, and only squares 1 and 3 of it sit beside a tree.
        assertEquals(2, at(3).rowCounts[0])
        assertEquals(listOf(1, 3), (0 until 5).filter { i -> !at(3).trees[i] && TentsLogic.orth(5, i).any { at(3).trees[it] } })
        // And grass sprouts around the tents.
        assertTrue(listOf(0, 4, 5, 6, 7, 9).all { at(4).seen[it] == TentsLogic.GRASS })

        // 5: one sweep along the bottom row, whose count is 0.
        val sweep = frames[4].accepts!!
        val bottom = (20 until 25).toSet()
        assertEquals(0, at(4).rowCounts[4])
        assertTrue(sweep(at(4).grassed(bottom)))
        assertFalse("part of the row", sweep(at(4).grassed(bottom - 24)))
        assertFalse("one tap", sweep(at(4).tapped(20)))
        assertEquals(at(5).marks, at(4).grassed(bottom).marks)

        // 6: free play, finishable by hints with no fallback and no mistake.
        assertTrue(frames[5].freePlay)
        var s = at(5)
        while (!s.solved) {
            val d = Tents.teach(s)!!
            assertFalse(d.fallback)
            assertFalse(d.mistake)
            s = d.apply(s) as TentsState
        }
        assertEquals(setOf(1, 3, 10, 17), s.marks.indices.filter { s.marks[it] == TentsLogic.TENT }.toSet())
        frames.forEach { assertTrue(it.caption.length <= 170) }
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
