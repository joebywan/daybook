package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.ParityFingerprint
import com.joebywan.daybook.core.PuzzleRegistry
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * The JVM's half of the year-long diff against the browser. With `DAYBOOK_PARITY_DUMP=<file>` set,
 * it writes every puzzle's board for every tier over 365 days from 2026-01-01, one
 * [ParityFingerprint] line each, in the order the web page prints its `RANGE` lines under
 * `?dump&range=365` (puzzle, then day, then tier). Strip the `RANGE ` prefix from the page's output
 * and the two files should be identical; it is also how Android boards are compared before and
 * after a change. The per-puzzle web parity tests pin a few of these boards outright.
 */
class WebParityDumpTest {

    @Test
    fun `the parity lines name every puzzle in the registry`() {
        val date = LocalDate.of(2026, 1, 1)
        val lines = PuzzleRegistry.all.map { type ->
            ParityFingerprint.line(type, date.toString(), Difficulty.STANDARD, DailySeed.seedFor(date, type.id, Difficulty.STANDARD))
        }
        assertEquals(PuzzleRegistry.all.map { it.id }, lines.map { it.substringBefore(' ') })
    }

    @Test
    fun `dump a year of every board when asked`() {
        val path = System.getenv("DAYBOOK_PARITY_DUMP")
        assumeTrue("set DAYBOOK_PARITY_DUMP to a file to write the year", !path.isNullOrEmpty())
        val start = LocalDate.of(2026, 1, 1)
        val out = StringBuilder()
        for (type in PuzzleRegistry.all) {
            for (day in 0 until 365L) {
                val date = start.plusDays(day)
                for (tier in Difficulty.entries) {
                    out.append(ParityFingerprint.line(type, date.toString(), tier, DailySeed.seedFor(date, type.id, tier))).append('\n')
                }
            }
        }
        File(path!!).writeText(out.toString())
    }
}
