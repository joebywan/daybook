package com.joebywan.daybook

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.TutorialStep
import com.joebywan.daybook.puzzles.Lamps
import com.joebywan.daybook.puzzles.LampsKeyAction
import com.joebywan.daybook.puzzles.LampsLogic
import com.joebywan.daybook.puzzles.LampsState
import com.joebywan.daybook.puzzles.applyKey
import com.joebywan.daybook.puzzles.crossed
import com.joebywan.daybook.puzzles.lampsKeyAction
import com.joebywan.daybook.puzzles.tapped
import com.joebywan.daybook.puzzles.withLamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LampsUiTest {
    private val board = Lamps.tutorialBoard()
    private val answer = setOf(1, 5, 14, 17, 23)

    @Test fun `the walkthrough board has one answer, the stored one, and the direct facts find it`() {
        assertEquals(listOf(Lamps.TUTORIAL_SOLUTION), LampsOracle.solutions(5, Lamps.TUTORIAL_WALLS))
        assertEquals(answer, Lamps.TUTORIAL_SOLUTION.indices.filter { Lamps.TUTORIAL_SOLUTION[it] == '1' }.toSet())
        assertEquals(LampsLogic.DIRECT, LampsLogic.depth(board))
    }

    @Test fun `each walkthrough frame accepts its move, made by the real gestures, and rejects a wrong one`() {
        val frames = Lamps.tutorial
        assertEquals(6, frames.size)
        assertEquals(listOf(true, true, false, false, false, false), frames.map { it.judge == null && !it.freePlay })
        fun at(i: Int) = frames[i].state as LampsState

        // 1: the glowing lamp at 17 lights 15, 16, 18, 19 and 22, and nothing else.
        val lit17 = LampsLogic.sight(5, Lamps.TUTORIAL_WALLS, 17).toSet()
        assertEquals(setOf(15, 16, 18, 19, 22), lit17)
        // 2: the 2 has the lamps at 1 and 5 beside it.
        assertEquals(2, at(1).lampsBeside(0))

        // 3: one tap crosses out the square below the 0.
        assertEquals(13, 8 + 5)
        val tap = frames[2].accepts!!
        assertTrue(tap(at(2).tapped(13)))
        assertFalse("a tap elsewhere", tap(at(2).tapped(9)))
        assertFalse("a lamp instead", tap(at(2).withLamp(13)))
        assertEquals(at(3).marks, at(2).tapped(13).marks)

        // 4: one sweep across the 0, from 7 to 9, crosses both (the wall in between takes nothing).
        val sweep = frames[3].accepts!!
        assertTrue(sweep(at(3).crossed(setOf(7, 8, 9))))
        assertFalse("one side only", sweep(at(3).crossed(setOf(7, 8))))
        assertFalse("one tap", sweep(at(3).tapped(9)))
        assertEquals(at(4).marks, at(3).crossed(setOf(7, 8, 9)).marks)

        // 5: double-tap both lamps beside the 2, in either order; taking one back is still on the way.
        val pair = frames[4].steps!!
        val glow = frames[4].glow!!
        assertEquals(setOf(1, 5), glow(at(4)).strong)
        assertEquals(listOf(1, 5), LampsLogic.orth(5, 0)) // the 2 has only these two squares beside it
        for ((a, b) in listOf(1 to 5, 5 to 1)) {
            val one = at(4).withLamp(a)
            assertEquals(TutorialStep.PROGRESS, pair(one))
            assertEquals(setOf(b), glow(one).strong)
            assertEquals(TutorialStep.PROGRESS, pair(one.withLamp(a)))
            assertEquals(TutorialStep.REJECT, pair(one.tapped(b)))
            assertEquals(TutorialStep.REJECT, pair(one.withLamp(6)))
            assertEquals(TutorialStep.DONE, pair(one.withLamp(b)))
            assertEquals(at(5).marks, one.withLamp(b).marks)
        }

        // 6: free play, finishable by hints with no fallback and no mistake.
        assertTrue(frames[5].freePlay)
        var s = at(5)
        while (!s.solved) {
            val d = Lamps.teach(s)!!
            assertFalse(d.fallback)
            assertFalse(d.mistake)
            s = d.apply(s) as LampsState
        }
        assertEquals(answer, s.marks.indices.filter { s.marks[it] == LampsLogic.LAMP_MARK }.toSet())
        frames.forEach { assertTrue(it.caption, it.caption.length <= 170) }
    }

    @Test fun `a tap toggles a cross, a double tap a lamp, a tap on a lamp takes it away`() {
        val x = board.tapped(6)
        assertEquals(LampsLogic.CROSS, x.marks[6])
        assertEquals(LampsLogic.NONE, x.tapped(6).marks[6])
        assertSame(board, board.tapped(0)) // a wall
        val l = x.withLamp(6) // the double tap builds on the board before the first tap's cross
        assertEquals(LampsLogic.LAMP_MARK, l.marks[6])
        assertEquals(LampsLogic.NONE, l.tapped(6).marks[6])
        assertEquals(LampsLogic.NONE, l.withLamp(6).marks[6])
        assertSame(board, board.withLamp(8))
        assertTrue(l.lit()[7] && l.lit()[16] && !l.lit()[9])
    }

    @Test fun `a sweep crosses empty white squares only, as one move`() {
        val l = board.withLamp(6)
        val x = l.crossed(setOf(0, 6, 7, 11))
        assertEquals(LampsLogic.LAMP_MARK, x.marks[6])
        assertEquals(LampsLogic.CROSS, x.marks[7])
        assertEquals(LampsLogic.CROSS, x.marks[11])
        assertEquals(LampsLogic.NONE, x.marks[0])
        assertEquals(l.moves + 1, x.moves)
        assertSame(l, l.crossed(setOf(0, 6)))
    }

    @Test fun `bad numbers are over their count or out of reach, crosses ignored`() {
        // A lamp at 1 leaves the 2 one lamp and room for one more: fine.
        assertEquals(emptySet<Int>(), Lamps.badNumbers(board.withLamp(1)))
        // A lamp at 2 lights 1 (row 0), leaving the 2 room for one lamp only: out of reach.
        assertEquals(setOf(0), Lamps.badNumbers(board.withLamp(2)))
        // Crossing a square never makes a number bad.
        assertEquals(emptySet<Int>(), Lamps.badNumbers(board.tapped(1).tapped(5)))
        // Over: the 1 with two lamps.
        assertTrue(12 in Lamps.badNumbers(board.withLamp(7).withLamp(11)))
    }

    @Test fun `keys map and apply`() {
        assertEquals(LampsKeyAction.Lamp, lampsKeyAction(Key.L))
        assertEquals(LampsKeyAction.Lamp, lampsKeyAction(Key.Enter))
        assertEquals(LampsKeyAction.Cross, lampsKeyAction(Key.X))
        assertEquals(LampsKeyAction.Cross, lampsKeyAction(Key.Spacebar))
        assertEquals(LampsKeyAction.Clear, lampsKeyAction(Key.Backspace))
        assertNull(lampsKeyAction(Key.Q))
        val l = board.applyKey(6, LampsKeyAction.Lamp)!!
        assertEquals(board.withLamp(6), l)
        assertEquals(LampsLogic.NONE, l.applyKey(6, LampsKeyAction.Lamp)!!.marks[6])
        assertEquals(LampsLogic.CROSS, l.applyKey(6, LampsKeyAction.Cross)!!.marks[6])
        assertEquals(board.tapped(6), board.applyKey(6, LampsKeyAction.Cross))
        assertEquals(LampsLogic.NONE, l.applyKey(6, LampsKeyAction.Clear)!!.marks[6])
        assertNull(board.applyKey(6, LampsKeyAction.Clear))
        assertNull(board.applyKey(0, LampsKeyAction.Lamp)) // a wall
    }
}
