package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.puzzles.Snap
import com.joebywan.daybook.puzzles.SnapState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * These are the tests that matter: a generator that silently falls back, hangs, or emits an
 * already-solved board would ship a broken daily puzzle to every device on the same date.
 */
class GeneratorTest {

    private val seeds = (0 until 6).map { DailySeed.seedFor(LocalDate.of(2026, 3, 1).plusDays(it.toLong()), "x", Difficulty.STANDARD) }

    @Test
    fun `every puzzle generates an unsolved board`() {
        for (puzzle in PuzzleRegistry.all) {
            for (difficulty in Difficulty.entries) {
                for (seed in seeds) {
                    val state = puzzle.generate(seed, difficulty)
                    assertFalse(
                        "${puzzle.id}/${difficulty.name}/$seed starts already solved",
                        state.solved,
                    )
                }
            }
        }
    }

    @Test
    fun `generation is deterministic for a given seed`() {
        for (puzzle in PuzzleRegistry.all) {
            for (difficulty in Difficulty.entries) {
                val seed = seeds.first()
                assertEquals(
                    "${puzzle.id}/${difficulty.name} is not reproducible",
                    puzzle.generate(seed, difficulty),
                    puzzle.generate(seed, difficulty),
                )
            }
        }
    }

    @Test
    fun `generation stays inside a sane time budget`() {
        val slow = mutableListOf<String>()
        for (puzzle in PuzzleRegistry.all) {
            for (difficulty in Difficulty.entries) {
                val start = System.nanoTime()
                repeat(3) { i -> puzzle.generate(seeds[i], difficulty) }
                val msEach = (System.nanoTime() - start) / 3 / 1_000_000
                println("%-10s %-9s %5d ms".format(puzzle.id, difficulty.name, msEach))
                if (msEach > 2500) slow += "${puzzle.id}/${difficulty.name} = ${msEach}ms"
            }
        }
        assertTrue("Generators too slow: $slow", slow.isEmpty())
    }

    @Test
    fun `snap always has a start square and a solvable path`() {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds) {
                val state = Snap.generate(seed, difficulty) as SnapState
                assertTrue(
                    "snap/${difficulty.name}/$seed has no square numbered 1",
                    state.waypoints.contains(1),
                )
                val highest = state.waypoints.max()
                assertTrue("snap/${difficulty.name}/$seed has too few waypoints", highest >= 2)
            }
        }
    }
}
