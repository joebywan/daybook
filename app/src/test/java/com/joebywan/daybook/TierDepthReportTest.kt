package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.SeedHash
import com.joebywan.daybook.puzzles.Atoms
import com.joebywan.daybook.puzzles.Inequality
import com.joebywan.daybook.puzzles.Kings
import com.joebywan.daybook.puzzles.Lits
import com.joebywan.daybook.puzzles.Mambo
import com.joebywan.daybook.puzzles.Nonogram
import com.joebywan.daybook.puzzles.NonogramLogic
import com.joebywan.daybook.puzzles.NonogramState
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.puzzles.Regions
import com.joebywan.daybook.puzzles.Shikaku
import com.joebywan.daybook.puzzles.Sudoku
import com.joebywan.daybook.puzzles.Tents
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * How deep each tier really is: a year of daily boards per tier (seeds exactly as the daily makes them), each walked by
 * its own teacher from the blank board to the end, recording the deepest technique the walk needed (in the ladder's
 * order) and how often it fell back. Measurement only; nothing here gates a board. Writes `build/reports/tier-depth.txt`.
 *
 * Env-gated (a few minutes): `DAYBOOK_DEPTH_REPORT=1`, or `=<days>` for a smaller sample. Results: `docs/TEACHING.md`,
 * "Tier depth (measured 2026-10)". Snap's whole-board deducer is [SnapDeducerReportTest], under the same switch.
 */
class TierDepthReportTest {

    /** Each puzzle's ladder, easiest first, as its teacher tries them (the teacher's own constants). */
    private val ladders: List<Pair<PuzzleType, List<String>>> = listOf(
        Sudoku to listOf(
            "full-house", "hidden-single-box", "hidden-single-line", "naked-single",
            "locked-candidates", "naked-pair", "hidden-pair", "what-if",
        ),
        Kings to listOf("last-square", "locked-to-a-line", "would-empty", "confined", "what-if"),
        Mambo to listOf("pair", "sandwich", "link", "quota-met", "quota-almost-met", "what-if"),
        Shikaku to listOf("only-fits", "only-reaches", "common-cells", "what-if"),
        Atoms to listOf("one-neighbour", "all-forced", "at-least-one", "crossing", "isolation", "only-way-out", "what-if"),
        Inequality to listOf("last-square", "only-digit", "only-place", "sign-bound", "sign-place", "naked-pair", "hidden-pair"),
        Tents to listOf("no-tree", "line-done", "line-full", "touch", "one-spot", "shared", "served"),
        Nonogram to listOf("full-line", "line-finished", "overlap", "line-logic"),
        Lits to listOf("whole-region", "overlap", "avoid-2x2", "letter-clash", "neighbour", "what-if"),
        Regions to listOf("last-square", "only-digit", "only-place", "locked-candidate"),
    )

    private class Walk(
        val deepest: String, val steps: Int, val fallbacks: Int, val mistakes: Int, val finished: Boolean,
        val uses: Map<String, Int>, val extra: Int = 0,
    )

    private fun walk(p: PuzzleType, ladder: List<String>, start: PuzzleState): Walk {
        var s = start
        var deepest = -1
        var steps = 0
        var fallbacks = 0
        var mistakes = 0
        val uses = HashMap<String, Int>()
        while (!s.solved && steps < 3000) {
            val d = p.teach(s) ?: break
            steps++
            uses.merge(d.technique, 1, Int::plus)
            when {
                d.mistake -> mistakes++
                d.fallback -> fallbacks++
                else -> {
                    val at = ladder.indexOf(d.technique)
                    assertTrue("${p.id}: technique ${d.technique} is not in the ladder", at >= 0)
                    deepest = maxOf(deepest, at)
                }
            }
            s = d.apply(s)
        }
        return Walk(if (deepest < 0) "none" else ladder[deepest], steps, fallbacks, mistakes, s.solved, uses)
    }

    /**
     * Nonogram's own depth: line passes from a blank board (each pass settles every row with what the board then
     * holds, then every column), until nothing changes. One pass is "every line's overlap alone"; the count is how
     * many times the lines must hand each other squares.
     */
    private fun nonogramPasses(s: NonogramState): Int {
        val rows = NonogramLogic.rowClues(s)
        val cols = NonogramLogic.colClues(s)
        val grid = IntArray(s.width * s.height)
        var passes = 0
        while (true) {
            var changed = false
            for (line in 0 until s.height + s.width) {
                val clue = if (line < s.height) rows[line] else cols[line - s.height]
                val at = NonogramLogic.lineCells(s.width, s.height, line)
                val found = NonogramLogic.forced(clue, IntArray(at.size) { grid[at[it]] })!!
                for (x in at.indices) if (found[x] != NonogramLogic.UNKNOWN) { grid[at[x]] = found[x]; changed = true }
            }
            if (!changed) break
            passes++
        }
        check(grid.none { it == NonogramLogic.UNKNOWN })
        return passes
    }

    @Test
    fun `report how deep each tier is`() {
        val env = System.getenv("DAYBOOK_DEPTH_REPORT")
        assumeTrue("set DAYBOOK_DEPTH_REPORT to measure tier depth", env != null)
        val days = env!!.toIntOrNull()?.takeIf { it > 1 } ?: 365
        val first = SeedHash.epochDay(2026, 1, 1)
        val out = StringBuilder("Tier depth: $days daily boards per tier from 2026-01-01, walked by each puzzle's own teacher.\n")
        out.append("Columns: deepest technique the walk needed (ladder order) as boards, then fallback boards %, fallback steps %, median / max steps;\nunder it, each technique's mean steps a board.\n\n")
        for ((p, ladder) in ladders) {
            out.append("== ${p.displayName} (${p.id}); ladder: ${ladder.joinToString(" < ")}\n")
            for (d in Difficulty.entries) {
                val t0 = System.nanoTime()
                val walks = (0 until days).toList().parallelStream().map { i ->
                    val s = p.generate(SeedHash.daily(first + i, p.id, d), d)
                    val w = walk(p, ladder, s)
                    if (s is NonogramState) Walk(w.deepest, w.steps, w.fallbacks, w.mistakes, w.finished, w.uses, nonogramPasses(s)) else w
                }.toList()
                val ms = (System.nanoTime() - t0) / 1_000_000
                val hist = walks.groupingBy { it.deepest }.eachCount()
                val order = listOf("none") + ladder
                val steps = walks.map { it.steps }.sorted()
                val allSteps = steps.sum()
                out.append("  ${d.name.padEnd(8)} ")
                out.append(order.filter { it in hist }.joinToString("  ") { "$it ${hist[it]}" })
                out.append(" | fallback boards %.1f%%, steps %.2f%%".format(100.0 * walks.count { it.fallbacks > 0 } / days, 100.0 * walks.sumOf { it.fallbacks } / allSteps))
                out.append(" | steps median ${steps[steps.size / 2]}, max ${steps.last()}")
                val unfinished = walks.count { !it.finished }
                val mistakes = walks.sumOf { it.mistakes }
                if (unfinished > 0 || mistakes > 0) out.append(" | UNFINISHED $unfinished, mistakes $mistakes")
                if (p === Nonogram) {
                    val passes = walks.groupingBy { it.extra }.eachCount().toSortedMap()
                    out.append(" | line passes: " + passes.entries.joinToString(" ") { "${it.key}:${it.value}" })
                }
                out.append(" | ${ms} ms\n")
                out.append("           steps a board: " + ladder.joinToString("  ") { t -> "$t %.1f".format(walks.sumOf { it.uses[t] ?: 0 }.toDouble() / days) } + "\n")
            }
            out.append('\n')
        }
        File("build/reports").mkdirs()
        File("build/reports/tier-depth.txt").writeText(out.toString())
        println(out)
    }
}
