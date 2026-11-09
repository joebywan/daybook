package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.puzzles.Links
import com.joebywan.daybook.puzzles.LinksLogic
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Test
import kotlin.time.TimeSource

class LinksProbeTest {
    @Test
    fun probe() {
        val start = LocalDate(2026, 11, 1)
        val d = Difficulty.EXPERT
        val spec = LinksLogic.specFor(d)
        for (day in 0 until 365) {
            val seed = DailySeed.seedFor(start.plus(DatePeriod(days = day)), Links.id, d)
            for (a in 0 until LinksLogic.ATTEMPTS) {
                val s = LinksLogic.draw(Rng(seed + a), spec) ?: continue
                val b = LinksLogic.Board.of(s.size, s.ends)
                val t0 = TimeSource.Monotonic.markNow()
                val dep = LinksLogic.depth(b, spec.needs.last)
                val w = t0.elapsedNow().inWholeMilliseconds
                if (w > 1000) println("PROBE day $day draw $a walk $w ms ends ${s.ends}")
                if (dep == null || dep !in spec.needs) continue
                val t1 = TimeSource.Monotonic.markNow()
                LinksLogic.solutions(b)
                val c = t1.elapsedNow().inWholeMilliseconds
                if (c > 1000) println("PROBE day $day draw $a count $c ms ends ${s.ends}")
                break
            }
        }
    }
}
