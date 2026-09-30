package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Sudoku
import com.joebywan.daybook.puzzles.SudokuState
import com.joebywan.daybook.puzzles.SudokuTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * The Sudoku hint solver, checked against an enumerator written out again here on a different
 * principle from the generator's (most-constrained cell first, through the shared peer table): plain
 * row-major backtracking that tests each digit by scanning its row, column and box directly.
 *
 * As for Kings, soundness on a unique board cannot tell reasoning from peeking, so the solver is also
 * walked over boards with several answers, where a digit is only sound if every answer still
 * standing has it there.
 */
class SudokuTeachingTest {

    private fun seeds(count: Int, difficulty: Difficulty, salt: String) = (0 until count).map {
        DailySeed.seedFor(LocalDate.of(2026, 1, 1).plusDays(it.toLong()), salt, difficulty)
    }

    // ---- independent rules --------------------------------------------------------------------

    private fun fits(grid: IntArray, cell: Int, d: Int): Boolean {
        val r = cell / 9
        val c = cell % 9
        for (k in 0 until 9) {
            if (grid[r * 9 + k] == d || grid[k * 9 + c] == d) return false
        }
        val br = r - r % 3
        val bc = c - c % 3
        for (dr in 0 until 3) for (dc in 0 until 3) if (grid[(br + dr) * 9 + bc + dc] == d) return false
        return true
    }

    /** Every completion of [cells], row-major, up to [cap]. Null when there are more than [cap]. */
    private fun allSolutions(cells: List<Int>, cap: Int): List<List<Int>>? {
        val grid = cells.toIntArray()
        val out = mutableListOf<List<Int>>()
        var over = false
        fun go(i: Int) {
            if (over) return
            if (i == 81) {
                if (out.size == cap) over = true else out += grid.toList()
                return
            }
            if (grid[i] != 0) return go(i + 1)
            for (d in 1..9) {
                if (!fits(grid, i, d)) continue
                grid[i] = d
                go(i + 1)
                grid[i] = 0
                if (over) return
            }
        }
        go(0)
        return if (over) null else out
    }

    /** A completed grid obeys the rules: every row, column and box holds 1..9. */
    private fun valid(g: List<Int>): Boolean {
        val want = (1..9).toSet()
        for (k in 0 until 9) {
            if ((0 until 9).map { g[k * 9 + it] }.toSet() != want) return false
            if ((0 until 9).map { g[it * 9 + k] }.toSet() != want) return false
            val br = k / 3 * 3
            val bc = k % 3 * 3
            if ((0 until 9).map { g[(br + it / 3) * 9 + bc + it % 3] }.toSet() != want) return false
        }
        return true
    }

    // ---- soundness ------------------------------------------------------------------------------

    @Test
    fun `every step on a real board agrees with the answer and places a digit`() {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds(40, difficulty, "sudoku-teach")) {
                var s = Sudoku.generate(seed, difficulty) as SudokuState
                assertEquals(listOf(s.solution), allSolutions(s.cells, 2))
                var guard = 0
                while (!s.solved) {
                    assertTrue("$difficulty/$seed: walk did not finish", guard++ < 81)
                    val d = Sudoku.teach(s)
                    assertNotNull(d)
                    d!!
                    assertFalse("$difficulty/$seed: a hint-only walk made a mistake", d.mistake)
                    assertEquals(1, d.targets.size)
                    val cell = d.targets.single()
                    assertEquals("$difficulty/$seed: ${d.technique} on an empty cell", 0, s.cells[cell])
                    val next = d.apply(s) as SudokuState
                    assertEquals("$difficulty/$seed: ${d.technique}: ${d.explanation}", s.solution[cell], next.cells[cell])
                    assertTrue(d.isReached(next))
                    assertFalse(d.isReached(s))
                    assertTrue("${d.explanation.length}: ${d.explanation}", d.explanation.length <= SudokuTeacher.MAX_EXPLANATION)
                    assertTrue("nudge too long: ${d.nudge}", d.nudge.length <= 60)
                    s = next
                }
                assertTrue(valid(s.cells))
            }
        }
    }

    @Test
    fun `on boards with several answers, a step holds for every answer still possible`() {
        val rng = java.util.Random(7)
        var boards = 0
        var steps = 0
        var tries = 0
        while (boards < 150) {
            assertTrue("could not make enough multi-answer boards", tries++ < 2000)
            val base = Sudoku.generate(rng.nextLong(), Difficulty.entries[rng.nextInt(3)]) as SudokuState
            // Knock out a few more givens, unchecked, so several answers stand.
            val cells = base.cells.toMutableList()
            val filled = cells.indices.filter { cells[it] != 0 }.shuffled(rng)
            filled.take(3 + rng.nextInt(6)).forEach { cells[it] = 0 }
            var answers = allSolutions(cells, 300) ?: continue
            if (answers.size < 2) continue
            boards++
            while (true) {
                val step = SudokuTeacher.deduce(cells) ?: break
                assertTrue(
                    "${step.technique}: ${step.explanation}",
                    answers.all { it[step.cell] == step.digit },
                )
                cells[step.cell] = step.digit
                answers = answers.filter { it[step.cell] == step.digit }
                steps++
            }
        }
        assertTrue("too few steps exercised: $steps", steps > 1000)
    }

    /**
     * `app/build/reports/sudoku-teaching-coverage.txt`: how often each technique is what a player
     * needs next, and how often the fallback is reached, per tier.
     */
    @Test
    fun `coverage - which techniques boards need, per difficulty`() {
        val perTier = 300
        val report = StringBuilder()
        report.appendLine("Sudoku teaching coverage: $perTier boards per tier, walked from the start by hints alone")
        for (difficulty in Difficulty.entries) {
            val stepCounts = SudokuTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            val boardCounts = SudokuTeacher.TECHNIQUES.associateWith { 0 }.toMutableMap()
            var totalSteps = 0
            var longest = ""
            for (seed in seeds(perTier, difficulty, "sudoku-coverage")) {
                var s = Sudoku.generate(seed, difficulty) as SudokuState
                val used = mutableSetOf<String>()
                while (!s.solved) {
                    val d = Sudoku.teach(s)!!
                    if (d.explanation.length > longest.length) longest = d.explanation
                    stepCounts[d.technique] = stepCounts.getValue(d.technique) + 1
                    used += d.technique
                    totalSteps++
                    s = d.apply(s) as SudokuState
                }
                used.forEach { boardCounts[it] = boardCounts.getValue(it) + 1 }
            }
            report.appendLine()
            report.appendLine("${difficulty.name}: $totalSteps steps, ${"%.1f".format(totalSteps / perTier.toDouble())} per board")
            for (t in SudokuTeacher.TECHNIQUES) {
                report.appendLine(
                    "  %-20s %5d steps (%5.1f%%)   needed on %3d/%d boards (%5.1f%%)".format(
                        t, stepCounts.getValue(t), 100.0 * stepCounts.getValue(t) / totalSteps,
                        boardCounts.getValue(t), perTier, 100.0 * boardCounts.getValue(t) / perTier,
                    )
                )
            }
            report.appendLine("  longest explanation, ${longest.length} characters: $longest")
            assertTrue("$difficulty: an explanation too long for the panel: $longest", longest.length <= SudokuTeacher.MAX_EXPLANATION)
            val fallbackBoards = boardCounts.getValue(SudokuTeacher.FALLBACK)
            assertTrue(
                "$difficulty: the fallback is reached on $fallbackBoards/$perTier boards",
                fallbackBoards * 10 < perTier,
            )
        }
        println(report)
        File("build/reports").mkdirs()
        File("build/reports/sudoku-teaching-coverage.txt").writeText(report.toString())
    }
}
