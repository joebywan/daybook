package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.Lamps
import com.joebywan.daybook.puzzles.LampsLogic
import com.joebywan.daybook.puzzles.LampsState
import com.joebywan.daybook.puzzles.LampsVerdict
import com.joebywan.daybook.puzzles.withLamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class LampsRulesTest {

    private fun withLamps(s: LampsState, lamps: String) =
        s.copy(marks = lamps.map { if (it == '1') LampsLogic.LAMP_MARK else LampsLogic.NONE }.joinToString(""))

    @Test
    fun `each rule on its own refuses a board`() {
        val board = Lamps.tutorialBoard()
        val answer = withLamps(board, Lamps.TUTORIAL_SOLUTION)
        assertTrue(answer.solved)
        // A square left dark: without the lamp at 23 nothing lights it (its column stops at the 0, its row at a wall).
        assertFalse(answer.withLamp(23).solved)
        // Two lamps lighting each other, and nothing else wrong: 22 is lit by 17 and 23 already.
        assertTrue(LampsLogic.clashes(5, Lamps.TUTORIAL_WALLS, answer.withLamp(22).lamps()).isNotEmpty())
        assertFalse(answer.withLamp(22).solved)
        // A lamp on a wall is never placed.
        assertTrue(answer.withLamp(0) === answer)
        assertFalse(board.solved)
        // A number with the wrong count and nothing else wrong: a 1 in a 2x2 corner, every square lit, no clash.
        val corner = LampsState(2, "1...", "0001")
        assertFalse(withLamps(corner, "0001").solved) // none beside it
        assertFalse(withLamps(corner, "0110").solved) // two beside it
        assertTrue(LampsLogic.isSolved(2, "#...", booleanArrayOf(false, false, false, true)))
    }

    @Test
    fun `a legal answer that is not the stored one wins`() {
        // An open 2x2 has two answers: the stored diagonal and the other one.
        val s = LampsState(2, "....", "1001")
        assertTrue(withLamps(s, "0110").solved)
        assertTrue(withLamps(s, "1001").solved)
        assertFalse(withLamps(s, "1000").solved)
        assertEquals(LampsVerdict.Several, LampsLogic.countSolutions(2, "...."))
    }

    @Test
    fun `the rule check agrees with the oracle on every lamp set of small boards`() {
        val rng = Rng(7)
        repeat(60) {
            val n = 3
            val walls = randomWalls(rng, n, numberAll = rng.nextBoolean())
            val legal = LampsOracle.solutions(n, walls).toSet()
            for (mask in 0 until (1 shl (n * n))) {
                val lamps = BooleanArray(n * n) { mask shr it and 1 == 1 && walls[it] == '.' }
                if ((0 until n * n).any { mask shr it and 1 == 1 && walls[it] != '.' }) continue
                val key = CharArray(n * n) { if (lamps[it]) '1' else '0' }.concatToString()
                assertEquals("$walls $key", key in legal, LampsLogic.isSolved(n, walls, lamps))
            }
        }
    }

    /** Random walls (a quarter), numbered from a random maximal lamp set, then numbers dropped at random. */
    private fun randomWalls(rng: Rng, n: Int, numberAll: Boolean): String {
        val w = CharArray(n * n) { if (rng.nextInt(4) == 0) '#' else '.' }
        val lamp = BooleanArray(n * n)
        val walls = w.concatToString()
        for (i in rng.shuffled((0 until n * n).filter { w[it] == '.' })) {
            if (LampsLogic.lit(n, walls, lamp)[i]) continue
            lamp[i] = true
        }
        for (i in 0 until n * n) if (w[i] == '#' && (numberAll || rng.nextBoolean())) w[i] = '0' + LampsLogic.orth(n, i).count { lamp[it] }
        return w.concatToString()
    }

    @Test
    fun `the exhaustive count agrees with the oracle, several answers included`() {
        val rng = Rng(11)
        var several = 0
        repeat(300) {
            val n = 4 + rng.nextInt(3)
            val walls = randomWalls(rng, n, numberAll = rng.nextInt(3) == 0)
            val oracle = LampsOracle.solutions(n, walls, limit = 2)
            val verdict = LampsLogic.countSolutions(n, walls)
            when (oracle.size) {
                0 -> assertEquals(walls, LampsVerdict.None, verdict)
                1 -> assertEquals(walls, LampsVerdict.ExactlyOne(oracle[0]), verdict)
                else -> { several++; assertEquals(walls, LampsVerdict.Several, verdict) }
            }
        }
        assertTrue("too few boards with several answers to mean anything: $several", several > 50)
    }

    @Test
    fun `the ladder is sound - what it settles every answer agrees with`() {
        val rng = Rng(5)
        repeat(200) {
            val n = 5 + rng.nextInt(2)
            val walls = randomWalls(rng, n, numberAll = rng.nextBoolean())
            val answers = LampsOracle.solutions(n, walls, limit = 50)
            if (answers.isEmpty() || answers.size == 50) return@repeat
            val g = LampsLogic.Geo(n, walls)
            val k = IntArray(n * n)
            LampsLogic.settle(g, k, LampsLogic.WHAT_IF_RUNG)
            for (a in answers) for (i in 0 until n * n) {
                if (k[i] == LampsLogic.LAMP) assertEquals("$walls $i", '1', a[i])
                if (k[i] == LampsLogic.NO) assertEquals("$walls $i", '0', a[i])
            }
        }
    }

    @Test
    fun `generated boards have one answer, the stored one, by the oracle`() {
        for ((difficulty, count) in listOf(Difficulty.STANDARD to 30, Difficulty.HARD to 8, Difficulty.EXPERT to 3)) {
            for (seed in 1L..count) {
                val s = Lamps.generate(seed, difficulty) as LampsState
                assertEquals("$difficulty $seed", listOf(s.solution), LampsOracle.solutions(s.size, s.walls, limit = 2))
                assertTrue(withLamps(s, s.solution).solved)
            }
        }
    }

    @Test
    fun `each last resort is accepted at its own tier`() {
        for (d in Difficulty.entries) {
            val s = LampsLogic.lastResort(d)
            assertEquals(LampsLogic.specFor(d).side, s.size)
            assertTrue("$d", LampsLogic.accepts(s, LampsLogic.specFor(d)))
        }
    }

    /**
     * A year of daily boards per tier: each needs exactly its tier's rung (direct / look-ahead / what-if), so a harder
     * tier cannot quietly become easier. Writes the measurements to `build/reports/lamps-generation.txt`.
     */
    @Test
    fun `a year of daily boards - every tier needs exactly its rung, and the tiers get harder`() {
        val report = StringBuilder()
        val start = LocalDate.of(2026, 1, 1)
        for (d in Difficulty.entries) {
            val spec = LampsLogic.specFor(d)
            val ms = ArrayList<Double>()
            var walls = 0
            var numbered = 0
            var squares = 0
            for (day in 0 until 365L) {
                val seed = DailySeed.seedFor(start.plusDays(day), Lamps.id, d)
                val t = System.nanoTime()
                val s = Lamps.generate(seed, d) as LampsState
                ms += (System.nanoTime() - t) / 1e6
                assertEquals("$d day $day", spec.needs, LampsLogic.depth(s))
                walls += s.walls.count { it != LampsLogic.WHITE }
                numbered += s.walls.count { it in '0'..'4' }
                squares += s.size * s.size
            }
            ms.sort()
            report.appendLine(
                "${d.name}: ${spec.side}x${spec.side}, walls ${"%.1f".format(100.0 * walls / squares)}% of squares, " +
                    "numbered ${"%.1f".format(100.0 * numbered / walls)}% of walls (${"%.1f".format(100.0 * numbered / squares)}% of squares), " +
                    "ms median ${"%.1f".format(ms[ms.size / 2])} p95 ${"%.1f".format(ms[ms.size * 95 / 100])} max ${"%.1f".format(ms.last())}",
            )
        }
        print(report)
        File("build/reports").mkdirs()
        File("build/reports/lamps-generation.txt").writeText(report.toString())
        assertTrue(Difficulty.entries.map { LampsLogic.specFor(it).needs }.zipWithNext().all { (a, b) -> a < b })
    }
}
