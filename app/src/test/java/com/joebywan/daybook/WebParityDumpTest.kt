package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.ParityFingerprint
import com.joebywan.daybook.core.PuzzleRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The JVM's half of the year-long diff against the browser. With `DAYBOOK_PARITY_DUMP=<file>` set,
 * it writes every puzzle's board for every tier over 365 days (or `DAYBOOK_PARITY_DAYS`, which
 * `tools/web-parity/check.py` and CI use for a shorter range) from 2026-01-01, one
 * [ParityFingerprint] line each, in the order the web page prints its `RANGE` lines under
 * `?dump&range=365` (puzzle, then day, then tier). Strip the `RANGE ` prefix from the page's output
 * and the two files should be identical; it is also how Android boards are compared before and
 * after a change. `BoardPinTest` pins nine of each puzzle's boards outright.
 */
class WebParityDumpTest {

    @Test
    fun `dump a year of every board when asked`() {
        val path = System.getenv("DAYBOOK_PARITY_DUMP")
        assumeTrue("set DAYBOOK_PARITY_DUMP to a file to write the year", !path.isNullOrEmpty())
        val days = System.getenv("DAYBOOK_PARITY_DAYS")?.toIntOrNull() ?: 365
        val out = StringBuilder()
        for (type in PuzzleRegistry.all) {
            for (day in 0 until days) {
                val date = TEST_START.plusDays(day.toLong())
                for (tier in Difficulty.entries) {
                    out.append(ParityFingerprint.line(type, date.toString(), tier, daySeed(day, type.id, tier))).append('\n')
                }
            }
        }
        // Then as many levels as days, five boards each, under the pseudo-puzzle "levels".
        for (level in 1..days) ParityFingerprint.levelLines(level).forEach { out.append(it).append('\n') }
        File(path!!).writeText(out.toString())
    }
}
