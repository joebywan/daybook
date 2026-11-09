package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.LoopEdges
import com.joebywan.daybook.core.Verdict
import com.joebywan.daybook.puzzles.Loop
import com.joebywan.daybook.puzzles.LoopLogic
import com.joebywan.daybook.puzzles.LoopState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopRulesTest {

    /** The loop round the squares marked `1` in [inside] (row by row), as an edge string of `1`s, by the oracle's own reading. */
    private fun around(n: Int, inside: String) = LoopOracle.loopOf(n, inside)

    private fun drawn(loop: String) = loop.map { if (it == '1') '-' else '.' }.joinToString("")

    @Test
    fun `each rule on its own refuses a loop`() {
        // The 3x3 board's border: every edge square has its outer sides, the middle none.
        val border = around(3, "111111111")
        val clues = LoopLogic.cluesOf(3, border)
        assertEquals("212101212", clues)
        assertTrue(LoopLogic.isSolved(3, clues, drawn(border)))
        // A number one off.
        assertFalse(LoopLogic.isSolved(3, "3........", drawn(border)))
        assertFalse(LoopLogic.isSolved(3, "....1....", drawn(border)))
        // Two loops, each fine for the numbers they touch.
        val two = around(4, "1100" + "1100" + "0011" + "0011")
        assertFalse("touching at a corner is two loops, or a dot with four lines", LoopLogic.isSolved(4, ".".repeat(16), drawn(two)))
        val apart = around(5, "11000" + "11000" + "00000" + "00011" + "00011")
        assertFalse(LoopLogic.isSolved(5, ".".repeat(25), drawn(apart)))
        // A path that does not close, and a branch.
        val open = drawn(border).toCharArray().also { it[0] = '.' }.concatToString()
        assertFalse(LoopLogic.isSolved(3, ".".repeat(9), open))
        val spur = drawn(border).toCharArray().also { it[LoopLogic.sides(3, 4)[0]] = '-' }.concatToString()
        assertFalse(LoopLogic.isSolved(3, ".".repeat(9), spur))
        // No lines at all is no loop, even with no numbers.
        assertFalse(LoopLogic.isSolved(3, ".".repeat(9), ".".repeat(24)))
        // Crosses never count, either way.
        assertTrue(LoopLogic.isSolved(3, clues, drawn(border).replace('.', 'x')))
    }

    @Test
    fun `a legal loop wins whether or not it is the stored one`() {
        // One 2 in the top-left corner of a 3x3: the border, and the loop round the top-left 2x2, both give it two sides.
        val clues = "2........"
        val stored = LoopState(3, clues, around(3, "111111111"))
        assertTrue(stored.copy(edges = drawn(around(3, "111111111"))).solved)
        assertTrue(stored.copy(edges = drawn(around(3, "110110000"))).solved)
        assertEquals(Verdict.Several, LoopLogic.solutions(3, clues))
        assertEquals(2, LoopOracle.answers(3, clues).size)
    }

    @Test
    fun `the count says none for a board with no loop`() {
        // A 0 beside a 3: the 3 needs the side they share.
        val clues = "30......."
        assertEquals(Verdict.None, LoopLogic.solutions(3, clues))
        assertTrue(LoopOracle.answers(3, clues).isEmpty())
    }

    @Test
    fun `the oracle agrees that generated boards have one answer, the stored loop`() {
        val samples = mapOf(Difficulty.STANDARD to 60, Difficulty.HARD to 30, Difficulty.EXPERT to 4)
        for ((difficulty, count) in samples) {
            var slowest = 0L
            for (day in 0 until count) {
                val s = Loop.generate(daySeed(day * 9, Loop.id, difficulty), difficulty) as LoopState
                val t0 = System.nanoTime()
                val found = LoopOracle.answers(s.size, s.clues)
                slowest = maxOf(slowest, (System.nanoTime() - t0) / 1_000_000)
                assertEquals("day $day ${difficulty.name}: answers", 1, found.size)
                assertEquals("day $day ${difficulty.name}: the oracle's loop is the stored one", s.loop, LoopOracle.loopOf(s.size, found[0]))
            }
            println("TIME oracle ${difficulty.name}: slowest of $count boards ${slowest}ms")
        }
    }

    @Test
    fun `the oracle and the count agree on small boards with any numbers`() {
        // Random clue sets on 3x3 and 4x4, most with none or several answers: the verdicts must match.
        val rng = com.joebywan.daybook.core.Rng(77)
        var several = 0
        var none = 0
        var one = 0
        repeat(400) {
            val n = if (rng.nextInt(2) == 0) 3 else 4
            val clues = CharArray(n * n) { if (rng.nextInt(10) < 4) '0' + rng.nextInt(4) else '.' }.concatToString()
            val oracle = LoopOracle.answers(n, clues, limit = 2)
            when (val v = LoopLogic.solutions(n, clues)) {
                Verdict.None -> { none++; assertTrue(clues, oracle.isEmpty()) }
                Verdict.Several -> { several++; assertEquals(clues, 2, oracle.size) }
                is Verdict.ExactlyOne -> { one++; assertEquals(clues, 1, oracle.size); assertEquals(clues, LoopOracle.loopOf(n, oracle[0]), v.value) }
            }
        }
        println("small boards: $one one, $several several, $none none")
        assertTrue(one > 10 && several > 10 && none > 10)
    }

    @Test
    fun `the stored loop solves its board, and the board starts empty`() {
        for (difficulty in Difficulty.entries) for (seed in 1L..15L) {
            val s = Loop.generate(seed, difficulty) as LoopState
            assertFalse(s.solved)
            assertTrue(LoopLogic.isSolved(s.size, s.clues, drawn(s.loop)))
        }
    }

    /**
     * A measurement and a gate, over a year of daily boards: every tier needs exactly its rung, the tiers get bigger, and
     * what they give away (numbers shown, as a share of the squares) and cost (draws, milliseconds) is printed and written
     * to `app/build/reports/loop-tiers.txt`.
     */
    @Test
    fun `a year of daily boards - every tier needs exactly its rung, and the tiers get bigger`() {
        val sides = Difficulty.entries.map { LoopLogic.specFor(it).side }
        assertEquals(sides.sorted().distinct(), sides)
        val report = StringBuilder("Loop, a year of daily boards per tier\n")
        for (difficulty in Difficulty.entries) {
            val spec = LoopLogic.specFor(difficulty)
            val shown = ArrayList<Double>()
            val length = ArrayList<Double>()
            val draws = ArrayList<Int>()
            val ms = ArrayList<Long>()
            for (day in 0 until 365) {
                val seed = daySeed(day, Loop.id, difficulty)
                val t0 = System.nanoTime()
                var got: LoopState? = null
                var a = 0
                while (got == null && a < LoopLogic.ATTEMPTS) got = LoopLogic.attempt(seed, difficulty, a++)
                ms += (System.nanoTime() - t0) / 1_000_000
                draws += a
                val s = got ?: error("day $day ${difficulty.name}: no draw accepted")
                assertEquals("day $day ${difficulty.name}", spec.needs, LoopLogic.depth(s))
                shown += 100.0 * s.clues.count { it != '.' } / (s.size * s.size)
                length += 100.0 * s.loop.count { it == '1' } / ((s.size + 1) * (s.size + 1))
            }
            ms.sort()
            draws.sort()
            report.appendLine(
                "%s %dx%d: numbers shown %.1f%% of squares (%.0f-%.0f), loop through %.0f%% of dots; draws mean %.2f max %d; ms median %d p95 %d max %d".format(
                    difficulty.name, spec.side, spec.side, shown.average(), shown.min(), shown.max(), length.average(),
                    draws.average(), draws.last(), ms[ms.size / 2], ms[ms.size * 95 / 100], ms.last(),
                )
            )
        }
        println(report)
        writeReport("loop-tiers.txt", report.toString())
    }

    @Test
    fun `the last resort is accepted at its tier`() {
        for (difficulty in Difficulty.entries) {
            val s = LoopLogic.lastResort(difficulty)
            assertTrue(difficulty.name, LoopLogic.accepts(s, LoopLogic.specFor(difficulty)))
            assertEquals(1, LoopOracle.answers(s.size, s.clues).size)
        }
    }

    @Test
    fun `a drag draws, a drag along a line rubs it out, and a tap crosses`() {
        val s = LoopState(3, "212101212", around(3, "111111111"))
        val g = LoopLogic.grid(3)
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
        assertEquals('-', crossed.drag(listOf(5, 6)).edges[g.between(5, 6)])
        assertTrue(s.drag(listOf(3)) === s)
    }

    @Test
    fun `the board's edges are the squares' sides, by the oracle's own reading`() {
        // Each square's four sides, as LoopLogic names them, are exactly the edges the oracle draws round that square alone.
        for (n in listOf(3, 5)) for (q in 0 until n * n) {
            val alone = CharArray(n * n) { if (it == q) '1' else '0' }.concatToString()
            val ring = around(n, alone)
            assertEquals(ring.indices.filter { ring[it] == '1' }.toSet(), LoopLogic.sides(n, q).toSet())
            assertEquals(LoopEdges.OPEN_MARK.toString().repeat(ring.length), LoopState(n, ".".repeat(n * n), ring).edges)
        }
    }
}
