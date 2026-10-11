package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.CageLogic
import org.junit.Test

class CageProbeTest {
    @Test
    fun probe() {
        for (tier in Difficulty.entries) {
            var ok = 0; var givens = 0; var cages = 0; var big = 0
            val t0 = System.nanoTime()
            val n = 40
            for (s in 1..n) {
                val st = CageLogic.generateVerified(1000L * s + 7, tier)
                if (st != null) { ok++; givens += st.grid.givens.count { it }; cages += st.sums.size
                    big += CageLogic.cageCells(st.cageOf).count { it.size >= 5 } }
            }
            val ms = (System.nanoTime() - t0) / 1e6 / n
            println("PROBE $tier ok=$ok/$n givens=${givens.toDouble()/maxOf(ok,1)} cages=${cages.toDouble()/maxOf(ok,1)} big=${big.toDouble()/maxOf(ok,1)} ms/board=$ms")
        }
    }
}
