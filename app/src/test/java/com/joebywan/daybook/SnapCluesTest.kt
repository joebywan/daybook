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
 * [countPaths] here is deliberately the naive thing: depth-first from the square numbered 1, no
 * node budget, no viability pruning, and the waypoint order checked only once a full line exists
 * rather than pruned into the walk. The generator's own solver is the opposite on every one of
 * those points, so the two cannot agree by sharing a mistake. The price is speed — one Expert
 * board takes several seconds — which is why the sample thins out as the boards grow.
 */
class SnapCluesTest {

    private val seeds = listOf(1L, 404L, 7919L, 20260924L, 555555L, 42L, 98765L, 31337L)

    private fun neighbours(s: SnapState, cell: Int): List<Int> {
        val r = cell / s.width
        val c = cell % s.width
        return buildList {
            if (r > 0) add(cell - s.width)
            if (r < s.height - 1) add(cell + s.width)
            if (c > 0) add(cell - 1)
            if (c < s.width - 1) add(cell + 1)
        }
    }

    /** Every line obeying the board, up to [cap] of them, as the cells they pass through. */
    private fun countPaths(s: SnapState, cap: Int): List<List<Int>> {
        val n = s.cellCount
        val seen = BooleanArray(n)
        val trail = IntArray(n)
        val found = mutableListOf<List<Int>>()

        fun walk(cell: Int, depth: Int) {
            if (found.size >= cap) return
            seen[cell] = true
            trail[depth - 1] = cell
            if (depth == n) {
                val order = (0 until n).mapNotNull { s.waypoints[trail[it]].takeIf { w -> w > 0 } }
                if (order == (1..order.size).toList() &&
                    order.size == s.waypoints.count { it > 0 }
                ) {
                    found += trail.toList()
                }
            } else {
                for (next in neighbours(s, cell)) if (!seen[next]) walk(next, depth + 1)
            }
            seen[cell] = false
        }

        walk(s.waypoints.indexOf(1), 1)
        return found
    }

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
            val paths = countPaths(s, cap = 2)
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
            assertEquals("snap/HARD/$seed", 1, countPaths(s, cap = 2).size)
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
        assertEquals(1, countPaths(s, cap = 2).size)
    }

    /** A line that skips a waypoint, or takes them out of order, must not count as solved. */
    @Test
    fun `the rules reject a line that ignores the numbers`() {
        val s = Snap.generate(7919L, Difficulty.STANDARD) as SnapState
        val answer = countPaths(s, cap = 1).single()
        assertTrue(s.copy(path = answer).solved)
        assertTrue("a reversed line meets the numbers backwards", !s.copy(path = answer.reversed()).solved)
        assertTrue("a short line covers nothing", !s.copy(path = answer.dropLast(1)).solved)
    }
}
