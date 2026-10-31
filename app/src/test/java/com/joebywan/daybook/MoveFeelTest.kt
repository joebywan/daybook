package com.joebywan.daybook.ui

import com.joebywan.daybook.core.Tones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveFeelTest {
    @Test
    fun aFinishedUnitsToneFiresOnAMoveOnly() {
        val row = listOf(intArrayOf(0, 1))
        fun d(v: Boolean) = BooleanArray(1) { v }
        val a = listOf(1, 0)
        val b = listOf(1, 2)
        // A move; reduced motion stills the feel, never the sound.
        for (still in listOf(false, true)) assertEquals(1, MoveFeel().apply {
            observe(0, a, row, d(false), still)
            observe(1, b, row, d(true), still)
        }.unitRun)
        // Loaded finished, undone into finished, or finished by the solving move (the chime has that one).
        assertEquals(0, MoveFeel().apply { observe(3, b, row, d(true), false) }.unitRun)
        assertEquals(0, MoveFeel().apply {
            observe(0, a, row, d(false), false)
            observe(0, b, row, d(true), false, undos = 1)
        }.unitRun)
        assertEquals(0, MoveFeel().apply {
            observe(0, a, row, d(false), false)
            observe(1, b, row, d(true), false, solves = 1)
        }.unitRun)
    }

    @Test fun theNoteFollowsTheUnitsDoneAndAnUndoStepsBack() {
        val units = List(10) { intArrayOf(it) }
        fun done(n: Int) = BooleanArray(10) { it < n }
        val cells = listOf(0)
        val f = MoveFeel()
        f.observe(0, cells, units, done(0), false)
        val heard = ArrayList<Int>()
        fun move(tick: Int, n: Int) { f.observe(tick, cells, units, done(n), false); heard += f.unitStep }
        move(1, 1); move(2, 3); move(3, 5)
        assertEquals(listOf(0, 1, 3), heard)
        // Undone back to one unit: no note for the undo, and the unit found again is the note it was.
        f.observe(3, cells, units, done(1), false, undos = 1)
        assertEquals(3, f.unitRun)
        move(4, 2)
        assertEquals(Tones.phraseStep(2, 10), f.unitStep)
        assertTrue(f.unitStep < heard[2])
    }

    @Test fun aWinsThumpIsOneDropAndOneRiseAndEndsAtRest() {
        val path = (0..200).map { winThump(it / 200f) }
        assertEquals(0f, path.first())
        assertEquals(0f, path.last())
        assertEquals(1f, path.max(), 0.02f)
        // The drop is fast, and the rise above rest is small.
        assertTrue(path.indexOf(path.max()) < 50)
        assertTrue(path.min() < -0.1f && path.min() > -0.4f)
        // Down, up through rest, back to rest: one crossing to above rest, none but a settle after it.
        assertTrue((1..200).count { path[it - 1] > 0.02f && path[it] <= 0.02f } == 1)
    }

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

    @Test fun anUndoDipsWhatItTookBack() {
        val f = MoveFeel().apply {
            observe(1, listOf(1, 2, 0), row, BooleanArray(1), false)
            observe(1, listOf(1, 0, 0), row, BooleanArray(1), false, undos = 1)
        }
        assertTrue(f.scale(1) < 1f)
        assertEquals(1f, f.scale(0))
    }

    @Test fun theSolvingMoveWavesAcrossEverySquare() {
        val f = MoveFeel().apply {
            observe(1, listOf(1, 2, 0), row, full(listOf(1, 2, 0)), false)
            observe(2, listOf(1, 2, 3), row, full(listOf(1, 2, 3)), false, solves = 1)
        }
        f.sweep = 1f
        for (i in 0..2) assertTrue((1..99).maxOf { f.wave = it / 100f; f.glow(i) } > 0.9f)
        f.wave = 1f
        assertTrue((0..2).all { f.glow(it) == 0f })
        // A board loaded already solved, or under reduced motion, has no wave.
        val loaded = MoveFeel().apply { observe(0, listOf(1, 2, 3), row, BooleanArray(1) { true }, false, solves = 0) }
        assertEquals(1f, loaded.wave)
        val still = MoveFeel().apply {
            observe(1, listOf(1, 2, 0), row, BooleanArray(1), true)
            observe(2, listOf(1, 2, 3), row, BooleanArray(1) { true }, true, solves = 1)
        }
        assertEquals(1f, still.wave)
    }

    @Test fun anArrivingBoardFadesInInReadingOrder() {
        val f = MoveFeel().apply { observe(0, listOf(1, 2, 3), row, BooleanArray(1), false, arrives = true) }
        assertTrue((0..2).all { f.alpha(it) == 0f })
        f.arrive = 0.5f
        assertTrue(f.alpha(0) > f.alpha(1) && f.alpha(1) > f.alpha(2))
        f.arrive = 1f
        assertTrue((0..2).all { f.alpha(it) == 1f })
        // Only the first board seen arrives; never under reduced motion.
        val later = MoveFeel().apply {
            observe(0, listOf(1, 2, 3), row, BooleanArray(1), false)
            observe(0, listOf(1, 2, 3), row, BooleanArray(1), false, arrives = true)
        }
        assertEquals(1f, later.alpha(2))
        val still = MoveFeel().apply { observe(0, listOf(1, 2, 3), row, BooleanArray(1), true, arrives = true) }
        assertEquals(1f, still.alpha(2))
    }

    @Test fun theSweepLightsEverySquareOnceAndNoneAtTheEnds() {
        for (at in listOf(0f, 0.25f, 0.5f, 1f)) {
            assertEquals(0f, sweepGlow(0f, at))
            assertEquals(0f, sweepGlow(1f, at))
            assertTrue((1..99).maxOf { sweepGlow(it / 100f, at) } > 0.95f)
        }
    }
}
