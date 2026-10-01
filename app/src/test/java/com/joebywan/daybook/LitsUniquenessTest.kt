package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Lits
import com.joebywan.daybook.puzzles.LitsState
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Every shipped LITS board has exactly one answer under the rules the win check enforces.
 *
 * The generator once proved uniqueness among *connected* shadings while [Lits.isSolved] (PR #15)
 * accepts disconnected ones, so 138/300 Standard, 50/300 Hard and 85/300 Expert boards had several
 * answers a player could legitimately reach. The answers here come from [LitsOracle], which shares
 * no code with the generator and has no connectivity rule, exactly like the win check.
 */
class LitsUniquenessTest {

    private fun daily(difficulty: Difficulty, days: Int): List<Long> {
        val start = LocalDate.of(2026, 1, 1)
        return (0 until days).map { DailySeed.seedFor(start.plusDays(it.toLong()), Lits.id, difficulty) }
    }

    @Test
    fun `every daily board has one answer under the win check rules`() {
        val bad = mutableListOf<String>()
        for (difficulty in Difficulty.entries) {
            for (seed in daily(difficulty, 365)) {
                val s = Lits.generate(seed, difficulty) as LitsState
                val answers = LitsOracle.answers(s.width, s.height, s.region, cap = 2)
                if (answers.size != 1) bad += "${difficulty.name}/$seed (${answers.size})"
                else assertTrue(
                    "${difficulty.name}/$seed: the stored answer is not the one answer",
                    answers.single() == s.solution.indices.filter { s.solution[it] }.toSet(),
                )
                // The win check agrees that the one answer finishes the board.
                if (answers.isNotEmpty()) assertTrue(
                    "${difficulty.name}/$seed: win check refuses the oracle's answer",
                    Lits.isSolved(s.width, s.height, s.region, List(s.width * s.height) { it in answers[0] }),
                )
            }
        }
        assertTrue("${bad.size} boards without exactly one answer: ${bad.take(10)}", bad.isEmpty())
    }

    /** Opt-in measurement: `DAYBOOK_LITS_MEASURE=<days>` prints the multi-answer rate per tier. */
    @Test
    fun `measure multi-answer boards when asked`() {
        val days = System.getenv("DAYBOOK_LITS_MEASURE")?.toIntOrNull() ?: return
        val sb = StringBuilder()
        for (difficulty in Difficulty.entries) {
            var multi = 0
            var multiClassic = 0
            for (seed in daily(difficulty, days)) {
                val s = Lits.generate(seed, difficulty) as LitsState
                if (LitsOracle.answers(s.width, s.height, s.region, cap = 2).size != 1) multi++
                val connected = LitsOracle.answers(s.width, s.height, s.region, cap = 100_000)
                    .count { joined(it, s.width, s.height) }
                if (connected != 1) multiClassic++
            }
            sb.appendLine(
                "${difficulty.name}: $multi / $days boards with several answers under the win check; " +
                    "$multiClassic / $days without exactly one connected answer (classic rule)"
            )
        }
        File("build/reports").mkdirs()
        File("build/reports/lits-uniqueness.txt").writeText(sb.toString())
    }

    private fun joined(cells: Set<Int>, w: Int, h: Int): Boolean {
        val seen = mutableSetOf(cells.first())
        val todo = ArrayDeque(listOf(cells.first()))
        while (todo.isNotEmpty()) {
            val c = todo.removeFirst()
            for (n in listOf(c - w, c + w, c - 1, c + 1)) {
                if (n !in cells || n in seen) continue
                if (n / w != c / w && n % w != c % w) continue
                if ((n == c - 1 && c % w == 0) || (n == c + 1 && n % w == 0)) continue
                seen += n; todo += n
            }
        }
        return seen.size == cells.size
    }
}
