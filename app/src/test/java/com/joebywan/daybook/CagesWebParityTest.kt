package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.ParityFingerprint
import com.joebywan.daybook.core.SeedHash
import com.joebywan.daybook.puzzles.Cages
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Cage Sudoku on the web route, pinned the way [TentsWebParityTest] pins Tents: the web page
 * prints the same lines under `?dump`, and [WebParityDumpTest] writes the JVM's side of a whole year.
 * A board is a pure function of its seed and `Rng`, so these also pin the generator: a change to the
 * draw, the order things are taken back in, or the ladder (which the generator asks to finish every board) moves boards and goes red.
 */
class CagesWebParityTest {

    @Test
    fun `the web route reaches the same Cages boards as the app`() {
        val lines = PARITY_DATES.flatMap { date ->
            Difficulty.entries.map { tier ->
                val seed = DailySeed.seedFor(date, Cages.id, tier)
                val epochDay = SeedHash.epochDay(date.year, date.monthValue, date.dayOfMonth)
                assertEquals(seed, SeedHash.daily(epochDay, Cages.id, tier))
                ParityFingerprint.line(Cages, date.toString(), tier, seed)
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
            -1862718606, 143994853, -1779174554, 2059855175, -1291292223, -945129258,
            2112846315, -875673327, -250928833,
        )
    }
}
