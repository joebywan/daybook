package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.PearlsLogic
import org.junit.Test

class PearlsProbeTest {
    @Test
    fun probe() {
        val only = System.getenv("PEARLS_TIER")
        for (d in Difficulty.entries) {
            if (only != null && d.name != only) continue
            val times = ArrayList<Long>()
            val draws = ArrayList<Int>()
            var fails = 0
            val pearlCounts = ArrayList<Int>()
            for (seed in 1L..(System.getenv("PEARLS_N")?.toLong() ?: 20L)) {
                val t0 = System.nanoTime()
                var got = -1
                for (a in 0 until PearlsLogic.ATTEMPTS) {
                    val s = PearlsLogic.attempt(seed * 7919, d, a)
                    if (s != null) { got = a; pearlCounts += s.pearls.count { it != '.' }; break }
                }
                times += (System.nanoTime() - t0) / 1_000_000
                if (got < 0) fails++ else draws += got + 1
            }
            times.sort()
            println("PROBE $d fails=$fails draws mean=${draws.average()} max=${draws.maxOrNull()} ms median=${times[times.size / 2]} max=${times.last()} pearls=${pearlCounts.average()}")
        }
    }
}
