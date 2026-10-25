package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.WhosWhoLogic
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Test

class WhosWhoProbeTest {
    @Test
    fun probe() {
        val days = (System.getenv("WW_DAYS") ?: "30").toInt()
        for (d in Difficulty.entries) {
            var draws = 0L
            var worst = 0
            var fails = 0
            val clues = ArrayList<Int>()
            val times = ArrayList<Long>()
            for (i in 0 until days) {
                val seed = DailySeed.seedFor(LocalDate(2026, 1, 1).plus(DatePeriod(days = i)), "whoswho", d)
                val t0 = System.nanoTime()
                var a = 0
                var s = WhosWhoLogic.attempt(seed, d, a)
                while (s == null && a < WhosWhoLogic.ATTEMPTS - 1) {
                    a++
                    s = WhosWhoLogic.attempt(seed, d, a)
                }
                times.add((System.nanoTime() - t0) / 1000)
                if (s == null) fails++ else clues.add(s.clues.size)
                draws += a + 1
                worst = maxOf(worst, a + 1)
                if (i == 0 && s != null) s.clues.indices.forEach { println("  " + WhosWhoLogic.clueText(s, it)) }
            }
            times.sort()
            println(
                "$d draws mean ${draws.toDouble() / days} worst $worst fails $fails clues mean ${clues.average()} " +
                    "min ${clues.minOrNull()} max ${clues.maxOrNull()} us p50 ${times[days / 2]} p95 ${times[days * 95 / 100]} max ${times.last()}",
            )
        }
    }
}
