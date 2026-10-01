package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.ParityFingerprint
import com.joebywan.daybook.puzzles.Lits
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * LITS alone, over as many days as asked, with each board's generation time: the before-and-after
 * proof for a change to the LITS generator that must not move a board. With
 * `DAYBOOK_LITS_DUMP=<file>` (and optionally `DAYBOOK_LITS_DAYS`, default 730) it writes one
 * [ParityFingerprint] line per board from 2026-01-01, day then tier, to `<file>`, and
 * `<tier> <ms>` per board to `<file>.times`. Diff two runs' fingerprint files; the times are
 * single-threaded after a short warm-up.
 */
class LitsYearDumpTest {

    @Test
    fun `dump LITS boards and timings when asked`() {
        val path = System.getenv("DAYBOOK_LITS_DUMP")
        assumeTrue("set DAYBOOK_LITS_DUMP to a file to write the boards", !path.isNullOrEmpty())
        val days = System.getenv("DAYBOOK_LITS_DAYS")?.toLongOrNull() ?: 730L
        val start = LocalDate.of(2026, 1, 1)
        repeat(30) { Lits.generate(it.toLong(), Difficulty.entries[it % 3]) }
        val out = StringBuilder()
        val times = StringBuilder()
        for (day in 0 until days) {
            val date = start.plusDays(day)
            for (tier in Difficulty.entries) {
                val seed = DailySeed.seedFor(date, Lits.id, tier)
                val t0 = System.nanoTime()
                val line = ParityFingerprint.line(Lits, date.toString(), tier, seed)
                val ms = (System.nanoTime() - t0) / 1e6
                out.append(line).append('\n')
                times.append("${tier.name} $date $ms\n")
            }
        }
        File(path!!).writeText(out.toString())
        File("$path.times").writeText(times.toString())
    }
}
