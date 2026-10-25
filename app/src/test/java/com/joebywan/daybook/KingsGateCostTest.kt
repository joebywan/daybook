package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.SeedHash
import com.joebywan.daybook.puzzles.Kings
import org.junit.Assume.assumeTrue
import org.junit.Test

class KingsGateCostTest {
    @Test
    fun cost() {
        assumeTrue(System.getenv("DAYBOOK_GATE_COST") != null)
        val first = SeedHash.epochDay(2026, 1, 1)
        for (d in Difficulty.entries) {
            val atts = ArrayList<Int>(); val ms = ArrayList<Double>()
            for (i in 0 until 365) {
                val seed = SeedHash.daily(first + i, Kings.id, d)
                val t0 = System.nanoTime()
                var a = 0
                while (Kings.attempt(seed, d, a) == null) a++
                ms += (System.nanoTime() - t0) / 1e6
                atts += a + 1
            }
            atts.sort(); ms.sort()
            val boards = (0 until 100).map { Kings.attempt(SeedHash.daily(first + it, Kings.id, d), d, 0) ?: Kings.generate(SeedHash.daily(first + it, Kings.id, d), d) as com.joebywan.daybook.puzzles.KingsState }
            val t1 = System.nanoTime()
            for (b in boards) com.joebywan.daybook.puzzles.KingsTeacher.depth(b.size, b.region)
            println("KINGSCOST depth walk ms a board ${"%.2f".format((System.nanoTime() - t1) / 1e6 / boards.size)}")
            println("KINGSCOST $d attempts mean ${"%.1f".format(atts.average())} p50 ${atts[182]} p95 ${atts[346]} max ${atts.last()} | ms p50 ${"%.1f".format(ms[182])} p95 ${"%.1f".format(ms[346])} max ${"%.1f".format(ms.last())}")
        }
    }
}
