package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Mosaic
import com.joebywan.daybook.puzzles.MosaicMove
import com.joebywan.daybook.puzzles.MosaicState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

/**
 * Whether Mosaic's move limit is a *number* or a *constraint*.
 *
 * [MosaicFloodTest] already checks the limit is reachable, which is what stops a daily board being
 * impossible for everyone at once. It cannot catch the opposite failure, and the opposite failure
 * is the one that shipped: a Standard board was finished with four of its seven fills unspent.
 *
 * Two things could produce that, and only one of them turned out to be real. The generator's search
 * skipped every fill that merged nothing, so it proved a shortest line over a subset of the moves
 * and could in principle report an optimum that was too long; measurement says it never actually
 * did. What it did do was pile three spare fills on top of a four-fill optimum, which is not a
 * generous limit but an absent one. The search now covers every fill regardless, because a limit
 * players are held to should not rest on an unproved move-set argument, and this file is what keeps
 * it honest if anyone narrows the move set again.
 *
 * So everything here pushes from below. The generator's optimum is checked against a search written
 * in this file that knows nothing about how [Mosaic.solve] works — it moves by calling
 * [MosaicState.flood] and reads the board back through [MosaicState.area] — and that search is run
 * over *every* fill a player could make, which is precisely the set the old solver narrowed. A
 * claimed optimum that any of these searches beats by even one fill is a limit that does not
 * constrain the puzzle it is attached to.
 */
class MosaicOptimumTest {

    // ---- the generator's claims ---------------------------------------------------------------

    private fun seedsFrom(month: Int, count: Int, tier: Difficulty) =
        (0 until count).map {
            DailySeed.seedFor(LocalDate.of(2026, month, 1).plusDays(it.toLong()), "mosaic", tier)
        }

    /**
     * Expert gets fewer seeds than the other two on purpose. Refuting the fill below its optimum
     * means exhausting a full-move-set search a couple of dozen groups wide, and the independent
     * search in this file is deliberately naive — it carries no distance bound and no bitmasks, so
     * that a mistake in the puzzle's own cleverness cannot hide inside the check on it.
     */
    private fun tierSeeds(tier: Difficulty) = when (tier) {
        Difficulty.STANDARD -> seedsFrom(2, 24, tier)
        Difficulty.HARD -> seedsFrom(2, 12, tier)
        Difficulty.EXPERT -> seedsFrom(2, 3, tier)
    }

    private fun tierBoards(): List<Triple<Difficulty, Long, MosaicState>> =
        Difficulty.entries.flatMap { tier ->
            tierSeeds(tier).map { seed -> Triple(tier, seed, Mosaic.generate(seed, tier) as MosaicState) }
        }

    @Test
    fun `no board can be won in fewer fills than the generator proved`() {
        for ((tier, seed, board) in tierBoards()) {
            val label = "${tier.name}/$seed"
            val line = Mosaic.solve(board)
            assertNotNull("$label shipped without a proved line", line)
            val optimum = line!!.size

            assertFalse(
                "$label claims an optimum of $optimum but can be won in ${optimum - 1} — " +
                    "its limit of ${board.limit} is that much looser than it reads",
                winsWithin(board, optimum - 1),
            )
            assertTrue(
                "$label claims an optimum of $optimum that cannot actually be played",
                winsWithin(board, optimum),
            )
        }
    }

    @Test
    fun `the stored limit is still one the player can actually reach`() {
        for ((tier, seed, board) in tierBoards()) {
            val label = "${tier.name}/$seed"
            val line = Mosaic.solve(board)!!
            assertTrue("$label allows ${board.limit} fills but needs ${line.size}", line.size <= board.limit)
            assertTrue("$label: replaying its own proved line does not finish", replay(board, line).solved)
            assertTrue("$label: no win found inside its own limit", winsWithin(board, board.limit))
        }
    }

    /**
     * The limit has to bite. A board the player can finish with fills to spare several times over
     * is the complaint this whole file exists for, so the gap is asserted, not merely reported.
     */
    @Test
    fun `the limit is exactly the optimum on every tier`() {
        // Zero everywhere: the limit IS the optimum, so only a perfect line finishes.
        val slack = mapOf(Difficulty.STANDARD to 0, Difficulty.HARD to 0, Difficulty.EXPERT to 0)
        for ((tier, seed, board) in tierBoards()) {
            val optimum = Mosaic.solve(board)!!.size
            assertEquals(
                "${tier.name}/$seed hands out ${board.limit - optimum} spare fills over an optimum of $optimum",
                slack.getValue(tier),
                board.limit - optimum,
            )
        }
    }

    /**
     * A search that ran out of budget has to say nothing, not say its best guess.
     *
     * This is the guarantee the whole file depends on. The moment a truncated search is allowed to
     * hand back a length, every number here becomes an estimate and a daily board can go out with
     * a limit nobody can meet. Generation reseeds on null; a hint goes quiet on null.
     */
    @Test
    fun `a search that runs out of budget reports nothing at all`() {
        val board = Mosaic.generate(tierSeeds(Difficulty.EXPERT).first(), Difficulty.EXPERT) as MosaicState

        assertEquals("a starved search invented a line", null, Mosaic.solve(board, budget = 1))
        assertNotNull("the same board is unprovable at full budget", Mosaic.solve(board))
    }

    // ---- the generator's search, against a breadth-first one that shares none of it -------------

    /**
     * The check that matters. Breadth-first is the one search that cannot get an optimum wrong by
     * being clever — no heuristic to be inadmissible, no transposition table to poison, no move
     * ordering to prune with — so on boards small enough to run it exhaustively it is the ground
     * truth [Mosaic.solve] is measured against.
     *
     * The hand-built boards are tiny on purpose, since breadth-first over the full move set holds a
     * whole layer of distinct boards in memory. The random ones are where the real coverage is:
     * 120 of them a run, every one of which would catch a solver that reported a line one fill too
     * long or one fill too short.
     */
    @Test
    fun `on small boards the solver matches an exhaustive breadth-first search`() {
        for ((name, board) in handBuilt()) {
            val line = Mosaic.solve(board)
            assertNotNull("$name was not solved at all", line)
            assertTrue("$name: the reported line does not finish", replay(board, line!!).solved)
            val truth = breadthFirst(board)
            assertEquals("$name: the solver disagrees with breadth-first", truth, line.size)
            agreeWithBreadthFirst(name, board, truth)
        }
    }

    @Test
    fun `on random small boards the solver matches an exhaustive breadth-first search`() {
        val rng = Random(20260922)
        repeat(120) { round ->
            val width = 3 + rng.nextInt(2)
            val height = 3 + rng.nextInt(2)
            val colours = 2 + rng.nextInt(2)
            val cells = List(width * height) { rng.nextInt(colours) }
            val board = MosaicState(width, height, colours, width * height, cells)
            if (board.solved) return@repeat

            val line = Mosaic.solve(board)
            assertNotNull("round $round ($cells) was not solved at all", line)
            assertTrue("round $round ($cells): the reported line does not finish", replay(board, line!!).solved)
            val truth = breadthFirst(board)
            assertEquals("round $round ($cells): the solver disagrees with breadth-first", truth, line.size)
            agreeWithBreadthFirst("round $round ($cells)", board, truth)
        }
    }

    /**
     * Pins [winsWithin] to breadth-first truth on a board small enough to have both.
     *
     * Without this the tier tests above would be circular in the one direction that matters: a
     * refuter that pruned too hard would answer "no win in that many fills" to everything and
     * would pass every assertion made of it. Here it has to answer no at one fill below the true
     * optimum and yes at the optimum, on every board the exhaustive search has an answer for.
     */
    private fun agreeWithBreadthFirst(label: String, board: MosaicState, truth: Int) {
        assertTrue("$label: the refuter cannot find the $truth-fill win breadth-first proved", winsWithin(board, truth))
        if (truth > 0) {
            assertFalse(
                "$label: the refuter believes in a ${truth - 1}-fill win breadth-first ruled out",
                winsWithin(board, truth - 1),
            )
        }
    }

    /**
     * Shapes whose optimum can be worked out by hand, so a disagreement here is readable rather
     * than just red.
     *
     * They are deliberately awkward in different ways: one where a single fill takes the whole
     * board, one where nothing can be hurried because every colour has to be retired in turn, and
     * an island walled off from its own colour — the case that motivates offering fills which merge
     * nothing, since recolouring the wall is the only thing that ever reaches it.
     */
    private fun handBuilt(): List<Pair<String, MosaicState>> = listOf(
        // Four corners of one colour round a cross of another: one fill of the centre takes the lot.
        "cross" to MosaicState(3, 3, 2, 9, listOf(0, 1, 0, 1, 1, 1, 0, 1, 0)),
        // A three-colour stripe: every colour but one has to be retired, and nothing can be hurried.
        "stripes" to MosaicState(3, 3, 3, 9, listOf(0, 0, 0, 1, 1, 1, 2, 2, 2)),
        // A lone green square walled off by red, with green either side of the wall.
        "island" to MosaicState(5, 1, 3, 9, listOf(0, 1, 2, 1, 0)),
        "pocket" to MosaicState(
            4, 3, 3, 12,
            listOf(
                0, 1, 2, 0,
                1, 2, 1, 2,
                2, 0, 1, 0,
            ),
        ),
        "checker" to MosaicState(
            4, 4, 2, 16,
            listOf(
                0, 1, 0, 1,
                1, 0, 1, 0,
                0, 1, 0, 1,
                1, 0, 1, 0,
            ),
        ),
    )

    // ---- an independent engine, written against the public board only --------------------------

    private fun replay(board: MosaicState, line: List<MosaicMove>): MosaicState =
        line.fold(board) { acc, move -> acc.flood(move.cell, move.colour) }

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
     * Every fill the player could make: each area, each colour but the one it already wears.
     *
     * No filtering whatsoever, which is the entire point of this file — the set the shipped
     * generator searched was this set minus the fills that merge nothing.
     */
    private fun everyFill(board: MosaicState): List<MosaicMove> =
        areaCells(board).flatMap { rep ->
            (0 until board.colours).filter { it != board.cells[rep] }.map { MosaicMove(rep, it) }
        }

    /**
     * The true shortest line, by exhaustive breadth-first search over [everyFill].
     *
     * Only usable on the tiny boards above: it holds a whole layer of distinct boards in memory and
     * the layers grow by roughly `areas x (colours - 1)` each time. That is the cost of a search
     * with nothing clever in it, and the reason it is trustworthy.
     */
    private fun breadthFirst(board: MosaicState): Int {
        if (board.solved) return 0
        var frontier = listOf(board)
        val seen = hashSetOf(board.cells)
        var depth = 0
        while (frontier.isNotEmpty()) {
            depth++
            val next = mutableListOf<MosaicState>()
            for (state in frontier) {
                for (move in everyFill(state)) {
                    val moved = state.flood(move.cell, move.colour)
                    if (moved.solved) return depth
                    if (seen.add(moved.cells)) next += moved
                }
            }
            frontier = next
        }
        throw AssertionError("a board with no win at all: ${board.cells}")
    }

    /**
     * Whether the board can be won in at most [fills], searching [everyFill] to that depth.
     *
     * Depth-first rather than breadth-first because the tier boards are far too wide to hold a
     * layer of, and memoised on the cell colours because fills commute constantly. The memo records
     * the deepest refuted search per board, which is sound for the same reason it is in the puzzle:
     * "no win in r fills from here" does not stop being true.
     *
     * Running out of budget raises rather than returns false. A refutation that quietly gave up
     * would turn this file into the thing it is here to catch.
     */
    private fun winsWithin(board: MosaicState, fills: Int): Boolean {
        val memo = HashMap<List<Int>, Int>()
        val budget = intArrayOf(BUDGET)

        fun search(state: MosaicState, left: Int): Boolean {
            if (state.solved) return true
            if (left <= 0) return false
            // Every colour on the board but the last has to be retired, one fill apiece at best.
            if (state.cells.distinct().size - 1 > left) return false
            if (--budget[0] < 0) throw AssertionError("the independent search ran out of budget")
            if ((memo[state.cells] ?: -1) >= left) return false

            val tried = everyFill(state)
                .map { it to state.flood(it.cell, it.colour) }
                .sortedByDescending { (move, next) -> next.area(move.cell).size }
            for ((_, next) in tried) if (search(next, left - 1)) return true

            memo[state.cells] = left
            return false
        }

        return search(board, fills)
    }

    private companion object {
        /** Wide enough for an Expert refutation, small enough that a runaway test still ends. */
        const val BUDGET = 4_000_000
    }
}
