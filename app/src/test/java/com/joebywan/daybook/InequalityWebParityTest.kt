package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.ParityFingerprint
import com.joebywan.daybook.core.SeedHash
import com.joebywan.daybook.puzzles.Inequality
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Inequality on the web route, pinned the way [NonogramWebParityTest] pins Nonogram: the web page
 * prints the same lines under `?dump`, and [WebParityDumpTest] writes the JVM's side of a whole year.
 * A board is a pure function of its seed and `Rng`, so these also pin the generator: a change to the
 * draw, the order things are taken back in, or the teacher's ladder (which the generator asks to
 * finish every board) moves boards and goes red.
 */
class InequalityWebParityTest {

    @Test
    fun `the web route reaches the same Inequality boards as the app`() {
        val lines = PARITY_DATES.flatMap { date ->
            Difficulty.entries.map { tier ->
                val seed = DailySeed.seedFor(date, Inequality.id, tier)
                val epochDay = SeedHash.epochDay(date.year, date.monthValue, date.dayOfMonth)
                assertEquals(seed, SeedHash.daily(epochDay, Inequality.id, tier))
                ParityFingerprint.line(Inequality, date.toString(), tier, seed)
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
            1025107459, 1371178428, 1896126469, -52193566, 855727309, 1162442002,
            -1744467274, 753301278, -1048334304,
        )
    }
}
