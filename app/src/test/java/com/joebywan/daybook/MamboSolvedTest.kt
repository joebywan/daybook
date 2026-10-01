package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Link
import com.joebywan.daybook.puzzles.Mambo
import com.joebywan.daybook.puzzles.MamboState
import com.joebywan.daybook.puzzles.Sym
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * `solved` must be the rules, not a comparison with the stored answer. Everything here is written
 * apart from the code it checks: the rule checker reads each line as a string, and the answer
 * counter fills the board row by row from a table of legal rows, where the generator propagates.
 */
class MamboSolvedTest {

    // ---- an independent rule checker ----------------------------------------------------------

    private fun ch(s: Sym) = when (s) { Sym.SUN -> 'S'; Sym.MOON -> 'M'; Sym.NONE -> '.' }

    private fun legal(n: Int, g: List<Sym>, links: List<Link>): Boolean {
        val lines = (0 until n).map { r -> (0 until n).map { c -> ch(g[r * n + c]) }.joinToString("") } +
            (0 until n).map { c -> (0 until n).map { r -> ch(g[r * n + c]) }.joinToString("") }
        for (l in lines) {
            if ('.' in l) return false
            if (l.count { it == 'S' } != n / 2) return false
            if ("SSS" in l || "MMM" in l) return false
        }
        return links.all { (g[it.a] == g[it.b]) == it.same }
    }

    // ---- an independent answer counter: row by row, from every legal row ----------------------

    private fun legalRows(n: Int): List<List<Sym>> {
        val out = mutableListOf<List<Sym>>()
        for (mask in 0 until (1 shl n)) {
            if (Integer.bitCount(mask) != n / 2) continue
            val row = (0 until n).map { if (mask shr it and 1 == 1) Sym.SUN else Sym.MOON }
            if ((2 until n).any { row[it] == row[it - 1] && row[it] == row[it - 2] }) continue
            out += row
        }
        return out
    }

    /** Number of legal grids that honour the clues and links, stopping at [cap]. */
    private fun countAnswers(n: Int, clue: List<Sym>, links: List<Link>, cap: Int = 2): Int {
        val rows = legalRows(n)
        val grid = MutableList(n * n) { Sym.NONE }
        var found = 0
        fun go(r: Int) {
            if (found >= cap) return
            if (r == n) {
                if (legal(n, grid, links)) found++
                return
            }
            for (row in rows) {
                if ((0 until n).any { clue[r * n + it] != Sym.NONE && clue[r * n + it] != row[it] }) continue
                for (c in 0 until n) grid[r * n + c] = row[c]
                // Prune on columns so far: no triple, no more than half of a symbol.
                var ok = true
                for (c in 0 until n) {
                    val suns = (0..r).count { grid[it * n + c] == Sym.SUN }
                    if (suns > n / 2 || r + 1 - suns > n / 2) ok = false
                    if (r >= 2 && grid[r * n + c] == grid[(r - 1) * n + c] && grid[r * n + c] == grid[(r - 2) * n + c]) ok = false
                }
                for (l in links) if (l.a / n <= r && l.b / n <= r && (grid[l.a] == grid[l.b]) != l.same) ok = false
                if (ok) go(r + 1)
            }
            for (c in 0 until n) grid[r * n + c] = Sym.NONE
        }
        go(0)
        return found
    }

    private fun seeds(tier: Difficulty, count: Int) = (0 until count).map {
        DailySeed.seedFor(LocalDate.of(2026, 1, 1).plusDays(it.toLong()), "mambo", tier)
    }

    // ---- the premise: does every generated board have exactly one answer? ----------------------

    @Test
    fun `every generated board has exactly one answer`() {
        for (tier in Difficulty.entries) {
            var checked = 0
            for (seed in seeds(tier, 300)) {
                val s = Mambo.generate(seed, tier) as MamboState
                val clues = s.cells
                assertTrue("$tier/$seed stored answer breaks the rules", legal(s.size, s.solution, s.links))
                assertEquals("$tier/$seed has several answers", 1, countAnswers(s.size, clues, s.links))
                checked++
            }
            assertEquals(300, checked)
        }
    }

    // ---- solved is the rules ------------------------------------------------------------------

    private fun empty4(links: List<Link> = emptyList()) = MamboState(
        size = 4, givens = List(16) { false }, cells = List(16) { Sym.NONE }, links = links,
        solution = List(16) { Sym.NONE },
    )

    @Test
    fun `solved agrees with the checker on every 4x4 grid`() {
        val links = listOf(Link(0, 1, true), Link(5, 9, false))
        for (withLinks in listOf(emptyList(), links)) {
            val base = empty4(withLinks)
            var accepted = 0
            for (mask in 0 until (1 shl 16)) {
                val cells = List(16) { if (mask shr it and 1 == 1) Sym.SUN else Sym.MOON }
                val expect = legal(4, cells, withLinks)
                assertEquals("mask $mask", expect, base.copy(cells = cells).solved)
                if (expect) accepted++
            }
            assertTrue(accepted > 2)
        }
    }

    @Test
    fun `a legal answer other than the stored one is solved`() {
        val s = Mambo.generate(DailySeed.seedFor(LocalDate.of(2026, 3, 1), "mambo", Difficulty.STANDARD), Difficulty.STANDARD) as MamboState
        assertEquals(1, countAnswers(s.size, s.cells, s.links))

        // Two answers by hand: with nothing printed, a 4x4 has many.
        val a = listOf("SSMM", "MMSS", "SMSM", "MSMS").joinToString("").map { if (it == 'S') Sym.SUN else Sym.MOON }
        val b = listOf("SMSM", "MSMS", "SSMM", "MMSS").joinToString("").map { if (it == 'S') Sym.SUN else Sym.MOON }
        assertNotEquals(a, b)
        assertTrue(countAnswers(4, List(16) { Sym.NONE }, emptyList(), cap = 3) >= 3)
        assertTrue(legal(4, a, emptyList()) && legal(4, b, emptyList()))
        val board = empty4().copy(solution = a)
        assertTrue(board.copy(cells = a).solved)
        assertTrue("a valid answer that is not the stored one", board.copy(cells = b).solved)
    }

    @Test
    fun `wrong full boards are rejected`() {
        val s = Mambo.generate(DailySeed.seedFor(LocalDate.of(2026, 3, 2), "mambo", Difficulty.HARD), Difficulty.HARD) as MamboState
        assertTrue(s.copy(cells = s.solution).solved)
        // Flipping any one cell breaks balance.
        for (i in s.solution.indices) {
            val cells = s.solution.toMutableList().also { it[i] = it[i].other() }
            assertFalse("flip $i", s.copy(cells = cells).solved)
        }
        // A board with a blank is not solved.
        assertFalse(s.copy(cells = s.solution.toMutableList().also { it[0] = Sym.NONE }).solved)
        // A balanced board with three alike, and one that is balanced but breaks a link.
        val triple = listOf("SSSMMM", "MMMSSS", "SSMMSM", "MMSSMS", "SMSMSM", "MSMSMS").joinToString("").map { if (it == 'S') Sym.SUN else Sym.MOON }
        val six = MamboState(6, List(36) { false }, triple, emptyList(), triple)
        assertFalse(six.solved)
        val a = listOf("SSMM", "MMSS", "SMSM", "MSMS").joinToString("").map { if (it == 'S') Sym.SUN else Sym.MOON }
        assertTrue(empty4().copy(cells = a).solved)
        assertFalse(empty4(listOf(Link(0, 1, false))).copy(cells = a).solved)
        assertFalse(empty4(listOf(Link(0, 2, true))).copy(cells = a).solved)
    }

    // ---- the teacher must not call a legal alternative wrong -----------------------------------

    @Test
    fun `the teacher does not flag a symbol that some answer keeps`() {
        val a = listOf("SSMM", "MMSS", "SMSM", "MSMS").joinToString("").map { if (it == 'S') Sym.SUN else Sym.MOON }
        val b = listOf("SMSM", "MSMS", "SSMM", "MMSS").joinToString("").map { if (it == 'S') Sym.SUN else Sym.MOON }
        // The stored answer is a; the player has laid most of b, a different legal answer.
        val s = empty4().copy(solution = a, cells = b.toMutableList().also { it[15] = Sym.NONE })
        val step = Mambo.teach(s)
        assertTrue("a legal alternative was called a mistake", step == null || !step.mistake)
    }
}
