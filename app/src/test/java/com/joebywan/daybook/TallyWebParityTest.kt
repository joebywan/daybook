package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.ParityFingerprint
import com.joebywan.daybook.core.SeedHash
import com.joebywan.daybook.puzzles.Tally
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Tally on the web route, pinned the way [NonogramWebParityTest] pins Nonogram: the web page prints
 * the same lines under `?dump`, and [WebParityDumpTest] writes the JVM's side of a whole year. A board
 * is a pure function of its seed and `Rng`, so these also pin the generator: a change to the draw, the
 * keep rate, the ladder or the count moves boards and goes red.
 */
class TallyWebParityTest {

    @Test
    fun `the web route reaches the same Tally boards as the app`() {
        val lines = PARITY_DATES.flatMap { date ->
            Difficulty.entries.map { tier ->
                val seed = DailySeed.seedFor(date, Tally.id, tier)
                val epochDay = SeedHash.epochDay(date.year, date.monthValue, date.dayOfMonth)
                assertEquals(seed, SeedHash.daily(epochDay, Tally.id, tier))
                ParityFingerprint.line(Tally, date.toString(), tier, seed)
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
            1544727131, -442637926, 1166708192, -1754905174, 1651409005, 599707071,
            319744754, -646461482, -392851936,
        )
    }
}
