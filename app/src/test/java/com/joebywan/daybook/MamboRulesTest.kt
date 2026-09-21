package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Broken
import com.joebywan.daybook.puzzles.Link
import com.joebywan.daybook.puzzles.Mambo
import com.joebywan.daybook.puzzles.MamboState
import com.joebywan.daybook.puzzles.Sym
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Two promises Mambo makes to the player, both of which it used to break: every board can be
 * reasoned out without guessing, and the feedback never leaks the answer.
 *
 * The solver below is written out again here rather than reusing the generator's, so a board that
 * only *its own* propagation can finish would still fail this test.
 */
class MamboRulesTest {

    private val seeds = (0 until 6).map {
        DailySeed.seedFor(LocalDate.of(2026, 3, 1).plusDays(it.toLong()), "x", Difficulty.STANDARD)
    }

    @Test
    fun `every generated board is solvable by propagation alone`() {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds) {
                val state = Mambo.generate(seed, difficulty) as MamboState
                val reasoned = solveByRules(state)
                assertEquals(
                    "mambo/${difficulty.name}/$seed needs a guess",
                    state.solution,
                    reasoned,
                )
            }
        }
    }

    @Test
    fun `every generated board opens with filled squares to anchor the eye`() {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds) {
                val state = Mambo.generate(seed, difficulty) as MamboState
                assertTrue(
                    "mambo/${difficulty.name}/$seed starts with ${state.givens.count { it }} givens",
                    state.givens.count { it } >= 2,
                )
            }
        }
    }

    @Test
    fun `a generated board flags nothing before it is touched`() {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds) {
                val state = Mambo.generate(seed, difficulty) as MamboState
                assertEquals(emptyList<Any>(), state.violations())
                assertEquals(emptyList<Any>(), state.copy(cells = state.solution).violations())
            }
        }
    }

    @Test
    fun `a cell that merely differs from the solution is not flagged`() {
        // The opposite symbol in an otherwise empty grid breaks nothing visible: no run of three,
        // no line past half, no link. The player is entitled to hold it until it collides.
        val board = board(cells = at(0 to Sym.MOON))
        assertTrue(board.cells[0] != board.solution[0])
        assertEquals(emptyList<Any>(), board.violations())
    }

    @Test
    fun `three identical symbols in a line are flagged as a triple`() {
        val board = board(cells = at(0 to Sym.SUN, 1 to Sym.SUN, 2 to Sym.SUN))
        val triples = board.violations().filter { it.rule == Broken.TRIPLE }
        assertEquals(listOf(listOf(0, 1, 2)), triples.map { it.cells })
    }

    @Test
    fun `a column of three identical symbols is flagged too`() {
        val board = board(cells = at(0 to Sym.MOON, 4 to Sym.MOON, 8 to Sym.MOON))
        val triples = board.violations().filter { it.rule == Broken.TRIPLE }
        assertEquals(listOf(listOf(0, 4, 8)), triples.map { it.cells })
    }

    @Test
    fun `a line holding more than half of one symbol is flagged whole`() {
        // Suns in three of the four cells, with a moon between them so no run of three forms.
        val board = board(cells = at(0 to Sym.SUN, 1 to Sym.SUN, 2 to Sym.MOON, 3 to Sym.SUN))
        val violations = board.violations()
        assertTrue(violations.none { it.rule == Broken.TRIPLE })
        assertEquals(
            listOf(listOf(0, 1, 2, 3)),
            violations.filter { it.rule == Broken.BALANCE }.map { it.cells },
        )
    }

    @Test
    fun `a broken link is flagged and a satisfied one is not`() {
        val differing = at(0 to Sym.SUN, 1 to Sym.MOON)

        val brokenSame = board(cells = differing, links = listOf(Link(0, 1, same = true)))
        assertEquals(
            listOf(listOf(0, 1)),
            brokenSame.violations().filter { it.rule == Broken.LINK }.map { it.cells },
        )

        val honoured = board(cells = differing, links = listOf(Link(0, 1, same = false)))
        assertEquals(emptyList<Any>(), honoured.violations())

        // One end still empty says nothing yet, so it must not be flagged.
        val halfFilled = board(cells = at(0 to Sym.SUN), links = listOf(Link(0, 1, same = true)))
        assertEquals(emptyList<Any>(), halfFilled.violations())
    }

    // ---- helpers ----------------------------------------------------------------------------

    /** A legal 4x4 answer: rows and columns balanced, no three in a line. */
    private val answer = listOf(
        Sym.SUN, Sym.SUN, Sym.MOON, Sym.MOON,
        Sym.MOON, Sym.MOON, Sym.SUN, Sym.SUN,
        Sym.SUN, Sym.MOON, Sym.SUN, Sym.MOON,
        Sym.MOON, Sym.SUN, Sym.MOON, Sym.SUN,
    )

    private fun at(vararg filled: Pair<Int, Sym>): List<Sym> {
        val cells = MutableList(16) { Sym.NONE }
        filled.forEach { (i, sym) -> cells[i] = sym }
        return cells
    }

    private fun board(cells: List<Sym>, links: List<Link> = emptyList()) = MamboState(
        size = 4,
        givens = List(16) { false },
        cells = cells,
        links = links,
        solution = answer,
    )

    /**
     * Fills what the three rules force, and returns the finished grid — or null if the board
     * stalls, which is exactly the state that would make a player guess.
     */
    private fun solveByRules(state: MamboState): List<Sym>? {
        val n = state.size
        val half = n / 2
        val grid = state.cells.toMutableList()
        var changed = true

        fun put(i: Int, sym: Sym) {
            if (grid[i] == Sym.NONE) {
                grid[i] = sym
                changed = true
            }
        }

        while (changed) {
            changed = false

            for (r in 0 until n) for (c in 0 until n) {
                val i = r * n + c
                val runs = buildList {
                    if (c + 2 < n) add(Triple(i, i + 1, i + 2))
                    if (r + 2 < n) add(Triple(i, i + n, i + 2 * n))
                }
                for ((a, b, d) in runs) {
                    if (grid[a] != Sym.NONE && grid[a] == grid[b]) put(d, grid[a].other())
                    if (grid[b] != Sym.NONE && grid[b] == grid[d]) put(a, grid[b].other())
                    if (grid[a] != Sym.NONE && grid[a] == grid[d]) put(b, grid[a].other())
                }
            }

            for (line in 0 until n) {
                val row = (0 until n).map { line * n + it }
                val column = (0 until n).map { it * n + line }
                for (idx in listOf(row, column)) {
                    for (sym in listOf(Sym.SUN, Sym.MOON)) {
                        if (idx.count { grid[it] == sym } == half) {
                            idx.forEach { put(it, sym.other()) }
                        }
                    }
                }
            }

            for (link in state.links) {
                val a = grid[link.a]
                val b = grid[link.b]
                if (a != Sym.NONE) put(link.b, if (link.same) a else a.other())
                if (b != Sym.NONE) put(link.a, if (link.same) b else b.other())
            }
        }

        return if (grid.contains(Sym.NONE)) null else grid.toList()
    }
}
