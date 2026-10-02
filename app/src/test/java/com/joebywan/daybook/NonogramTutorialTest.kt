package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Nonogram
import com.joebywan.daybook.puzzles.NonogramLogic
import com.joebywan.daybook.puzzles.NonogramState
import com.joebywan.daybook.puzzles.NonogramTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Nonogram's walkthrough, and the glue that turns a teacher's step into a hint the screen can play. */
class NonogramTutorialTest {

    private val fill = NonogramLogic.FILLED
    private val cross = NonogramLogic.CROSSED
    private val clean = NonogramLogic.UNMARKED

    // ---- the walkthrough ----------------------------------------------------------------------

    @Test
    fun `the walkthrough board has exactly one answer, line-solvable, and it is the stored one`() {
        val s = Nonogram.tutorial.first().state as NonogramState
        val rows = NonogramLogic.rowClues(s)
        val cols = NonogramLogic.colClues(s)
        val all = NonogramOracle.answers(5, 5, rows, cols)
        assertEquals(1, all.size)
        assertEquals(s.solution, all.single().joinToString("") { if (it) "1" else "0" })
        assertTrue(NonogramLogic.lineSolvable(5, 5, s.solution))
    }

    @Test
    fun `each walkthrough frame accepts its move, made by the real gestures, and rejects a wrong one`() {
        val frames = Nonogram.tutorial
        assertEquals(6, frames.size)
        fun board(i: Int) = frames[i].state as NonogramState

        // The first frame only explains; the last is free play.
        assertNull(frames[0].accepts)
        assertTrue(frames[5].freePlay)
        assertTrue(board(0).solved)

        // 2: one sweep along row 2.
        val row2 = (5..9).toList()
        val sweepRow2 = frames[1].accepts!!
        val afterRow2 = board(1).sweep(row2, fill, clean)
        assertTrue(sweepRow2(afterRow2))
        assertFalse("four of the five squares", sweepRow2(board(1).sweep(row2.take(4), fill, clean)))
        assertFalse("a single tap", sweepRow2(board(1).tap(5, fill)))
        assertFalse("the wrong row", sweepRow2(board(1).sweep((10..14).toList(), fill, clean)))
        assertFalse("crosses instead of fills", sweepRow2(board(1).sweep(row2, cross, clean)))
        assertEquals("frame 3 continues from frame 2's move", board(2).cells, afterRow2.cells)

        // 3: the same along row 3.
        val row3 = (10..14).toList()
        val afterRow3 = board(2).sweep(row3, fill, clean)
        assertTrue(frames[2].accepts!!(afterRow3))
        assertFalse(frames[2].accepts!!(board(2).sweep(row3.drop(1), fill, clean)))
        assertEquals(board(3).cells, afterRow3.cells)

        // 4: one tap on the middle of row 4.
        val tap = frames[3].accepts!!
        val afterTap = board(3).tap(17, fill)
        assertTrue(tap(afterTap))
        assertFalse("a tap beside it", tap(board(3).tap(16, fill)))
        assertFalse("a cross instead of a fill", tap(board(3).tap(17, cross)))
        assertEquals(board(4).cells, afterTap.cells)

        // 5: one cross at the bottom of column 1.
        val crossTap = frames[4].accepts!!
        val afterCross = board(4).tap(20, cross)
        assertTrue(crossTap(afterCross))
        assertFalse("a fill instead of a cross", crossTap(board(4).tap(20, fill)))
        assertFalse("a cross elsewhere in the column", crossTap(board(4).tap(15, cross)))
        assertEquals(board(5).cells, afterCross.cells)

        // Each move is a mark the picture agrees with, and no frame offers one that is a mistake.
        frames.forEach { f -> assertNull(NonogramTeacher.teach(f.state as NonogramState)?.takeIf { it.technique == NonogramTeacher.MISTAKE }) }
    }

    @Test
    fun `what each caption says is true of its board`() {
        val frames = Nonogram.tutorial
        val clues = NonogramLogic.rowClues(frames[0].state as NonogramState)
        assertEquals(listOf(1, 1), clues[0])
        assertEquals(listOf(5), clues[1])
        assertEquals(listOf(5), clues[2])
        assertEquals(listOf(3), clues[3])
        assertEquals(listOf(2), NonogramLogic.colClues(frames[0].state as NonogramState)[0])
        // Frame 5's board already shows column 1's 2 in rows 2 and 3.
        val s = frames[4].state as NonogramState
        assertEquals(listOf(fill, fill), listOf(s.cells[5], s.cells[10]))
        frames.forEach { assertTrue("caption too long (${it.caption.length}): ${it.caption}", it.caption.length <= 170) }
    }

    @Test
    fun `free play is finishable by hints alone, with no mistake on the way`() {
        var s = Nonogram.tutorial.last().state as NonogramState
        var guard = 0
        while (!s.solved) {
            assertTrue(guard++ < 100)
            val d = Nonogram.teach(s)
            assertNotNull(d)
            assertFalse(d!!.mistake)
            assertFalse(d.fallback)
            s = d.apply(s) as NonogramState
        }
    }

    // ---- the hint glue --------------------------------------------------------------------------

    private fun seeds(count: Int, difficulty: Difficulty) = (0 until count).map {
        DailySeed.seedFor(LocalDate.of(2026, 3, 1).plusDays(it.toLong()), "nonogram-glue", difficulty)
    }

    @Test
    fun `a hint's move reaches its own goal, and one move is one undo entry`() {
        for (d in Difficulty.entries) for (seed in seeds(6, d)) {
            var s = Nonogram.generate(seed, d) as NonogramState
            while (!s.solved) {
                val deduction = Nonogram.teach(s)!!
                assertFalse("not reached before it is made", deduction.isReached(s))
                val next = deduction.apply(s) as NonogramState
                assertTrue(deduction.isReached(next))
                assertEquals("one move per square changed", deduction.targets.size + s.moves, next.moves)
                s = next
            }
        }
    }

    @Test
    fun `a hint is reached by the player's own moves, one square at a time, and not before the last`() {
        for (d in Difficulty.entries) {
            val s = Nonogram.generate(seeds(1, d).single(), d) as NonogramState
            val deduction = Nonogram.teach(s)!!
            val step = NonogramTeacher.teach(s)!!
            val moves = step.fills.map { it to fill } + step.crosses.map { it to cross }
            var now = s
            moves.forEachIndexed { n, (square, pen) ->
                assertFalse("$d: reached after only $n of ${moves.size} moves", deduction.isReached(now))
                now = now.tap(square, pen)
            }
            assertTrue(d.toString(), deduction.isReached(now))
        }
    }

    @Test
    fun `a mistake's hint is reached by clearing the square or by changing it to the right mark`() {
        val base = Nonogram.generate(seeds(1, Difficulty.STANDARD).single(), Difficulty.STANDARD) as NonogramState
        val i = base.solution.indexOf('0')
        val wrong = base.tap(i, fill)
        val d = Nonogram.teach(wrong)!!
        assertTrue(d.mistake)
        assertEquals(setOf(i), d.targets)
        assertFalse(d.isReached(wrong))
        assertTrue(d.isReached(wrong.tap(i, fill)))
        assertTrue(d.isReached(wrong.tap(i, cross)))
        assertEquals(clean, (d.apply(wrong) as NonogramState).cells[i])
    }
}
