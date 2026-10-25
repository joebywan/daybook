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
 * apart from the code it checks: the rule checker reads each line as a string, and the answers are
 * counted by [MamboOracle], which fills the board cell by cell where the generator propagates.
 * That every generated board has one answer is `MamboRulesTest`'s.
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
        assertEquals(1, MamboOracle.countAnswers(s.size, s.cells, s.links))

        // Two answers by hand: with nothing printed, a 4x4 has many.
        val a = listOf("SSMM", "MMSS", "SMSM", "MSMS").joinToString("").map { if (it == 'S') Sym.SUN else Sym.MOON }
        val b = listOf("SMSM", "MSMS", "SSMM", "MMSS").joinToString("").map { if (it == 'S') Sym.SUN else Sym.MOON }
        assertNotEquals(a, b)
        assertTrue(MamboOracle.countAnswers(4, List(16) { Sym.NONE }, emptyList(), cap = 3) >= 3)
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
