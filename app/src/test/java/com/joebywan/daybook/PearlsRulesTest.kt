package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Pearls
import com.joebywan.daybook.puzzles.PearlsGrid
import com.joebywan.daybook.puzzles.PearlsLogic
import com.joebywan.daybook.puzzles.PearlsState
import com.joebywan.daybook.puzzles.PearlsVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PearlsRulesTest {

    /** Lines through the squares of [loop] (each a neighbour of the last, closing back to the first), as the player's edge string. */
    private fun drawn(n: Int, loop: List<Int>): String {
        val g = PearlsGrid.of(n)
        val marks = CharArray(g.edgeCount) { '.' }
        for (i in loop.indices) marks[g.between(loop[i], loop[(i + 1) % loop.size])] = '-'
        return marks.concatToString()
    }

    private fun loopString(n: Int, loop: List<Int>) = drawn(n, loop).map { if (it == '-') '1' else '0' }.joinToString("")

    /** The border of a 4x4 board, clockwise from the top-left corner. */
    private val border4 = listOf(0, 1, 2, 3, 7, 11, 15, 14, 13, 12, 8, 4)

    private fun pearlsAt(n: Int, vararg at: Pair<Int, Char>): String {
        val p = CharArray(n * n) { '.' }
        for ((i, c) in at) p[i] = c
        return p.concatToString()
    }

    @Test
    fun `each rule on its own refuses a loop`() {
        val lines = drawn(4, border4)
        // A black pearl on a corner whose neighbours run straight, a white one on a straight beside the corner: legal.
        assertTrue(PearlsLogic.isSolved(4, pearlsAt(4, 0 to 'b', 1 to 'w'), lines))
        // A black pearl where the loop runs straight.
        assertFalse(PearlsLogic.isSolved(4, pearlsAt(4, 1 to 'b'), lines))
        // A black pearl on a corner of a 2x2 loop: its neighbours turn too.
        assertFalse(PearlsLogic.isSolved(4, pearlsAt(4, 0 to 'b'), drawn(4, listOf(0, 1, 5, 4))))
        // A white pearl on a corner.
        assertFalse(PearlsLogic.isSolved(4, pearlsAt(4, 0 to 'w'), lines))
        // A white pearl on a straight with straights either side (a 5x5 border's middle square).
        val border5 = listOf(0, 1, 2, 3, 4, 9, 14, 19, 24, 23, 22, 21, 20, 15, 10, 5)
        assertFalse(PearlsLogic.isSolved(5, pearlsAt(5, 2 to 'w'), drawn(5, border5)))
        assertTrue(PearlsLogic.isSolved(5, pearlsAt(5, 1 to 'w'), drawn(5, border5)))
        // A pearl the loop leaves out.
        assertFalse(PearlsLogic.isSolved(4, pearlsAt(4, 5 to 'w'), lines))
        // Two loops, each fine on its own.
        val two = drawn(4, listOf(0, 1, 5, 4)).zip(drawn(4, listOf(10, 11, 15, 14))) { a, b -> if (a == '-' || b == '-') '-' else '.' }.joinToString("")
        assertFalse(PearlsLogic.isSolved(4, ".".repeat(16), two))
        // A path that does not close.
        val open = lines.toCharArray().also { it[PearlsGrid.of(4).between(0, 1)] = '.' }.concatToString()
        assertFalse(PearlsLogic.isSolved(4, pearlsAt(4, 2 to 'w'), open))
        // A branch: the border plus a spur.
        val spur = lines.toCharArray().also { it[PearlsGrid.of(4).between(1, 5)] = '-' }.concatToString()
        assertFalse(PearlsLogic.isSolved(4, pearlsAt(4, 2 to 'w'), spur))
        // Crosses never count, either way.
        assertTrue(PearlsLogic.isSolved(4, pearlsAt(4, 0 to 'b'), lines.replace('.', 'x')))
    }

    @Test
    fun `a legal loop wins whether or not it is the stored one`() {
        // One white pearl on the top edge: the 4x4 border and the 3x3 loop in the corner both keep it.
        val pearls = pearlsAt(4, 1 to 'w')
        val small = listOf(0, 1, 2, 6, 10, 9, 8, 4)
        val stored = PearlsState(4, pearls, loopString(4, border4))
        assertTrue(stored.copy(edges = drawn(4, border4)).solved)
        assertTrue(stored.copy(edges = drawn(4, small)).solved)
        assertEquals(PearlsVerdict.Several, PearlsLogic.solutions(4, pearls))
        assertTrue(PearlsOracle.answers(4, pearls).size == 2)
    }

    @Test
    fun `the count says none for a board with no loop`() {
        // Two black pearls side by side can never both turn with straight arms.
        val pearls = pearlsAt(4, 5 to 'b', 6 to 'b')
        assertEquals(PearlsVerdict.None, PearlsLogic.solutions(4, pearls))
        assertTrue(PearlsOracle.answers(4, pearls).isEmpty())
    }

    @Test
    fun `the oracle agrees that generated boards have one answer, the stored loop`() {
        val samples = mapOf(Difficulty.STANDARD to 60, Difficulty.HARD to 40, Difficulty.EXPERT to 25)
        for ((difficulty, count) in samples) {
            var slowest = 0L
            for (day in 0 until count) {
                val date = LocalDate.of(2026, 1, 1).plusDays(day * 9L)
                val s = Pearls.generate(DailySeed.seedFor(date, Pearls.id, difficulty), difficulty) as PearlsState
                val t0 = System.nanoTime()
                val found = PearlsOracle.answers(s.size, s.pearls)
                val ms = (System.nanoTime() - t0) / 1_000_000
                slowest = maxOf(slowest, ms)
                assertEquals("$date ${difficulty.name}: answers", 1, found.size)
                assertEquals("$date ${difficulty.name}: the oracle's loop is the stored one", s.loop, loopString(s.size, found[0]))
            }
            println("TIME oracle ${difficulty.name}: slowest of $count boards ${slowest}ms")
        }
    }

    @Test
    fun `the stored loop solves its board, and the board starts empty`() {
        for (difficulty in Difficulty.entries) for (seed in 1L..20L) {
            val s = Pearls.generate(seed, difficulty) as PearlsState
            assertFalse(s.solved)
            assertTrue(PearlsLogic.isSolved(s.size, s.pearls, s.loop.map { if (it == '1') '-' else '.' }.joinToString("")))
        }
    }

    @Test
    fun `the tiers get harder in the order they are offered`() {
        // Bigger, and each needs a rung the one before it never does: the gate is checked on the board itself.
        val sides = Difficulty.entries.map { PearlsLogic.specFor(it).side }
        assertEquals(sides.sorted(), sides)
        for (difficulty in Difficulty.entries) for (seed in 1L..30L) {
            val s = Pearls.generate(seed * 101, difficulty) as PearlsState
            assertEquals("${difficulty.name} seed $seed", PearlsLogic.specFor(difficulty).needs, PearlsLogic.depth(s))
        }
    }

    @Test
    fun `the last resort is accepted at its tier`() {
        for (difficulty in Difficulty.entries) {
            val s = PearlsLogic.lastResort(difficulty)
            assertTrue(difficulty.name, PearlsLogic.accepts(s, PearlsLogic.specFor(difficulty)))
            assertEquals(1, PearlsOracle.answers(s.size, s.pearls).size)
        }
    }

    @Test
    fun `a drag draws, a drag along a line rubs it out, and a tap crosses`() {
        val s = PearlsState(4, pearlsAt(4, 0 to 'b'), loopString(4, border4))
        val g = PearlsGrid.of(4)
        val drawnOnce = s.drag(listOf(0, 1, 2))
        assertEquals('-', drawnOnce.edges[g.between(0, 1)])
        assertEquals('-', drawnOnce.edges[g.between(1, 2)])
        assertEquals(2, drawnOnce.moves)
        val rubbed = drawnOnce.drag(listOf(2, 1))
        assertEquals('.', rubbed.edges[g.between(1, 2)])
        assertEquals('-', rubbed.edges[g.between(0, 1)])
        val crossed = s.tap(g.between(5, 6))
        assertEquals('x', crossed.edges[g.between(5, 6)])
        assertEquals('.', crossed.tap(g.between(5, 6)).edges[g.between(5, 6)])
        // A drag over a cross draws through it.
        assertEquals('-', crossed.drag(listOf(5, 6)).edges[g.between(5, 6)])
        // A tap on a line rubs it out.
        assertEquals('.', drawnOnce.tap(g.between(0, 1)).edges[g.between(0, 1)])
        // A drag that never leaves its square is no move.
        assertTrue(s.drag(listOf(3)) === s)
    }

    @Test
    fun `generation time - a year of seeds per tier`() {
        for (difficulty in Difficulty.entries) {
            val times = ArrayList<Double>()
            for (day in 0 until 365) {
                val seed = DailySeed.seedFor(LocalDate.of(2026, 1, 1).plusDays(day.toLong()), Pearls.id, difficulty)
                val t0 = System.nanoTime()
                assertNotNull(PearlsLogic.generateVerified(seed, difficulty))
                times += (System.nanoTime() - t0) / 1e6
            }
            times.sort()
            println(
                "TIME ${difficulty.name} median ${"%.1f".format(times[182])} p95 ${"%.1f".format(times[346])} " +
                    "max ${"%.1f".format(times.last())} ms",
            )
        }
    }
}
