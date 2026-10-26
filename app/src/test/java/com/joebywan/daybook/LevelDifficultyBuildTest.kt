package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Levels
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.core.SeedHash
import com.joebywan.daybook.core.techniqueNamesOf
import com.joebywan.daybook.puzzles.LexiconState
import com.joebywan.daybook.puzzles.PointersState
import com.joebywan.daybook.puzzles.SnapState
import com.joebywan.daybook.puzzles.TowerState
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Measures how hard each puzzle's tiers are, against each other, with no player data, and writes the result: the
 * ranking deal 3 spreads over levels 1-100 (`core/LevelSpread.kt`) and the table in `docs/LEVELS_SPEC.md` (between the
 * `level-difficulty` markers). Both are generated; never edit them by hand. Rewriting `LevelSpread.kt` moves deal 3's
 * plans (`LevelsTest` goes red on purpose), so a new measurement ships as a new deal, never over this one.
 *
 * For every puzzle and tier, [BOARDS] boards (the level seeds of levels 1..[BOARDS] at that tier) are walked by the
 * puzzle's own teacher from the blank board to the end, taking every hint. Three measures a board:
 * - **steps**: hints the walk took (deductions or moves to make);
 * - **depth**: the deepest technique it needed, as its place on the puzzle's ladder (`techniqueNamesOf`, simplest
 *   first) from 0 to 1, a fallback step counting as the top;
 * - **open**: the share of steps that were the fallback (nothing short explained the move: choosing or recalling).
 * A walk that ends unsolved (Lexicon's teacher can run out of guesses) counts its depth as the top.
 * Snap and Pointers have no teacher. Snap: steps are the squares of its line; depth is 1 when its whole-board rules
 * ([SnapDeducerReportTest.SnapDeducer]) do not finish it from blank (a what-if is needed), else 0. Pointers: steps are
 * its arrows; depth and open are 0 (nothing is hidden; its challenge is care, not reasoning).
 *
 * The measures are not comparable across puzzles as numbers (a Sudoku step is not a chess move), so each is turned
 * into a percentile rank among all puzzle-tiers, and the mean of the three is the measured score, 0-1000. The score
 * deal 3 uses is the mean of that and the tier itself (0 / 500 / 1000): each puzzle's tiers are already measured and
 * gated by its own generator (size and the ladder rung a board must need), and the walk alone misreads a puzzle whose
 * step is a whole search (a chess key move is one step), so it moves a puzzle within a band rather than across them.
 *
 * Env-gated (a minute or two): `DAYBOOK_LEVEL_DIFFICULTY=1`.
 */
class LevelDifficultyBuildTest {

    private class Row(val id: String, val tier: Difficulty, val steps: Double, val depth: Double, val open: Double, val unfinished: Int, val lost: Int)

    private fun median(xs: List<Int>): Double = xs.sorted().let { s -> if (s.size % 2 == 1) s[s.size / 2].toDouble() else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0 }

    private fun measure(id: String, tier: Difficulty): Row {
        val p = PuzzleRegistry.byId(id)!!
        val ladder = techniqueNamesOf(id).keys.toList()
        class Walk(val steps: Int, val depth: Double, val fallbacks: Int, val finished: Boolean, val won: Boolean = true)
        val walks = (1..BOARDS).toList().parallelStream().map { level ->
            var s = p.generate(SeedHash.level(level, id, tier), tier)
            when (s) {
                is SnapState -> {
                    val dd = SnapDeducerReportTest.SnapDeducer(s.width, s.height, s.waypoints.toIntArray())
                    val st = ByteArray(dd.m)
                    check(dd.propagate(st, 0))
                    val inEdges = st.count { it == SnapDeducerReportTest.SnapDeducer.IN }
                    Walk(s.width * s.height, if (inEdges == s.width * s.height - 1) 0.0 else 1.0, 0, true)
                }
                is PointersState -> Walk(s.arrows.size, 0.0, 0, true)
                else -> {
                    var steps = 0
                    var fallbacks = 0
                    var deepest = 0.0
                    while (!s.solved && steps < 3000) {
                        val d = p.teach(s) ?: break
                        steps++
                        when {
                            d.mistake -> {}
                            d.fallback -> { fallbacks++; deepest = 1.0 }
                            else -> {
                                val at = ladder.indexOf(d.technique)
                                assertTrue("$id: ${d.technique} is not in its ladder", at >= 0)
                                if (ladder.size > 1) deepest = maxOf(deepest, at.toDouble() / (ladder.size - 1))
                            }
                        }
                        // A typed row waits for Enter, which the player presses, not the hint (Lexicon, Tower).
                        s = d.apply(s).let { next ->
                            if (next != s) next
                            else when (s) { is LexiconState -> s.submit(); is TowerState -> s.submit(); else -> break }
                        }
                    }
                    Walk(steps, if (s.solved) deepest else 1.0, fallbacks, steps < 3000, s.solved)
                }
            }
        }.toList()
        val steps = walks.sumOf { it.steps }
        return Row(
            id, tier, median(walks.map { it.steps }), walks.map { it.depth }.average(),
            if (steps == 0) 0.0 else walks.sumOf { it.fallbacks }.toDouble() / steps, walks.count { !it.finished }, walks.count { !it.won },
        )
    }

    /** Percentile rank of each value among [xs], ties sharing the mean rank, 0 for the lowest and 1 for the highest. */
    private fun ranks(xs: List<Double>): List<Double> = xs.map { x ->
        val below = xs.count { it < x }
        val equal = xs.count { it == x }
        (below + (equal - 1) / 2.0) / (xs.size - 1)
    }

    @Test
    fun `measure each puzzle's tiers and write the level spread`() {
        assumeTrue("set DAYBOOK_LEVEL_DIFFICULTY to measure", System.getenv("DAYBOOK_LEVEL_DIFFICULTY") != null)
        val ids = Levels.POOL
        val rows = ids.flatMap { id -> Difficulty.entries.map { t -> measure(id, t) } }
        val unfinished = rows.filter { it.unfinished > 0 }.map { "${it.id}/${it.tier}: ${it.unfinished}" }
        assertTrue("walks that did not finish: $unfinished", unfinished.isEmpty())
        val r = listOf(ranks(rows.map { it.steps }), ranks(rows.map { it.depth }), ranks(rows.map { it.open }))
        val measured = rows.indices.map { i -> Math.round(1000 * (r[0][i] + r[1][i] + r[2][i]) / 3).toInt() }
        // Half the measure, half the tier the puzzle's own generator gated it to (0 / 500 / 1000).
        val score = rows.indices.map { i -> (measured[i] + 500 * rows[i].tier.ordinal + 1) / 2 }
        val byId = rows.indices.groupBy { rows[it].id }

        val kt = StringBuilder()
        kt.append("package com.joebywan.daybook.core\n\n")
        kt.append("// Generated by LevelDifficultyBuildTest (DAYBOOK_LEVEL_DIFFICULTY=1). Do not edit: deal 3 reads it, so a change moves\n")
        kt.append("// every deal-3 plan. A new measurement is a new deal. How it is measured: docs/LEVELS_SPEC.md, \"Level difficulty\".\n\n")
        kt.append("/** Deal 3's difficulty of each puzzle's Standard, Hard and Expert, 0 (gentlest) to 1000, for the first [RANKED] pool ids. */\n")
        kt.append("internal object LevelSpread {\n")
        kt.append("    const val RANKED = ${ids.size}\n\n")
        kt.append("    val SCORE: Map<String, List<Int>> = mapOf(\n")
        for (id in ids) kt.append("        \"$id\" to listOf(${byId[id]!!.joinToString(", ") { score[it].toString() }}),\n")
        kt.append("    )\n}\n")
        File("src/main/java/com/joebywan/daybook/core/LevelSpread.kt").writeText(kt.toString())

        val md = StringBuilder()
        md.append("Generated by `LevelDifficultyBuildTest` ($BOARDS boards a tier, the level seeds of levels 1-$BOARDS); do not edit by hand.\n")
        md.append("Steps: median hints a board. Depth: mean deepest technique, 0-1 on the puzzle's ladder. Open: fallback share of steps.\n")
        md.append("Score: measured, the mean of the three percentile ranks among all ${rows.size} puzzle-tiers (0-1000), then **the score deal 3 uses**,\n")
        md.append("the mean of that and the tier (Standard 0, Hard 500, Expert 1000). Sorted by Standard's score.\n\n")
        md.append("| puzzle | Standard: steps / depth / open | measured, score | Hard | measured, score | Expert | measured, score |\n|---|---|---|---|---|---|---|\n")
        for (id in ids.sortedBy { score[byId[it]!![0]] }) {
            md.append("| ${PuzzleRegistry.byId(id)!!.displayName} (`$id`)")
            for (i in byId[id]!!) {
                val w = rows[i]
                val lost = if (w.lost > 0) " (lost ${w.lost})" else ""
                md.append(" | %s / %.2f / %.0f%%%s | %d, **%d**".format(w.steps.let { if (it % 1 == 0.0) it.toInt().toString() else "%.1f".format(it) }, w.depth, 100 * w.open, lost, measured[i], score[i]))
            }
            md.append(" |\n")
        }
        val spec = File("../docs/LEVELS_SPEC.md")
        val text = spec.readText()
        val a = text.indexOf(START)
        val b = text.indexOf(END)
        check(a >= 0 && b > a) { "markers missing in LEVELS_SPEC.md" }
        spec.writeText(text.substring(0, a + START.length) + "\n" + md + text.substring(b))
        println(md)
    }

    private companion object {
        const val BOARDS = 60
        const val START = "<!-- level-difficulty:start -->"
        const val END = "<!-- level-difficulty:end -->"
    }
}
