package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Mosaic
import com.joebywan.daybook.puzzles.MosaicMove
import com.joebywan.daybook.puzzles.MosaicState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Mosaic is the one puzzle here that can be *unwinnable* rather than merely unsolved: the move
 * limit is a number the generator computes, and if it computes it wrong the daily board is
 * impossible for everyone on the planet at once. So the limit is not taken on trust anywhere in
 * this file — every generated board is beaten again by a search written against the public board
 * API alone, which never asks [Mosaic.solve] what it thinks.
 */
class MosaicFloodTest {

    private val seeds = (0 until 6).map {
        DailySeed.seedFor(LocalDate.of(2026, 5, 3).plusDays(it.toLong()), "mosaic", Difficulty.HARD)
    }

    private fun boards(): List<Triple<Difficulty, Long, MosaicState>> =
        Difficulty.entries.flatMap { difficulty ->
            seeds.map { seed ->
                Triple(difficulty, seed, Mosaic.generate(seed, difficulty) as MosaicState)
            }
        }

    @Test
    fun `a generated board is never already one colour`() {
        for ((difficulty, seed, board) in boards()) {
            val label = "${difficulty.name}/$seed"
            assertFalse("$label starts solved", board.solved)
            assertTrue("$label has only one area", board.areaCount() > 1)
            assertTrue("$label has no fills to spend", board.limit > 0)
            assertFalse("$label starts out of fills", board.failed)
        }
    }

    @Test
    fun `a fill swallows every neighbouring area already in that colour`() {
        // Green, red, green in a row: three areas, and one fill of the middle one leaves exactly
        // one. Small enough that the expected area count is obvious by eye.
        val strip = MosaicState(width = 3, height = 1, colours = 2, limit = 3, cells = listOf(0, 1, 0))
        assertEquals(3, strip.areaCount())

        val merged = strip.flood(1, 0)

        assertEquals("the two greens did not merge with the flooded middle", 1, merged.areaCount())
        assertTrue(merged.solved)
        assertEquals(1, merged.moves)
    }

    @Test
    fun `one fill can swallow several areas at once`() {
        // A plus of red inside green: flooding one arm green merges it with the surrounding green
        // but leaves the rest of the plus alone, so the count drops by exactly one.
        val cells = listOf(
            0, 1, 0,
            1, 1, 1,
            0, 1, 0,
        )
        val board = MosaicState(width = 3, height = 3, colours = 2, limit = 4, cells = cells)
        assertEquals("the four green corners and the red plus", 5, board.areaCount())

        val after = board.flood(4, 0)

        assertEquals("the plus should have joined every corner at once", 1, after.areaCount())
    }

    @Test
    fun `pouring an area its own colour is not a move`() {
        for ((difficulty, seed, board) in boards()) {
            val label = "${difficulty.name}/$seed"
            val cell = board.cells.indices.first()
            // Same instance, not merely an equal one: the play screen pushes an undo entry for
            // every state it is handed, so this has to be something the board can decline to emit.
            assertSame("$label spent a fill on a no-op", board, board.flood(cell, board.cells[cell]))
        }
    }

    @Test
    fun `the line the generator proved wins inside the limit`() {
        for ((difficulty, seed, board) in boards()) {
            val label = "${difficulty.name}/$seed"
            val line = Mosaic.solve(board)
            assertNotNull("$label shipped without a proved line", line)

            val finished = play(board, line!!)

            assertTrue("$label does not finish on its own optimal line", finished.solved)
            assertEquals("$label counted a fill it did not play", line.size, finished.moves)
            assertTrue("$label needs ${line.size} fills but allows ${board.limit}", line.size <= board.limit)
        }
    }

    @Test
    fun `the limit is reachable by a search that never asks the generator`() {
        for ((difficulty, seed, board) in boards()) {
            val label = "${difficulty.name}/$seed"
            val budget = intArrayOf(60_000)

            val line = winWithin(board, board.limit, budget)

            assertNotNull("$label: no win found inside its own limit of ${board.limit}", line)
            assertTrue("$label: found line is longer than the limit", line!!.size <= board.limit)
            assertTrue("$label: the found line does not actually finish", play(board, line).solved)
        }
    }

    @Test
    fun `a board is failed once the fills run out unsolved`() {
        val board = Mosaic.generate(seeds.first(), Difficulty.EXPERT) as MosaicState

        val spent = board.copy(moves = board.limit)

        assertFalse(spent.solved)
        assertTrue("a board past its limit has to say it is lost", spent.failed)
        assertFalse("one fill short of the limit is still a live game", board.copy(moves = board.limit - 1).failed)
    }

    @Test
    fun `a won board is never failed even at the limit`() {
        val flat = MosaicState(width = 2, height = 1, colours = 2, limit = 1, cells = listOf(0, 0), moves = 1)

        assertTrue(flat.solved)
        assertFalse("winning on the last fill is a win, not a loss", flat.failed)
    }

    @Test
    fun `a hint spends a fill on an optimal move`() {
        for ((difficulty, seed, board) in boards()) {
            val label = "${difficulty.name}/$seed"
            val before = Mosaic.solve(board)!!.size

            val hinted = Mosaic.hint(board) as MosaicState?

            assertNotNull("$label offered no hint", hinted)
            assertEquals("$label: a hint must cost exactly one fill", board.moves + 1, hinted!!.moves)
            assertEquals(
                "$label: the hint did not shorten the remaining line, so it was a guess",
                before - 1,
                Mosaic.solve(hinted)!!.size,
            )
        }
    }

    @Test
    fun `a hint is recomputed for the board in front of the player`() {
        val board = Mosaic.generate(seeds.first(), Difficulty.HARD) as MosaicState
        // Deliberately off the generator's own line: pour a colour into an area the proved line
        // never touches first, then check the hint still knows what to do from there.
        val strayed = board.flood(board.cells.lastIndex, (board.cells.last() + 1) % board.colours)

        val hinted = Mosaic.hint(strayed) as MosaicState?

        assertNotNull("a wandered board got no hint", hinted)
        assertEquals(
            "the hint replayed a stored line instead of re-solving",
            Mosaic.solve(strayed)!!.size - 1,
            Mosaic.solve(hinted!!)!!.size,
        )
    }

    @Test
    fun `a solved board is offered no hint`() {
        val done = MosaicState(width = 2, height = 1, colours = 2, limit = 3, cells = listOf(1, 1))

        assertTrue(done.solved)
        assertEquals(null, Mosaic.hint(done))
    }

    // ---- an independent solver, written against the public board only -------------------------

    private fun play(board: MosaicState, line: List<MosaicMove>): MosaicState =
        line.fold(board) { acc, move -> acc.flood(move.cell, move.colour) }

    /**
     * A win in at most [fills], or null. Shares nothing with [Mosaic.solve] but the rules: it moves
     * by calling [MosaicState.flood] and reads the board back through [MosaicState.area], so a
     * mistake in the puzzle's own search cannot hide a mistake here.
     *
     * Depth-first, biggest resulting blob first, with the one bound that needs no theory behind it
     * — every colour still on the board but the last costs a fill. It is looking for *a* win inside
     * the stated limit rather than the shortest one, which is what makes it cheap enough to run on
     * every generated board.
     */
    private fun winWithin(board: MosaicState, fills: Int, budget: IntArray): List<MosaicMove>? {
        if (board.solved) return emptyList()
        if (fills <= 0) return null
        if (board.cells.distinct().size - 1 > fills) return null
        if (--budget[0] < 0) return null

        val options = mutableListOf<Pair<MosaicMove, MosaicState>>()
        for (rep in areaCells(board)) {
            for (colour in neighbourColours(board, rep)) {
                options += MosaicMove(rep, colour) to board.flood(rep, colour)
            }
        }
        options.sortByDescending { (move, next) -> next.area(move.cell).size }

        for ((move, next) in options) {
            val rest = winWithin(next, fills - 1, budget) ?: continue
            return listOf(move) + rest
        }
        return null
    }

    /** One cell out of each area on the board. */
    private fun areaCells(board: MosaicState): List<Int> {
        val seen = BooleanArray(board.cells.size)
        val out = mutableListOf<Int>()
        for (cell in board.cells.indices) {
            if (seen[cell]) continue
            out += cell
            for (member in board.area(cell)) seen[member] = true
        }
        return out
    }

    /**
     * Colours held by the areas touching this one. Pouring anything else merges nothing, and a fill
     * that merges nothing can always be swapped for one that does, so they are not worth the branch.
     */
    private fun neighbourColours(board: MosaicState, rep: Int): Set<Int> {
        val out = mutableSetOf<Int>()
        for (cell in board.area(rep)) {
            val r = cell / board.width
            val c = cell % board.width
            if (r > 0) out += board.cells[cell - board.width]
            if (r < board.height - 1) out += board.cells[cell + board.width]
            if (c > 0) out += board.cells[cell - 1]
            if (c < board.width - 1) out += board.cells[cell + 1]
        }
        return out - board.cells[rep]
    }
}
