package com.joebywan.daybook

import com.joebywan.daybook.puzzles.MinesLogic
import com.joebywan.daybook.puzzles.PointersLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The curves a mine's blast and an arrow's recoil are drawn from, sampled from outside. */
class FeelCurvesTest {
    private val ts = (0..400).map { it / 400f }

    private fun peak(d: Float) = ts.maxOf { abs(MinesLogic.blastPush(it, d)) }
    private fun firstMove(d: Float) = ts.first { MinesLogic.blastPush(it, d) != 0f }

    @Test fun theBlastIsStrongestClosestInAndStopsAtItsReach() {
        val ds = listOf(1f, 1.414f, 2f, 2.236f, 3f)
        val peaks = ds.map(::peak)
        assertEquals(peaks, peaks.sortedDescending())
        assertTrue("next to the mine is a visible push", peaks[0] > 0.15f)
        assertTrue("a neighbour moves less than half a square", peaks[0] < 0.5f)
        assertTrue("falls off faster than a straight line", peaks[2] < peaks[0] * 0.5f)
        for (d in listOf(MinesLogic.BLAST_REACH, 4f, 12f)) assertEquals(0f, peak(d))
    }

    @Test fun theBlastTravelsOutwardsAndSpringsBack() {
        assertTrue(firstMove(1f) < firstMove(2f))
        assertTrue(firstMove(2f) < firstMove(3f))
        for (d in listOf(1f, 2f, 3f)) {
            assertEquals(0f, MinesLogic.blastPush(0f, d))
            assertEquals(0f, MinesLogic.blastPush(1f, d))
            assertTrue("the last tenth is nearly at rest", ts.filter { it > 0.9f }.all { abs(MinesLogic.blastPush(it, d)) < 0.02f })
            // The first move is away from the mine.
            assertTrue(MinesLogic.blastPush(firstMove(d), d) > 0f)
        }
    }

    @Test fun anArrowLandsOnItsBlockerAtContactAndSnapsBack() {
        val c = PointersLogic.BUMP_CONTACT
        for (gap in listOf(0, 1, 5, 13)) {
            val reach = PointersLogic.reachOf(gap)
            assertEquals(reach, PointersLogic.lunge(c, reach), 0.001f)
            assertEquals("never past the blocker", reach, ts.maxOf { PointersLogic.lunge(it, reach) }, 0.001f)
            assertEquals(0f, PointersLogic.lunge(0f, reach))
            assertEquals(0f, PointersLogic.lunge(1f, reach))
            assertTrue("back well before the end", PointersLogic.lunge(0.7f, reach) == 0f)
            assertTrue("never backwards", ts.all { PointersLogic.lunge(it, reach) >= 0f })
        }
    }

    @Test fun theKnockAndTheHeartWaitForContact() {
        val before = ts.filter { it <= PointersLogic.BUMP_CONTACT }
        assertTrue(before.all { PointersLogic.knock(it) == 0f && PointersLogic.heartBreak(it) == 0f })
        val after = ts.filter { it > PointersLogic.BUMP_CONTACT }
        assertTrue(after.first().let { PointersLogic.heartBreak(it) > 0f })
        assertEquals(1f, PointersLogic.heartBreak(1f))
        assertTrue("a small knock", after.maxOf { abs(PointersLogic.knock(it)) } in 0.05f..0.25f)
        assertEquals(0f, PointersLogic.knock(1f))
    }
}
