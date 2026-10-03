package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.ParityFingerprint
import com.joebywan.daybook.core.SeedHash
import com.joebywan.daybook.puzzles.Chess
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Mate on the web route: the web page prints the same lines under `?dump`, and [WebParityDumpTest] writes the JVM's
 * side of a whole year. A board is an index into the sorted position list, so these also pin the list: adding or
 * removing a position moves every later daily board and goes red on purpose.
 */
class ChessWebParityTest {

    @Test
    fun `the web route reaches the same Chess boards as the app`() {
        val lines = PARITY_DATES.flatMap { date ->
            Difficulty.entries.map { tier ->
                val seed = DailySeed.seedFor(date, Chess.id, tier)
                val epochDay = SeedHash.epochDay(date.year, date.monthValue, date.dayOfMonth)
                assertEquals(seed, SeedHash.daily(epochDay, Chess.id, tier))
                ParityFingerprint.line(Chess, date.toString(), tier, seed)
            }
        }
        lines.forEach(::println)
        assertEquals(FINGERPRINTS, lines.map { it.hashCode() })
    }

    companion object {
        val PARITY_DATES = listOf(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 9, 30),
            LocalDate.of(2027, 2, 28),
        )

        /** Hashes of the 9 lines, in the order above; [String.hashCode] keeps long lines out of the source. */
        val FINGERPRINTS = listOf(
            523066311, -1165899494, 404631131, -1458839375, -1394438850, -604155021, -299587287, 1996623160, -692798700,
        )
    }
}
