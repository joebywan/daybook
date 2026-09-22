package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Kings
import com.joebywan.daybook.puzzles.KingsState
import com.joebywan.daybook.puzzles.Mark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The generator's contract, checked against rules written out again from scratch.
 *
 * Nothing here calls the production rule check or the production solver: a board that is wrong in
 * the same way the generator is wrong would sail past a test that reused them, which is how a Hard
 * board shipped with two kings in one column of its own stored answer. The checker and the counter
 * below are deliberately slow, obvious re-statements of the printed rules.
 */
class KingsRulesTest {

    private fun seeds(count: Int, difficulty: Difficulty) = (0 until count).map {
        DailySeed.seedFor(LocalDate.of(2026, 1, 1).plusDays(it.toLong()), "kings-probe", difficulty)
    }

    // ---- an independent rule checker ------------------------------------------------------

    /** Exactly one king per row, per column and per region, and no two kings touching. */
    private fun legal(n: Int, region: List<Int>, kings: Set<Int>): Boolean {
        if (kings.size != n) return false
        if (kings.map { it / n }.toSet().size != n) return false
        if (kings.map { it % n }.toSet().size != n) return false
        if (kings.map { region[it] }.toSet().size != n) return false
        for (a in kings) for (b in kings) {
            if (a == b) continue
            val dr = a / n - b / n
            val dc = a % n - b % n
            if (dr in -1..1 && dc in -1..1) return false
        }
        return true
    }

    /**
     * Every legal placement, found by brute force over the columns of each row rather than by the
     * generator's own pruned search.
     */
    private fun allSolutions(n: Int, region: List<Int>): List<Set<Int>> {
        val out = mutableListOf<Set<Int>>()
        val chosen = IntArray(n) { -1 }
        // Stops once the board is emphatically ambiguous; every assertion below is about there
        // being exactly one answer, and nine is already an answer to that.
        fun walk(row: Int) {
            if (out.size > 8) return
            if (row == n) {
                val kings = (0 until n).map { it * n + chosen[it] }.toSet()
                if (legal(n, region, kings)) out += kings
                return
            }
            for (c in 0 until n) {
                // Column and touching are the only constraints cheap enough to prune on here;
                // everything else is left to `legal` so the enumeration stays obviously complete.
                if ((0 until row).any { chosen[it] == c }) continue
                if (row > 0 && kotlin.math.abs(chosen[row - 1] - c) <= 1) continue
                chosen[row] = c
                walk(row + 1)
                chosen[row] = -1
            }
        }
        walk(0)
        return out
    }

    private fun place(state: KingsState, kings: Set<Int>) = state.copy(
        marks = state.marks.mapIndexed { i, _ -> if (i in kings) Mark.KING else Mark.EMPTY },
    )

    // ---- the generator ---------------------------------------------------------------------

    @Test
    fun `every board stores a legal answer and admits exactly one`() {
        for (difficulty in Difficulty.entries) {
            var illegal = 0
            var ambiguous = 0
            for (seed in seeds(30, difficulty)) {
                val state = Kings.generate(seed, difficulty) as KingsState
                val n = state.size

                assertEquals(
                    "kings/${difficulty.name}/$seed is not ${n}x$n", n * n, state.region.size,
                )
                assertEquals(
                    "kings/${difficulty.name}/$seed has the wrong number of regions",
                    n, state.region.toSet().size,
                )
                assertTrue(
                    "kings/${difficulty.name}/$seed has an unassigned square",
                    state.region.all { it in 0 until n },
                )
                assertTrue(
                    "kings/${difficulty.name}/$seed has a disconnected region",
                    (0 until n).all { connected(n, state.region, it) },
                )

                if (!legal(n, state.region, state.solution)) illegal++
                val solutions = allSolutions(n, state.region)
                if (solutions.size != 1) ambiguous++
            }
            assertEquals("kings/${difficulty.name}: stored answers that break a rule", 0, illegal)
            assertEquals("kings/${difficulty.name}: boards with a second answer", 0, ambiguous)
        }
    }

    /** A region a player cannot see as one shape is not a region. */
    private fun connected(n: Int, region: List<Int>, id: Int): Boolean {
        val cells = region.indices.filter { region[it] == id }
        if (cells.isEmpty()) return false
        val seen = mutableSetOf(cells.first())
        val queue = ArrayDeque(listOf(cells.first()))
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            val r = cell / n
            val c = cell % n
            val around = listOfNotNull(
                if (r > 0) cell - n else null,
                if (r < n - 1) cell + n else null,
                if (c > 0) cell - 1 else null,
                if (c < n - 1) cell + 1 else null,
            )
            for (next in around) if (region[next] == id && seen.add(next)) queue += next
        }
        return seen.size == cells.size
    }

    @Test
    fun `the proved path never abdicates to the last resort`() {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(30, difficulty)) {
                assertNotNull(
                    "kings/${difficulty.name}/$seed fell through to the unproved last resort",
                    Kings.generateVerified(seed, difficulty),
                )
            }
        }
    }

    // ---- the win condition ------------------------------------------------------------------

    /**
     * The bug a player actually hit: a board solved legally was refused because the placement was
     * not the one the generator happened to store. The answer used here is found independently, so
     * the assertion holds whichever of the board's legal placements that turns out to be.
     */
    @Test
    fun `a legal placement wins, whether or not it is the stored one`() {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(30, difficulty)) {
                val state = Kings.generate(seed, difficulty) as KingsState
                val found = allSolutions(state.size, state.region).singleOrNull()
                assertNotNull("kings/${difficulty.name}/$seed has no single answer", found)
                val solved = place(state, found!!)
                assertTrue(
                    "kings/${difficulty.name}/$seed refuses an independently found legal answer",
                    solved.solved,
                )
                assertTrue(
                    "kings/${difficulty.name}/$seed reports conflicts in a legal answer",
                    solved.conflicts().isEmpty(),
                )
            }
        }
    }

    /**
     * A 5x5 board with room to break one rule at a time. The regions cut across rows and columns,
     * so a check that stood a row in for the region would still fail here.
     */
    private val regions = listOf(
        0, 0, 1, 1, 1,
        0, 0, 1, 1, 1,
        2, 2, 2, 3, 3,
        2, 4, 4, 3, 3,
        4, 4, 4, 3, 3,
    )

    private fun board(kings: Set<Int>) = KingsState(
        size = 5,
        region = regions,
        marks = List(25) { if (it in kings) Mark.KING else Mark.EMPTY },
        solution = emptySet(),
    )

    /**
     * The stored answer here is deliberately empty: if it still decided anything, nothing below
     * could pass. Six placements are legal on this board, and every one of them must win.
     */
    @Test
    fun `every legal placement on this 5x5 is accepted`() {
        val answers = allSolutions(5, regions)
        assertTrue("the fixture board should admit several answers", answers.size > 1)
        for (answer in answers) assertTrue("$answer is legal but refused", board(answer).solved)
    }

    @Test
    fun `each rule on its own is enough to refuse a placement`() {
        // Each of these breaks exactly one rule; everything else about it is a legal placement.
        // Two kings in row 0, so row 1 has none.
        assertFalse("two kings in one row", board(setOf(0, 3, 11, 19, 22)).solved)
        // Two kings in column 0, so column 4 has none.
        assertFalse("two kings in one column", board(setOf(0, 7, 10, 18, 21)).solved)
        // Two kings in region 3, so region 2 has none.
        assertFalse("two kings in one region", board(setOf(0, 7, 14, 16, 23)).solved)
        // r2c1 and r3c2 touch diagonally; rows, columns and regions are all one each.
        assertFalse("two kings touching diagonally", board(setOf(0, 8, 11, 17, 24)).solved)
        // And a board with too few kings is not finished, however legal what is on it.
        assertFalse("only four kings", board(setOf(0, 8, 11, 24)).solved)
    }
}
