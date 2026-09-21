package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Pipes
import com.joebywan.daybook.puzzles.PipesState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The fill is the only feedback the player gets before the board solves, so it has to mean exactly
 * what the win condition means. These tests pin the two together from both ends: a board the fill
 * covers completely must be solved, and a board that looks finished but is really two rings must
 * not be — the case a fully-joined check alone would wave through.
 */
class PipesFlowTest {

    private val seeds = (0 until 5).map {
        DailySeed.seedFor(LocalDate.of(2026, 5, 2).plusDays(it.toLong()), "pipes", Difficulty.STANDARD)
    }

    /** A 3x3 snake through every cell: joined, connected, and therefore solved. */
    private val solvedBoard = PipesState(
        width = 3,
        height = 3,
        cells = listOf(2, 10, 12, 6, 10, 9, 3, 10, 8),
        source = 0,
    )

    /**
     * Two 2x2 rings side by side in a 4x2 grid. Every opening meets another, so "no loose ends"
     * is satisfied, yet the water can only ever reach half the board.
     */
    private val twoRings = PipesState(
        width = 4,
        height = 2,
        cells = listOf(6, 12, 6, 12, 3, 9, 3, 9),
        source = 0,
    )

    @Test
    fun `a fresh board fills from the source and no further than the board`() {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds) {
                val s = Pipes.generate(seed, difficulty) as PipesState
                val label = "${difficulty.name}/$seed"

                assertTrue("$label put the source off the board", s.source in s.cells.indices)

                val filled = Pipes.filled(s)
                assertTrue(
                    "$label filled cells that are not on the board",
                    s.cells.indices.toSet().containsAll(filled),
                )
                assertTrue("$label does not fill its own source", s.source in filled)
            }
        }
    }

    @Test
    fun `the source survives a rotation`() {
        val s = Pipes.generate(seeds.first(), Difficulty.HARD) as PipesState
        assertEquals(s.source, s.rotate(0).rotate(1).source)
    }

    @Test
    fun `a solved board is filled end to end`() {
        assertTrue("the hand-built board is not solved", solvedBoard.solved)
        assertEquals(solvedBoard.cells.indices.toSet(), Pipes.filled(solvedBoard))
    }

    @Test
    fun `the fill reaches every cell wherever the source sits`() {
        for (source in solvedBoard.cells.indices) {
            val s = solvedBoard.copy(source = source)
            assertEquals("source $source strands part of the board", s.cells.size, Pipes.filled(s).size)
        }
    }

    @Test
    fun `a fully joined but disconnected board is not solved`() {
        assertTrue("the two-ring board has a loose end", Pipes.fullyJoined(twoRings))
        assertFalse("two separate rings must not count as one network", Pipes.connected(twoRings))
        assertFalse(twoRings.solved)
        assertEquals("only the ring holding the source fills", setOf(0, 1, 4, 5), Pipes.filled(twoRings))
    }

    @Test
    fun `an unjoined opening does not carry the fill`() {
        // Turn the tile next to the source so its opening no longer meets the source's.
        val broken = solvedBoard.rotate(1)
        val filled = Pipes.filled(broken)

        assertFalse(broken.solved)
        assertTrue("the source itself always holds water", broken.source in filled)
        assertTrue(
            "a half-open join must not carry the fill",
            filled.size < broken.cells.size,
        )
    }
}
