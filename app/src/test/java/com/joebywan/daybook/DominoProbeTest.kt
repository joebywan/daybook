package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.DominoLogic
import com.joebywan.daybook.puzzles.DominoTeacher
import com.joebywan.daybook.puzzles.RuleKind
import org.junit.Test
import java.time.LocalDate

class DominoProbeTest {
    @Test
    fun probe() {
        val days = (System.getenv("DOMINO_DAYS") ?: "40").toInt()
        for (d in Difficulty.entries) {
            val times = ArrayList<Double>()
            var attempts = 0
            var most = 0
            var nulls = 0
            var ruled = 0
            var squares = 0
            var rules = 0
            var sums = 0
            val kinds = IntArray(5)
            val levels = IntArray(7)
            for (day in 0 until days) {
                val seed = DailySeed.seedFor(LocalDate.of(2026, 1, 1).plusDays(day.toLong()), "dominoes", d)
                val t0 = System.nanoTime()
                val s = DominoLogic.generateVerified(seed, d)
                times += (System.nanoTime() - t0) / 1e6
                if (s == null) { nulls++; continue }
                val used = 1 + (0 until DominoLogic.ATTEMPTS).first { DominoLogic.attempt(seed, d, it) != null }
                attempts += used; most = maxOf(most, used)
                squares += s.open.count { it }
                ruled += s.regionOf.count { it >= 0 }
                rules += s.rules.size
                sums += s.rules.count { it.kind == RuleKind.SUM }
                s.rules.forEach { kinds[it.kind.ordinal]++ }
                levels[DominoTeacher.solveLevel(s.layout, 6)!!]++
            }
            times.sort()
            println("$d: nulls=$nulls attempts mean=${"%.1f".format(attempts.toDouble() / (days - nulls))} most=$most " +
                "ms median=${"%.1f".format(times[times.size / 2])} p95=${"%.1f".format(times[times.size * 95 / 100])} max=${"%.1f".format(times.last())} " +
                "ruled=${"%.0f".format(100.0 * ruled / squares)}% sums=${"%.0f".format(100.0 * sums / rules)}% kinds=${kinds.toList()} levels=${levels.toList()}")
        }
    }
}
