package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.Sudoku
import com.joebywan.daybook.puzzles.SudokuState
import com.joebywan.daybook.puzzles.SudokuTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Sudoku's generator: symmetric givens, one answer by a solver that shares nothing with [Sudoku.countSolutions], and
 * each tier at its own depth of the teacher's ladder (`Sudoku.generateVerified`'s gate). The year of daily boards that
 * never falls back is `FallbackTest`'s.
 */
class SudokuRulesTest {

    private val start = LocalDate.of(2026, 1, 1)

    private fun daily(day: Long, d: Difficulty) = DailySeed.seedFor(start.plusDays(day), Sudoku.id, d)

    /** Naive reading-order search, counting up to two: no candidate masks, no most-constrained cell, no shared code. */
    private fun oracleCount(g: IntArray, from: Int = 0): Int {
        val i = (from until 81).firstOrNull { g[it] == 0 } ?: return 1
        val r = i / 9
        val c = i % 9
        var n = 0
        for (d in 1..9) {
            val clash = (0 until 9).any { k ->
                g[r * 9 + k] == d || g[k * 9 + c] == d || g[(r / 3 * 3 + k / 3) * 9 + c / 3 * 3 + k % 3] == d
            }
            if (clash) continue
            g[i] = d
            n += oracleCount(g, i + 1)
            g[i] = 0
            if (n >= 2) break
        }
        return n
    }

    @Test
    fun `givens are symmetric, true to the answer, and have one answer by an independent solver`() {
        for (d in Difficulty.entries) for (day in 0 until 25L) {
            val s = Sudoku.generate(daily(day, d), d) as SudokuState
            val where = "${d.name} day $day"
            for (i in 0 until 81) {
                assertEquals("$where: symmetric at $i", s.givens[i], s.givens[80 - i])
                assertEquals("$where: given $i", if (s.givens[i]) s.solution[i] else 0, s.cells[i])
            }
            assertTrue(where, Sudoku.isSolved(s.solution))
            assertEquals("$where: answers", 1, oracleCount(s.cells.toIntArray()))
        }
    }

    /** The deepest technique a walk by hints alone (the player's path, `Sudoku.teach`) needs, or "fallback". */
    private fun deepest(start: SudokuState): String {
        var s = start
        var deepest = -1
        while (!s.solved) {
            val step = Sudoku.teach(s)!!
            if (step.technique == SudokuTeacher.FALLBACK) return SudokuTeacher.FALLBACK
            deepest = maxOf(deepest, SudokuTeacher.TECHNIQUES.indexOf(step.technique))
            s = step.apply(s) as SudokuState
        }
        return SudokuTeacher.TECHNIQUES[deepest]
    }

    @Test
    fun `each tier needs its own depth of the ladder, and none falls back`() {
        val t = SudokuTeacher.TECHNIQUES
        for (d in Difficulty.entries) for (day in 0 until 120L) {
            val deepest = deepest(Sudoku.generate(daily(day, d), d) as SudokuState)
            val ok = when (d) {
                Difficulty.STANDARD -> t.indexOf(deepest) <= t.indexOf(SudokuTeacher.HIDDEN_SINGLE_LINE)
                Difficulty.HARD -> deepest == SudokuTeacher.NAKED_SINGLE
                Difficulty.EXPERT -> deepest != SudokuTeacher.FALLBACK && t.indexOf(deepest) >= t.indexOf(SudokuTeacher.LOCKED)
            }
            assertTrue("${d.name} day $day needed $deepest", ok)
        }
    }

    /** The dig's shortcut must give the teacher's own verdict, at every ceiling, on boards from real digs. */
    @Test
    fun `the dig's shortcut agrees with the teacher's walk`() {
        val rng = Rng(7)
        val ceilings = listOf(SudokuTeacher.HIDDEN_SINGLE_LINE, SudokuTeacher.NAKED_SINGLE, SudokuTeacher.WHAT_IF)
        var both = 0
        var neither = 0
        repeat(40) {
            val solution = Sudoku.fullGrid(rng)
            val p = solution.toMutableList()
            for (i in rng.shuffled((0..40).toList())) {
                p[i] = 0
                p[80 - i] = 0
                if (Sudoku.countSolutions(p) != 1) {
                    p[i] = solution[i]
                    p[80 - i] = solution[80 - i]
                    continue
                }
                for (c in ceilings) {
                    val fast = Sudoku.finishes(p, c)
                    assertEquals("$c on ${p.joinToString("")}", SudokuTeacher.depth(p, c) != null, fast)
                    if (fast) both++ else neither++
                }
                if (!Sudoku.finishes(p, SudokuTeacher.WHAT_IF)) {
                    p[i] = solution[i]
                    p[80 - i] = solution[80 - i]
                }
            }
        }
        assertTrue("both verdicts seen: $both finish, $neither stop", both > 50 && neither > 50)
    }

    @Test
    fun `the last resort is proved on every tier`() {
        for (d in Difficulty.entries) {
            val s = Sudoku.lastResort(d)
            assertEquals(1, oracleCount(s.cells.toIntArray()))
            assertTrue(SudokuTeacher.depth(s.cells) != null)
        }
    }

    /** Clues, attempts and time over a year of daily boards per tier: `build/reports/sudoku-generation.txt`. */
    @Test
    fun `a year of boards - clues, attempts and time`() {
        val report = StringBuilder("Sudoku generation, a year of daily boards per tier (JVM)\n")
        for (d in Difficulty.entries) {
            val clues = sortedMapOf<Int, Int>()
            val attempts = sortedMapOf<Int, Int>()
            val ms = mutableListOf<Double>()
            for (day in 0 until 365L) {
                val seed = daily(day, d)
                val t0 = System.nanoTime()
                val s = Sudoku.generateVerified(seed, d)!!
                ms += (System.nanoTime() - t0) / 1e6
                clues.merge(s.givens.count { it }, 1, Int::plus)
                // Attempt k draws from Rng(seed + k), so the first seed + k that proves alone is the attempt that shipped.
                val k = (0 until Sudoku.ATTEMPTS).first { Sudoku.generateVerified(seed + it, d, attempts = 1) != null }
                attempts.merge(k + 1, 1, Int::plus)
            }
            ms.sort()
            val mean = clues.entries.sumOf { it.key * it.value } / 365.0
            val meanAttempts = attempts.entries.sumOf { it.key * it.value } / 365.0
            report.appendLine("${d.name}: clues ${clues.firstKey()}-${clues.lastKey()} (mean ${"%.1f".format(mean)}) $clues")
            report.appendLine("  attempts mean ${"%.1f".format(meanAttempts)}, most ${attempts.lastKey()} of ${Sudoku.ATTEMPTS}: $attempts")
            report.appendLine("  ms median ${"%.1f".format(ms[182])} p95 ${"%.1f".format(ms[346])} max ${"%.1f".format(ms.last())}")
            assertTrue("${d.name} needed ${attempts.lastKey()} of ${Sudoku.ATTEMPTS} attempts", attempts.lastKey() <= Sudoku.ATTEMPTS / 2)
        }
        println(report)
        File("build/reports").mkdirs()
        File("build/reports/sudoku-generation.txt").writeText(report.toString())
    }
}
