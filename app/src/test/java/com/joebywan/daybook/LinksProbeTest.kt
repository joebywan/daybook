package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.Verdict
import com.joebywan.daybook.puzzles.LinksLogic
import org.junit.Test
import kotlin.time.TimeSource

class LinksProbeTest {
    @Test
    fun probe() {
        val tiers = (System.getenv("PROBE_TIERS") ?: "0,1,2").split(",").map { Difficulty.entries[it.toInt()] }
        for (d in tiers) {
            val spec = LinksLogic.specFor(d)
            var drawn = 0
            var unique = 0
            val depths = IntArray(6)
            var nulls = 0
            val t0 = TimeSource.Monotonic.markNow()
            val tries = (System.getenv("PROBE_N") ?: "200").toInt()
            for (a in 0 until tries) {
                val s = LinksLogic.draw(Rng(1000L + a), spec) ?: continue
                drawn++
                val b = LinksLogic.Board.of(s.size, s.ends)
                val dep = LinksLogic.depth(b)
                if (dep == null) nulls++ else depths[dep]++
                if (dep == null && a < 40) {
                    val v = LinksLogic.solutions(b)
                    if (v is Verdict.ExactlyOne) unique++
                }
            }
            println("PROBE $d drawn=$drawn/$tries depths=${depths.toList()} stuck=$nulls uniqueAmongStuck(first40)=$unique ms=${t0.elapsedNow().inWholeMilliseconds}")
        }
    }
}
