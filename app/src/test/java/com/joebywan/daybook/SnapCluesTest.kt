package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Snap
import com.joebywan.daybook.puzzles.SnapState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Snap's numbers, checked against a solver that shares nothing with the generator's.
 *
 * [SnapOracle] is deliberately the naive thing: depth-first from the square numbered 1, no
 * node budget, no viability pruning, and the waypoint order checked only once a full line exists
 * rather than pruned into the walk. The generator's own solver is the opposite on every one of
 * those points, so the two cannot agree by sharing a mistake. The price is speed — one Expert
 * board takes several seconds — which is why the sample thins out as the boards grow.
 */
class SnapCluesTest {

    private val seeds = listOf(1L, 404L, 7919L, 20260924L, 555555L, 42L, 98765L, 31337L)

    /**
     * The regression this file exists for. A board that numbers most of its squares has a unique
     * answer for the worst possible reason — there is nothing left to work out — and that is what
     * shipped: Expert boards were arriving with 39 of 42 squares numbered.
     */
    @Test
    fun `no board numbers more than a third of its squares`() {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds) {
                val s = Snap.generate(seed, difficulty) as SnapState
                val clues = s.waypoints.count { it > 0 }
                assertTrue(
                    "snap/${difficulty.name}/$seed numbers $clues of ${s.cellCount} squares",
                    clues <= s.cellCount / 3,
                )
            }
        }
    }

    /** Numbers must run 1..k with none missing, or the ordering rule means nothing. */
    @Test
    fun `numbers are a run from one with no gaps and no repeats`() {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds) {
                val s = Snap.generate(seed, difficulty) as SnapState
                val given = s.waypoints.filter { it > 0 }.sorted()
                assertEquals(
                    "snap/${difficulty.name}/$seed numbers are $given",
                    (1..given.size).toList(),
                    given,
                )
            }
        }
    }

    @Test
    fun `standard boards have exactly one answer`() {
        for (seed in seeds) {
            val s = Snap.generate(seed, Difficulty.STANDARD) as SnapState
            val paths = SnapOracle.answers(s.width, s.height, s.waypoints, cap = 2)
            assertEquals("snap/STANDARD/$seed", 1, paths.size)
            // And the answer the rules accept is the answer the search found.
            assertTrue(
                "snap/STANDARD/$seed does not accept its own solution",
                s.copy(path = paths.single()).solved,
            )
        }
    }

    @Test
    fun `hard boards have exactly one answer`() {
        for (seed in seeds.take(3)) {
            val s = Snap.generate(seed, Difficulty.HARD) as SnapState
            assertEquals("snap/HARD/$seed", 1, SnapOracle.answers(s.width, s.height, s.waypoints, cap = 2).size)
        }
    }

    /**
     * One board only. Expert is 42 squares and the naive search takes several seconds over each
     * one, but it is also the size that went wrong, so leaving it out entirely would drop the
     * case that mattered.
     */
    @Test
    fun `an expert board has exactly one answer`() {
        val s = Snap.generate(20260924L, Difficulty.EXPERT) as SnapState
        assertEquals(1, SnapOracle.answers(s.width, s.height, s.waypoints, cap = 2).size)
    }

    /** A line that skips a waypoint, or takes them out of order, must not count as solved. */
    @Test
    fun `the rules reject a line that ignores the numbers`() {
        val s = Snap.generate(7919L, Difficulty.STANDARD) as SnapState
        val answer = SnapOracle.answers(s.width, s.height, s.waypoints, cap = 1).single()
        assertTrue(s.copy(path = answer).solved)
        assertTrue("a reversed line meets the numbers backwards", !s.copy(path = answer.reversed()).solved)
        assertTrue("a short line covers nothing", !s.copy(path = answer.dropLast(1)).solved)
    }
}
