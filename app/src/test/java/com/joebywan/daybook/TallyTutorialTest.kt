package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Tally
import com.joebywan.daybook.puzzles.TallyLogic
import com.joebywan.daybook.puzzles.TallyState
import com.joebywan.daybook.puzzles.TallyTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Tally's walkthrough, and the glue that turns a teacher's step into a hint the screen can play. */
class TallyTutorialTest {

    private val remove = TallyLogic.REMOVED
    private val keep = TallyLogic.KEPT
    private val clean = TallyLogic.UNMARKED

    @Test
    fun `the walkthrough board has exactly one answer, finished by the lines, and it is the stored one`() {
        val s = Tally.tutorial.first().state as TallyState
        val answers = TallyOracle.answerStrings(4, s.digits.map { it - '0' }, TallyLogic.rowTargets(s), TallyLogic.colTargets(s))
        assertEquals(listOf(s.keep), answers)
        assertEquals(TallyLogic.SHORTCUTS, TallyLogic.depth(s))
    }

    @Test
    fun `each walkthrough frame accepts its move, made by the real gestures, and rejects a wrong one`() {
        val frames = Tally.tutorial
        assertEquals(5, frames.size)
        fun board(i: Int) = frames[i].state as TallyState

        assertNull(frames[0].accepts)
        assertTrue(frames[4].freePlay)
        assertTrue(board(0).solved)

        // 2: one tap removes the 9 in row 1.
        val tap = frames[1].accepts!!
        val afterNine = board(1).tap(1, remove)
        assertTrue(tap(afterNine))
        assertFalse("the wrong square", tap(board(1).tap(0, remove)))
        assertFalse("a circle instead", tap(board(1).tap(1, keep)))
        assertEquals("frame 3 continues from frame 2's move", board(2).marks, afterNine.marks)

        // 3: one sweep down column 4 across both 9s.
        val sweep = frames[2].accepts!!
        val afterSweep = board(2).sweep(listOf(7, 11), remove, clean)
        assertTrue(sweep(afterSweep))
        assertFalse("only one of them", sweep(board(2).tap(7, remove)))
        assertFalse("too far", sweep(board(2).sweep(listOf(7, 11, 15), remove, clean)))
        assertEquals(board(3).marks, afterSweep.marks)

        // 4: Keep, then tap the 5.
        val circle = frames[3].accepts!!
        val afterKeep = board(3).tap(5, keep)
        assertTrue(circle(afterKeep))
        assertFalse("removing it instead", circle(board(3).tap(5, remove)))
        assertFalse("circling its neighbour", circle(board(3).tap(4, keep)))
        assertEquals(board(4).marks, afterKeep.marks)

        // No frame's board holds a mistake.
        frames.forEach { f -> assertNull(TallyTeacher.teach(f.state as TallyState)?.takeIf { it.technique == TallyTeacher.MISTAKE }) }
    }

    @Test
    fun `what each caption says is true of its board`() {
        val s0 = Tally.tutorial.first().state as TallyState
        val rows = TallyLogic.rowTargets(s0)
        val cols = TallyLogic.colTargets(s0)
        assertEquals(7, rows[0])
        assertEquals(listOf(1, 9, 6, 2), (0..3).map { s0.digitAt(it) })
        assertEquals("row 1 keeps 1 + 6", "1010", s0.keep.take(4))
        assertEquals(3, cols[3])
        assertEquals(listOf(9, 9), listOf(s0.digitAt(7), s0.digitAt(11)))
        // Frame 4: row 2 left adds to 10, 4 over its 6, and only the 5 is bigger than 4.
        val s3 = Tally.tutorial[3].state as TallyState
        assertEquals(6, rows[1])
        assertEquals(10, TallyLogic.leftIn(s3, 1))
        assertEquals(listOf(5), (4..7).filter { s3.marks[it] == clean && s3.digitAt(it) > 4 }.map { s3.digitAt(it) })
        Tally.tutorial.forEach { assertTrue("caption too long (${it.caption.length}): ${it.caption}", it.caption.length <= 170) }
    }

    @Test
    fun `free play is finishable by hints alone, with no mistake on the way`() {
        var s = Tally.tutorial.last().state as TallyState
        var guard = 0
        while (!s.solved) {
            assertTrue(guard++ < 50)
            val d = Tally.teach(s)
            assertNotNull(d)
            assertFalse(d!!.mistake)
            assertFalse(d.fallback)
            s = d.apply(s) as TallyState
        }
    }

    // ---- the hint glue --------------------------------------------------------------------------

    private fun seeds(count: Int, difficulty: Difficulty) = (0 until count).map {
        DailySeed.seedFor(LocalDate.of(2026, 3, 1).plusDays(it.toLong()), "tally-glue", difficulty)
    }

    @Test
    fun `a hint's move reaches its own goal, and one move is one undo entry`() {
        for (d in Difficulty.entries) for (seed in seeds(6, d)) {
            var s = Tally.generate(seed, d) as TallyState
            while (!s.solved) {
                val deduction = Tally.teach(s)!!
                assertFalse("not reached before it is made", deduction.isReached(s))
                val next = deduction.apply(s) as TallyState
                assertTrue(deduction.isReached(next))
                assertEquals("one move per square changed", deduction.targets.size + s.moves, next.moves)
                s = next
            }
        }
    }

    @Test
    fun `a hint is reached by the player's own taps, and not before the last`() {
        for (d in Difficulty.entries) {
            val s = Tally.generate(seeds(1, d).single(), d) as TallyState
            val deduction = Tally.teach(s)!!
            val step = TallyTeacher.teach(s)!!
            val moves = step.stays.map { it to keep } + step.goes.map { it to remove }
            var now = s
            moves.forEachIndexed { n, (square, pen) ->
                assertFalse("$d: reached after only $n of ${moves.size} moves", deduction.isReached(now))
                now = now.tap(square, pen)
            }
            assertTrue(d.toString(), deduction.isReached(now))
        }
    }

    @Test
    fun `a mistake's hint is reached by clearing the square or by changing its mark`() {
        val base = Tally.generate(seeds(1, Difficulty.STANDARD).single(), Difficulty.STANDARD) as TallyState
        val i = base.keep.indexOf('1')
        val wrong = base.tap(i, remove)
        val d = Tally.teach(wrong)!!
        assertTrue(d.mistake)
        assertEquals(setOf(i), d.targets)
        assertFalse(d.isReached(wrong))
        assertTrue(d.isReached(wrong.tap(i, remove)))
        assertTrue(d.isReached(wrong.tap(i, keep)))
        assertEquals(clean, (d.apply(wrong) as TallyState).marks[i])
    }
}
