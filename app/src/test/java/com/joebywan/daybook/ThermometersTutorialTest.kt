package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Thermometers
import com.joebywan.daybook.puzzles.ThermometersLogic
import com.joebywan.daybook.puzzles.ThermometersState
import com.joebywan.daybook.puzzles.ThermometersTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Thermometers' walkthrough, and the glue that turns a teacher's step into a hint the screen can play. */
class ThermometersTutorialTest {

    private val fill = ThermometersLogic.FILLED
    private val cross = ThermometersLogic.CROSSED
    private val clean = ThermometersLogic.UNMARKED

    private fun countsOf(s: ThermometersState) = ThermometersLogic.targets(s).let { it.take(s.size) to it.drop(s.size) }

    @Test
    fun `the walkthrough board has exactly one answer, finished by the lines, and it is the stored one`() {
        val s = Thermometers.tutorial.first().state as ThermometersState
        val (rows, cols) = countsOf(s)
        assertEquals(listOf(s.fill), ThermometersOracle.answers(4, s.tubes, rows, cols))
        assertEquals(ThermometersLogic.SHORTCUTS, ThermometersLogic.depth(s))
    }

    @Test
    fun `each walkthrough frame accepts its move, made by the real gestures, and rejects a wrong one`() {
        val frames = Thermometers.tutorial
        assertEquals(6, frames.size)
        fun board(i: Int) = frames[i].state as ThermometersState

        assertNull(frames[0].accepts)
        assertTrue(frames[5].freePlay)
        assertTrue(board(0).solved)

        // 2: one sweep along row 4.
        val sweep = frames[1].accepts!!
        val afterSweep = board(1).sweep(listOf(12, 13, 14, 15), cross, clean)
        assertTrue(sweep(afterSweep))
        assertFalse("only part of it", sweep(board(1).sweep(listOf(12, 13, 14), cross, clean)))
        assertFalse("filled instead", sweep(board(1).sweep(listOf(12, 13, 14, 15), fill, clean)))
        assertEquals("frame 3 continues from frame 2's move", board(2).marks, afterSweep.marks)

        // 3: one tap on column 1's third square fills its thermometer from the bulb.
        val tap = frames[2].accepts!!
        val afterColumn = board(2).tap(8, fill)
        assertTrue(tap(afterColumn))
        assertEquals(1, afterColumn.moves - board(2).moves)
        assertFalse("the square below", tap(board(2).tap(4, fill)))
        assertFalse("a cross instead", tap(board(2).tap(8, cross)))
        assertEquals(board(3).marks, afterColumn.marks)

        // 4: a tap on row 1's middle square fills the bulb beside it too.
        val top = frames[3].accepts!!
        val afterRow = board(3).tap(2, fill)
        assertTrue(top(afterRow))
        assertFalse("the bulb alone", top(board(3).tap(1, fill)))
        assertFalse("the whole thermometer", top(board(3).tap(3, fill)))
        assertEquals(board(4).marks, afterRow.marks)

        // 5: a double tap (the other pen) on the tip crosses it.
        val tip = frames[4].accepts!!
        val afterTip = board(4).tap(3, cross)
        assertTrue(tip(afterTip))
        assertFalse("filling it instead", tip(board(4).tap(3, fill)))
        assertFalse("the wrong square", tip(board(4).tap(7, cross)))
        assertEquals(board(5).marks, afterTip.marks)

        frames.forEach { assertNull(ThermometersTeacher.teach(it.state as ThermometersState)?.takeIf { s -> s.technique == ThermometersTeacher.MISTAKE }) }
    }

    @Test
    fun `what each caption says is true of its board`() {
        val s0 = Thermometers.tutorial.first().state as ThermometersState
        val (rows, cols) = countsOf(s0)
        assertEquals(3, rows[0])
        assertEquals(0, rows[3])
        assertEquals(3, cols[0])
        assertEquals(3, cols[2])
        // Frame 3: column 1 has just 3 squares left once row 4 is crossed; frame 4: column 3 too.
        val s2 = Thermometers.tutorial[2].state as ThermometersState
        assertEquals(3, ThermometersLogic.lineCells(4, 4).count { s2.marks[it] == clean })
        assertEquals(3, ThermometersLogic.lineCells(4, 6).count { s2.marks[it] == clean })
        // Frame 5: row 1 holds its 3.
        val s4 = Thermometers.tutorial[4].state as ThermometersState
        assertEquals(3, ThermometersLogic.filledIn(s4, 0))
        // The thermometers the captions name: column 1's from the top, row 1's from the left.
        assertEquals(listOf(0, 4, 8, 12), s0.tubes.first { 8 in it })
        assertEquals(listOf(1, 2, 3), s0.tubes.first { 2 in it })
        Thermometers.tutorial.forEach { assertTrue("caption too long (${it.caption.length}): ${it.caption}", it.caption.length <= 170) }
    }

    @Test
    fun `free play is finishable by hints alone, with no mistake on the way`() {
        var s = Thermometers.tutorial.last().state as ThermometersState
        var guard = 0
        while (!s.solved) {
            assertTrue(guard++ < 50)
            val d = Thermometers.teach(s)
            assertNotNull(d)
            assertFalse(d!!.mistake)
            assertFalse(d.fallback)
            s = d.apply(s) as ThermometersState
        }
    }

    // ---- the hint glue --------------------------------------------------------------------------

    @Test
    fun `a hint's move reaches its own goal, and one move is one undo entry`() {
        for (d in Difficulty.entries) for (seed in seeds(6, d, "thermometers-glue", LocalDate.of(2026, 3, 1))) {
            var s = Thermometers.generate(seed, d) as ThermometersState
            while (!s.solved) {
                val deduction = Thermometers.teach(s)!!
                assertFalse("not reached before it is made", deduction.isReached(s))
                val next = deduction.apply(s) as ThermometersState
                assertTrue(deduction.isReached(next))
                assertEquals("one move per square changed", deduction.targets.size + s.moves, next.moves)
                s = next
            }
        }
    }

    @Test
    fun `a hint is reached by the player's own taps, one a thermometer, and not before the last`() {
        var checked = 0
        for (d in Difficulty.entries) for (seed in seeds(10, d, "thermometers-glue", LocalDate.of(2026, 3, 1))) {
            var s = Thermometers.generate(seed, d) as ThermometersState
            while (!s.solved) {
                val deduction = Thermometers.teach(s)!!
                val step = ThermometersTeacher.teach(s)!!
                // Per thermometer: one fill tap on its highest square to fill, one cross tap on its lowest to cross.
                val l = ThermometersLogic.layout(s)
                val taps = step.full.groupBy { l.tubeOf[it] }.values.map { sq -> sq.maxBy { l.posOf[it] } to fill } +
                    step.empty.groupBy { l.tubeOf[it] }.values.map { sq -> sq.minBy { l.posOf[it] } to cross }
                var now = s
                taps.forEachIndexed { n, (square, pen) ->
                    assertFalse("$d/$seed: reached after only $n of ${taps.size} taps", deduction.isReached(now))
                    now = now.tap(square, pen)
                }
                assertTrue("$d/$seed ${step.technique}", deduction.isReached(now))
                if (step.technique in setOf(ThermometersTeacher.AT_MOST, ThermometersTeacher.AT_LEAST, ThermometersTeacher.WHAT_IF)) {
                    assertEquals("one tap makes a thermometer's step", 1, taps.size)
                }
                checked++
                s = now
            }
        }
        assertTrue(checked > 200)
    }

    @Test
    fun `a mistake's hint is reached by clearing the square or by changing its mark`() {
        val base = Thermometers.generate(seeds(1, Difficulty.STANDARD, "thermometers-glue", LocalDate.of(2026, 3, 1)).single(), Difficulty.STANDARD) as ThermometersState
        val i = base.fill.indexOf('1')
        val wrong = base.paint(listOf(i), cross)
        val d = Thermometers.teach(wrong)!!
        assertTrue(d.mistake)
        assertEquals(setOf(i), d.targets)
        assertFalse(d.isReached(wrong))
        assertTrue(d.isReached(wrong.paint(listOf(i), clean)))
        assertTrue(d.isReached(wrong.paint(listOf(i), fill)))
        assertEquals(clean, (d.apply(wrong) as ThermometersState).marks[i])
    }
}
