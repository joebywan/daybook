package com.joebywan.daybook

import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.LinksLogic
import org.junit.Test
import kotlin.time.TimeSource

class LinksProbeTest {
    @Test
    fun probe() {
        val specs = (System.getenv("PROBE_SPECS") ?: "6:5:7,8:7:9,10:9:12").split(",").map { it.split(":").map(String::toInt) }
        val tries = (System.getenv("PROBE_N") ?: "100").toInt()
        for ((side, lo, hi) in specs) {
            val spec = LinksLogic.Spec(side, lo, hi, LinksLogic.LOCAL..LinksLogic.WHAT_IF)
            var drawn = 0
            val depths = IntArray(6)
            var nulls = 0
            var cutMs = 0L
            var fullMs = 0L
            for (a in 0 until tries) {
                val s = LinksLogic.draw(Rng(1000L + a), spec) ?: continue
                drawn++
                val b = LinksLogic.Board.of(s.size, s.ends)
                val t0 = TimeSource.Monotonic.markNow()
                val upToCut = LinksLogic.depth(b, LinksLogic.CUT)
                cutMs += t0.elapsedNow().inWholeMilliseconds
                val t1 = TimeSource.Monotonic.markNow()
                val dep = upToCut ?: LinksLogic.depth(b)
                fullMs += t1.elapsedNow().inWholeMilliseconds
                if (dep == null) nulls++ else depths[dep]++
            }
            println("PROBE ${side}x$side $lo-$hi drawn=$drawn/$tries depths=${depths.toList()} stuck=$nulls cutWalkMs=$cutMs fullMs=$fullMs")
        }
    }
}
