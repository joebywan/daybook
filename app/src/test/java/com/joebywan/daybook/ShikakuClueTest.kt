package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Block
import com.joebywan.daybook.puzzles.Shikaku
import com.joebywan.daybook.puzzles.ShikakuState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shikaku's smallest clue, and what it costs to have banned it.
 *
 * A clue of 1 is not a clue. It names the square it is printed on, so the rectangle is finished the
 * instant it is read and nothing is deduced; and it is the one rectangle a player cannot draw,
 * because a 1x1 drag is the degenerate drag and reads as a slipped tap. The generator is supposed
 * to make them unproducible rather than filter them out, by refusing any cut that would leave a
 * sliver, so the interesting question for this file is not only "are there 1s" but "did banning
 * them quietly break something else".
 *
 * Two things could have broken, and both are checked here rather than assumed. The clues must still
 * sum to the grid, or the slicer has started dropping cells instead of cutting them. And each board
 * must still have exactly one tiling — the weaker the clue set, the harder uniqueness is to reach,
 * and a generator that had started shipping ambiguous boards would fail no other test in the suite.
 *
 * The tiling count is deliberately *not* taken from the generator. Shikaku's own counter is the
 * thing that decided these boards were unique in the first place, so asking it again would only
 * confirm it agrees with itself. [countTilings] below is a second implementation on a different
 * principle: the production search picks the most-constrained clue and tries its precomputed
 * rectangles, while this one walks the grid in reading order and asks only what can cover the first
 * uncovered square. A board both agree is unique is unique for a reason, not by shared assumption.
 */
class ShikakuClueTest {

    private val seeds = (0 until 60).map {
        daySeed(it, "shikaku", Difficulty.STANDARD)
    }

    // ---- the rule -------------------------------------------------------------------------------

    @Test
    fun `no generated board carries a clue of 1`() {
        forEachBoard { label, state ->
            val ones = state.clues.withIndex().filter { it.value == 1 }
            assertTrue(
                "$label has ${ones.size} clue(s) of 1 at cells ${ones.map { it.index }}",
                ones.isEmpty(),
            )
        }
    }

    /**
     * The last resort is the board of one fixed seed, so it must itself be proved: this is what
     * stops it throwing. Checked here with this file's own counter, and for the rule above.
     */
    @Test
    fun `the last resort is proved on every tier and carries no clue of 1`() {
        for (difficulty in Difficulty.entries) {
            val state = Shikaku.lastResort(difficulty)
            val label = "shikaku-last-resort/${difficulty.name}"
            assertTrue("$label has a clue of 1", state.clues.none { it == 1 })
            assertEquals("$label clues do not sum to the grid", state.width * state.height, state.clues.filterNotNull().sum())
            assertEquals("$label is not uniquely tileable", 1, countTilings(state.width, state.height, state.clues))
        }
    }

    /**
     * Tilings are grown, not cut, so layouts with no cut across the whole grid (a pinwheel, at its
     * smallest) must turn up. Measured with this file's own checker: a tiling is a guillotine one if
     * some full row or column line inside its box crosses no rectangle and both sides are again.
     * Over a year of daily boards, 46 / 61 / 71% were non-guillotine when this was written.
     */
    @Test
    fun `tilings are not only guillotine cuts`() {
        for (difficulty in Difficulty.entries) {
            val boards = (0 until 60).map { Shikaku.generate(daySeed(it, "shikaku", difficulty), difficulty) as ShikakuState }
            val pinwheels = boards.count { !guillotine(it.solution, 0, 0, it.height - 1, it.width - 1) }
            assertTrue("$difficulty: only $pinwheels of ${boards.size} boards are non-guillotine", pinwheels * 4 >= boards.size)
        }
    }

    private fun guillotine(blocks: List<Block>, r0: Int, c0: Int, r1: Int, c1: Int): Boolean {
        val inside = blocks.filter { it.r0 >= r0 && it.r1 <= r1 && it.c0 >= c0 && it.c1 <= c1 }
        if (inside.size <= 1) return true
        for (cut in r0 until r1) {
            if (inside.none { it.r0 <= cut && it.r1 > cut }) return guillotine(inside, r0, c0, cut, c1) && guillotine(inside, cut + 1, c0, r1, c1)
        }
        for (cut in c0 until c1) {
            if (inside.none { it.c0 <= cut && it.c1 > cut }) return guillotine(inside, r0, c0, r1, cut) && guillotine(inside, r0, cut + 1, r1, c1)
        }
        return false
    }

    // ---- what the rule must not have broken -----------------------------------------------------

    @Test
    fun `clue areas still tile the whole grid exactly`() {
        forEachBoard { label, state ->
            assertEquals(
                "$label clues do not sum to the grid",
                state.width * state.height,
                state.clues.filterNotNull().sum(),
            )
            assertEquals(
                "$label has a clue that is not some rectangle's area",
                state.solution.map { it.area }.sorted(),
                state.clues.filterNotNull().sorted(),
            )
        }
    }

    @Test
    fun `every board still has exactly one tiling`() {
        forEachBoard { label, state ->
            assertEquals(
                "$label is not uniquely tileable",
                1,
                countTilings(state.width, state.height, state.clues),
            )
        }
    }

    /** The generator's own answer has to be a legal tiling, not merely the one it happened to cut. */
    @Test
    fun `the stored solution is itself a legal tiling`() {
        forEachBoard { label, state ->
            val cover = IntArray(state.width * state.height) { -1 }
            state.solution.forEachIndexed { index, block ->
                var clues = 0
                for (r in block.r0..block.r1) {
                    for (c in block.c0..block.c1) {
                        val cell = r * state.width + c
                        assertEquals("$label covers cell $cell twice", -1, cover[cell])
                        cover[cell] = index
                        if (state.clues[cell] != null) clues++
                    }
                }
                assertEquals("$label has a rectangle holding $clues clues", 1, clues)
            }
            assertTrue("$label leaves a cell uncovered", cover.none { it == -1 })
        }
    }

    // ---- helpers --------------------------------------------------------------------------------

    private fun forEachBoard(check: (String, ShikakuState) -> Unit) {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds) {
                val state = Shikaku.generate(seed, difficulty) as ShikakuState
                check("shikaku/${difficulty.name}/$seed", state)
            }
        }
    }

    /**
     * Counts perfect tilings, stopping at two, without reusing anything the generator knows.
     *
     * The whole search rests on one observation: fill the grid in reading order, and the rectangle
     * covering the first uncovered square must have that square as its *top-left* corner — any
     * other corner would put an earlier square inside it, and earlier squares are already taken. So
     * there is no need to pick a clue, rank candidates or precompute anything. Take the first hole,
     * try every rectangle rooted there that fits, holds exactly one clue, and whose area is that
     * clue, and recurse.
     */
    private fun countTilings(w: Int, h: Int, clues: List<Int?>): Int {
        val taken = BooleanArray(w * h)
        var found = 0

        fun fill(r0: Int, c0: Int, r1: Int, c1: Int, value: Boolean) {
            for (r in r0..r1) for (c in c0..c1) taken[r * w + c] = value
        }

        fun search() {
            if (found >= 2) return
            val hole = taken.indexOfFirst { !it }
            if (hole == -1) {
                found++
                return
            }
            val r0 = hole / w
            val c0 = hole % w
            for (rows in 1..h - r0) {
                val r1 = r0 + rows - 1
                // Growing downwards past a taken square is hopeless, and so is every taller one.
                if (taken[r1 * w + c0]) break
                for (cols in 1..w - c0) {
                    val c1 = c0 + cols - 1
                    if ((r0..r1).any { r -> taken[r * w + c1] }) break
                    val inside = (r0..r1).flatMap { r -> (c0..c1).mapNotNull { c -> clues[r * w + c] } }
                    // Wider can only swallow more clues, so one too many ends this row of tries.
                    if (inside.size > 1) break
                    if (inside.size == 1 && inside[0] == rows * cols) {
                        fill(r0, c0, r1, c1, true)
                        search()
                        fill(r0, c0, r1, c1, false)
                        if (found >= 2) return
                    }
                }
            }
        }

        search()
        return found
    }
}
