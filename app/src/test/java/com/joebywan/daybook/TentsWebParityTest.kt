package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.ParityFingerprint
import com.joebywan.daybook.core.SeedHash
import com.joebywan.daybook.puzzles.Tents
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Tents on the web route, pinned the way [InequalityWebParityTest] pins Inequality: the web page
 * prints the same lines under `?dump`, and [WebParityDumpTest] writes the JVM's side of a whole year.
 * A board is a pure function of its seed and `Rng`, so these also pin the generator: a change to the
 * draw, the order things are taken back in, or the ladder (which the generator asks to finish every board) moves boards and goes red.
 */
class TentsWebParityTest {

    @Test
    fun `the web route reaches the same Tents boards as the app`() {
        val lines = PARITY_DATES.flatMap { date ->
            Difficulty.entries.map { tier ->
                val seed = DailySeed.seedFor(date, Tents.id, tier)
                val epochDay = SeedHash.epochDay(date.year, date.monthValue, date.dayOfMonth)
                assertEquals(seed, SeedHash.daily(epochDay, Tents.id, tier))
                ParityFingerprint.line(Tents, date.toString(), tier, seed)
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
            940931634, -194075166, 594257890, 1026611820, 193407596, -1548371628,
            -341010275, 1640864579, 1396139213,
        )
    }
}
