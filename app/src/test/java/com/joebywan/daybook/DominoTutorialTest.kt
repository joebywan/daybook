package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.DominoLogic
import com.joebywan.daybook.puzzles.DominoPick
import com.joebywan.daybook.puzzles.DominoState
import com.joebywan.daybook.puzzles.DominoTeacher
import com.joebywan.daybook.puzzles.Dominoes
import com.joebywan.daybook.puzzles.Placement
import com.joebywan.daybook.puzzles.RuleKind
import com.joebywan.daybook.puzzles.dragSquares
import com.joebywan.daybook.puzzles.flipAt
import com.joebywan.daybook.puzzles.tapSquare
import com.joebywan.daybook.puzzles.tapTray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Dominoes' walkthrough, played by the board's own tap functions, and the glue that turns a step into a hint. */
class DominoTutorialTest {

    private fun norm(s: DominoState, ps: List<Placement?>) = ps.mapIndexed { d, p ->
        if (p != null && s.tray[d].isDouble && p.first > p.second) Placement(p.second, p.first) else p
    }

    /** Taps, in order: a tray slot as `TRAY + k`, else a square. The board the last tap leaves. */
    private fun play(s: DominoState, vararg taps: Int): DominoState {
        var pick = DominoPick()
        var board = s
        for (t in taps) {
            val m = if (t >= DominoTeacher.TRAY) board.tapTray(pick, t - DominoTeacher.TRAY) else board.tapSquare(pick, t)
            pick = m.pick
            m.board?.let { board = it }
        }
        return board
    }

    private val tray = DominoTeacher.TRAY

    @Test
    fun `the walkthrough board has exactly one answer by the oracle, and it is the stored one`() {
        val s = Dominoes.tutorialBoard()
        val answers = DominoOracle.answers(s.width, s.height, s.open, s.regionOf, s.rules, s.tray, cap = 3)
        assertEquals(1, answers.size)
        assertEquals(norm(s, s.answer), norm(s, answers.single()))
        val verdict = DominoLogic.solutions(s)
        assertTrue(verdict is DominoLogic.Verdict.ExactlyOne)
        assertNotNull("the ladder finishes it", DominoTeacher.solveLevel(s.layout, DominoTeacher.LEVEL_WHAT_IF))
    }

    @Test
    fun `each walkthrough frame accepts its move, made by the real gestures, and rejects a wrong one`() {
        val frames = Dominoes.tutorial
        assertEquals(7, frames.size)
        fun board(i: Int) = frames[i].state as DominoState
        assertNull(frames[0].accepts)
        assertTrue(board(0).solved)
        assertTrue(frames[6].freePlay)
        (1..5).forEach { assertNotNull("frame $it is played", frames[it].accepts) }

        // 1: the double 6 down the left, either square first.
        val six = frames[1].accepts!!
        val afterSix = play(board(1), tray + 3, 0, 4)
        assertTrue(six(afterSix))
        assertTrue("the other square first", six(play(board(1), tray + 3, 4, 0)))
        assertFalse("not selecting it lays the first in the tray", six(play(board(1), 0, 4)))
        assertFalse("the wrong squares", six(play(board(1), tray + 3, 1, 5)))
        assertEquals("frame 2 continues from frame 1's move", norm(afterSix, afterSix.placed), norm(board(2), board(2).placed))

        // 2: the 5-2 laid unturned from the 2 square: its 5 lands there, the player's own mistake.
        val backwards = frames[2].accepts!!
        val afterBackwards = play(board(2), tray + 2, 1, 5)
        assertTrue(backwards(afterBackwards))
        assertFalse("turned first, it is right, which this frame is not about", backwards(play(board(2), tray + 2, tray + 2, 1, 5)))
        assertEquals("the 5 is on the 2 square", 5, DominoLogic.pipsOf(afterBackwards)[1])
        assertTrue("and it is a mistake", DominoTeacher.teach(afterBackwards)!!.mistake)
        assertEquals(afterBackwards.placed, board(3).placed)

        // 3: a double tap or long press (both flipAt) turns it round where it lies; a single tap lifts it instead.
        val turn = frames[3].accepts!!
        val turned = board(3).flipAt(DominoPick(), 5).board!!
        assertTrue(turn(turned))
        assertTrue("on either square", turn(board(3).flipAt(DominoPick(), 1).board!!))
        assertFalse("a single tap lifts it", turn(board(3).tapSquare(DominoPick(), 1).board!!))
        assertEquals(2, DominoLogic.pipsOf(turned)[1])
        assertEquals(turned.placed, board(4).placed)

        // 4: the 1-0 along the bottom by a drag, its 1 on the = square.
        val one = frames[4].accepts!!
        val afterOne = board(4).dragSquares(board(4).tapTray(DominoPick(), 0).pick, 6, 7).board!!
        assertTrue(one(afterOne))
        assertTrue("by taps too", one(play(board(4), tray + 0, 6, 7)))
        assertFalse("dragged the wrong way", one(board(4).dragSquares(DominoPick(0), 7, 6).board!!))
        assertFalse("the wrong domino", one(play(board(4), tray + 1, 6, 7)))
        assertEquals(afterOne.placed, board(5).placed)

        // 5: a tap on the 5-2 lifts it, and puts it in hand.
        val lift = frames[5].accepts!!
        val m = board(5).tapSquare(DominoPick(), 5)
        assertTrue(lift(m.board!!))
        assertEquals(2, m.pick.selected)
        assertFalse("lifting the 1-0 instead", lift(board(5).tapSquare(DominoPick(), 6).board!!))
        assertEquals(m.board.placed, board(6).placed)

        // No frame's board holds a mistake but the one the player made on purpose.
        frames.forEachIndexed { i, f -> if (i != 3) assertFalse(DominoTeacher.teach(f.state as DominoState)?.mistake ?: false) }
    }

    @Test
    fun `what each caption says is true of its board`() {
        val s = Dominoes.tutorialBoard()
        val pips = DominoLogic.pipsOf(Dominoes.tutorial[0].state as DominoState)
        assertEquals(RuleKind.SUM, s.rules[0].kind)
        assertEquals(12, s.rules[0].n)
        assertEquals(listOf(6, 6), listOf(pips[0], pips[4]))
        assertEquals("the double 6 is the only 6", 1, s.tray.count { it.a == 6 || it.b == 6 })
        assertEquals(listOf(1), (0 until 8).filter { s.regionOf[it] == 1 })
        assertEquals(2, s.rules[1].n)
        assertEquals(RuleKind.EQUAL, s.rules[2].kind)
        assertEquals(RuleKind.LESS, s.rules[3].kind)
        assertEquals(2, s.rules[3].n)
        // Only 1 is on both the 4-1 and the 1-0.
        assertEquals(setOf(1), setOf(s.tray[0].a, s.tray[0].b).intersect(setOf(s.tray[1].a, s.tray[1].b)))
        Dominoes.tutorial.forEach { assertTrue("caption too long (${it.caption.length}): ${it.caption}", it.caption.length <= 170) }
        assertTrue("the rules name no glyph Fredoka lacks", Dominoes.rules.all { r -> r.all { it.code < 0x2000 } })
    }

    @Test
    fun `free play is finishable by hints alone, with no mistake and no fallback`() {
        var s = Dominoes.tutorial.last().state as DominoState
        var guard = 0
        while (!s.solved) {
            assertTrue(guard++ < 10)
            val d = Dominoes.teach(s)
            assertNotNull(d)
            assertFalse(d!!.mistake)
            assertFalse(d.fallback)
            s = d.apply(s) as DominoState
        }
    }

    // ---- the hint glue --------------------------------------------------------------------------

    private fun seeds(count: Int, difficulty: Difficulty) = (0 until count).map {
        DailySeed.seedFor(LocalDate.of(2026, 3, 1).plusDays(it.toLong()), "dominoes-glue", difficulty)
    }

    @Test
    fun `a hint's move reaches its own goal by the player's taps, and is one undo entry`() {
        for (d in Difficulty.entries) for (seed in seeds(4, d)) {
            var s = Dominoes.generate(seed, d) as DominoState
            while (!s.solved) {
                val deduction = Dominoes.teach(s)!!
                val step = DominoTeacher.teach(s)!!
                assertFalse("not reached before it is made", deduction.isReached(s))
                val shown = deduction.apply(s) as DominoState
                assertTrue(deduction.isReached(shown))
                assertEquals(s.moves + 1, shown.moves)
                // The same move by taps: the domino, turned if its lead must go second, then its two squares.
                val p = step.placement!!
                val byTaps = play(s, tray + step.domino, p.second, p.first)
                assertTrue("$d: taps reach the hint", deduction.isReached(byTaps))
                s = byTaps
            }
        }
    }

    @Test
    fun `a mistake's hint is reached by lifting the domino, and Show me lifts it`() {
        val base = Dominoes.generate(seeds(1, Difficulty.STANDARD).single(), Difficulty.STANDARD) as DominoState
        val d = base.tray.indices.first { !base.tray[it].isDouble }
        val right = base.answer[d]
        val wrong = DominoLogic.place(base, d, Placement(right.second, right.first))
        val deduction = Dominoes.teach(wrong)!!
        assertTrue(deduction.mistake)
        assertFalse(deduction.isReached(wrong))
        val lifted = wrong.tapSquare(DominoPick(), right.first).board!!
        assertTrue(deduction.isReached(lifted))
        assertNull((deduction.apply(wrong) as DominoState).placed[d])
    }
}
