package com.joebywan.daybook.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveFeelTest {
    private val row = listOf(intArrayOf(0, 1, 2))
    private fun full(cells: List<Int>) = BooleanArray(1) { cells.all { it != 0 } }

    /** A feel that has seen [before] at move [tick], then [after] at move [next]. */
    private fun feel(before: List<Int>, after: List<Int>, tick: Int = 0, next: Int = 1, still: Boolean = false) = MoveFeel().apply {
        observe(tick, before, row, full(before), still)
        observe(next, after, row, full(after), still)
    }

    @Test fun aMoveSettlesOnlyWhatItChanged() {
        val f = feel(listOf(1, 0, 0), listOf(1, 2, 0))
        assertTrue(f.scale(1) > 1f)
        assertEquals(1f, f.scale(0))
        assertEquals(1f, f.scale(2))
    }

    @Test fun finishingAUnitSweepsIt() {
        val f = feel(listOf(1, 2, 0), listOf(1, 2, 3))
        f.sweep = 0.5f
        assertTrue((0..2).any { f.glow(it) > 0f })
    }

    @Test fun undoLoadAndReducedMotionPlayNothing() {
        // Undo, restart and a restored save hand a board without a move (the count does not change).
        val undone = feel(listOf(1, 2, 0), listOf(1, 2, 3), tick = 4, next = 4)
        undone.sweep = 0.5f
        assertEquals(1f, undone.scale(2))
        assertTrue((0..2).all { undone.glow(it) == 0f })
        // The first board seen is a load, whatever came before.
        val loaded = MoveFeel().apply { observe(3, listOf(1, 2, 3), row, BooleanArray(1) { true }, false) }
        assertEquals(1f, loaded.scale(2))
        val still = feel(listOf(1, 2, 0), listOf(1, 2, 3), still = true)
        still.sweep = 0.5f
        assertEquals(1f, still.scale(2))
        assertTrue((0..2).all { still.glow(it) == 0f })
    }

    @Test fun theSweepLightsEverySquareOnceAndNoneAtTheEnds() {
        for (at in listOf(0f, 0.25f, 0.5f, 1f)) {
            assertEquals(0f, sweepGlow(0f, at))
            assertEquals(0f, sweepGlow(1f, at))
            assertTrue((1..99).maxOf { sweepGlow(it / 100f, at) } > 0.95f)
        }
    }
}
