package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Lits
import com.joebywan.daybook.puzzles.LitsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The two promises LITS broke, both of which cost a player a finished board.
 *
 * The win condition used to be `shaded == solution`, so a second legal answer was reported as
 * wrong; that is pinned here. The other, a uniqueness check that abandoned its search at a node
 * cap and let ambiguous boards ship, is pinned by [LitsUniquenessTest], whose [LitsOracle] has no
 * budget and no connectivity rule and walks a year of daily boards on every tier.
 */
class LitsRulesTest {

    // ---- the win condition is the rules -------------------------------------------------------

    @Test
    fun `the stored solution satisfies the rules on every generated board`() {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(6, difficulty, "lits", LocalDate.of(2026, 7, 1))) {
                val state = Lits.generate(seed, difficulty) as LitsState
                assertTrue(
                    "lits/${difficulty.name}/$seed ships a solution that breaks its own rules",
                    Lits.isSolved(state.width, state.height, state.region, state.solution),
                )
                assertTrue(
                    "lits/${difficulty.name}/$seed is not solved by its own solution",
                    state.copy(shaded = state.solution).solved,
                )
            }
        }
    }

    @Test
    fun `a generated board does not start solved`() {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(6, difficulty, "lits", LocalDate.of(2026, 7, 1))) {
                val state = Lits.generate(seed, difficulty) as LitsState
                assertFalse("lits/${difficulty.name}/$seed starts solved", state.solved)
            }
        }
    }

    @Test
    fun `a legal board is accepted whether or not it is the stored answer`() {
        assertTrue(Lits.isSolved(4, 4, TWO_COLUMNS, shade(16, 0, 4, 5, 9, 6, 7, 11, 15)))
    }

    @Test
    fun `a fully shaded two by two is rejected`() {
        // Every other rule holds: an S and an I, four squares each, joined up and different
        // letters. Only the block of four at 1-2-5-6 is wrong.
        assertFalse(Lits.isSolved(4, 4, TWO_COLUMNS, shade(16, 1, 4, 5, 8, 2, 6, 10, 14)))
    }

    @Test
    fun `a region with the wrong number of shaded squares is rejected`() {
        assertFalse(
            "three squares in a region is not a tetromino",
            Lits.isSolved(4, 4, TWO_COLUMNS, shade(16, 0, 4, 5, 9, 6, 7, 11)),
        )
        assertFalse(
            "five squares in a region is not a tetromino either",
            Lits.isSolved(4, 4, TWO_COLUMNS, shade(16, 0, 4, 5, 9, 3, 6, 7, 11, 15)),
        )
        assertFalse(
            "a region left blank is not merely unfinished, it is wrong",
            Lits.isSolved(4, 4, TWO_COLUMNS, shade(16, 0, 4, 5, 9)),
        )
    }

    @Test
    fun `shading that falls into two separate pieces is accepted`() {
        // Two legal I tetrominoes, four squares each, no 2x2, and too far apart to count as one
        // same-letter contact. They never touch, which this version of LITS does not require.
        assertTrue(Lits.isSolved(4, 4, TWO_COLUMNS, shade(16, 0, 4, 8, 12, 3, 7, 11, 15)))
    }

    @Test
    fun `the disconnected answer a player was refused on 23 Sept 2026 Expert is accepted`() {
        // Transcribed from the player's screenshot. The T in the middle (rows 3 to 5) touches no
        // other shading; every region still holds a legal tetromino. The regions are written out
        // rather than regenerated from the seed, because capping region size changed the board
        // that date now produces.
        val region = listOf(
            2, 1, 1, 0, 0, 3, 3, 3,
            2, 2, 1, 1, 0, 0, 3, 3,
            2, 5, 5, 1, 0, 9, 3, 3,
            2, 5, 5, 5, 0, 9, 3, 4,
            8, 8, 8, 8, 9, 9, 4, 4,
            8, 8, 7, 7, 9, 9, 9, 4,
            7, 8, 7, 7, 7, 6, 6, 4,
            7, 7, 7, 6, 6, 6, 6, 4,
        )
        val state = LitsState(8, 8, region, List(64) { false }, List(64) { false })
        val picture = listOf(
            "I S S . T . . S",
            "I . S S T T S S",
            "I . T . T . S .",
            "I T T T . T . I",
            "S . . . T T . I",
            "S S . T . T . I",
            ". S T T T . L I",
            ". . . . L L L .",
        )
        val shaded = picture.flatMap { row -> row.split(" ").map { it != "." } }
        assertEquals(state.width * state.height, shaded.size)
        val played = state.copy(shaded = shaded)
        assertEquals(
            "the screenshot's letters should be what the board shows",
            picture.flatMap { row -> row.split(" ").map { if (it == ".") null else it } },
            played.letters().map { it?.name },
        )
        assertTrue(played.solved)
    }

    @Test
    fun `two touching tetrominoes of the same letter are rejected`() {
        // Two L tetrominoes — one a rotation of the other, which the rule counts as the same
        // letter — meeting edge to edge at squares 0 and 1.
        assertFalse(Lits.isSolved(5, 4, STAIRCASE, shade(20, 0, 5, 10, 11, 1, 2, 3, 8)))
    }

    // ---- region size ---------------------------------------------------------------------------

    @Test
    fun `generated regions hold at most seven squares`() {
        // Uncapped, about one region in six came out at eight squares or more, up to fourteen:
        // nearly room for two tetrominoes, most of it squares that only ever get crossed off.
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(10, difficulty, "lits", LocalDate.of(2026, 7, 1))) {
                val state = Lits.generate(seed, difficulty) as LitsState
                val largest = state.region.groupingBy { it }.eachCount().values.max()
                assertTrue("lits/${difficulty.name}/$seed has a $largest-square region", largest <= 7)
            }
        }
    }

    // ---- dragging --------------------------------------------------------------------------------

    @Test
    fun `a drag sets every square it crosses the same way, in one state`() {
        val blank = LitsState(4, 4, TWO_COLUMNS, List(16) { false }, List(16) { false })
        val start = blank.toggle(5)
        val dragged = start.paint(listOf(4, 5, 6), on = true)
        assertEquals(shade(16, 4, 5, 6), dragged.shaded)
        assertEquals("one move per square that changed", start.moves + 2, dragged.moves)

        val cleared = dragged.paint(listOf(5, 6, 7), on = false)
        assertEquals(shade(16, 4), cleared.shaded)
        assertEquals(dragged.moves + 2, cleared.moves)
    }

    @Test
    fun `a drag that changes nothing is not a new state`() {
        val board = LitsState(4, 4, TWO_COLUMNS, shade(16, 0, 1), List(16) { false })
        assertTrue(board.paint(listOf(0, 1), on = true) === board)
        assertTrue(board.paint(listOf(2, 3), on = false) === board)
    }

    private fun shade(size: Int, vararg cells: Int): List<Boolean> {
        val on = cells.toSet()
        return List(size) { it in on }
    }

    private companion object {
        /** A 4x4 grid split down the middle, two regions of eight squares each. */
        val TWO_COLUMNS = listOf(
            0, 0, 1, 1,
            0, 0, 1, 1,
            0, 0, 1, 1,
            0, 0, 1, 1,
        )

        /** A 5x4 grid split along a diagonal, so both halves can hold an L. */
        val STAIRCASE = listOf(
            0, 1, 1, 1, 1,
            0, 0, 1, 1, 1,
            0, 0, 0, 1, 1,
            0, 0, 0, 1, 1,
        )
    }
}
