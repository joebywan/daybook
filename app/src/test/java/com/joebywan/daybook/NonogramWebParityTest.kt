package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.ParityFingerprint
import com.joebywan.daybook.core.SeedHash
import com.joebywan.daybook.puzzles.Nonogram
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Nonogram on the web route, pinned the way [WebParityMamboPipesSetsTowerTest] pins Tower: the web
 * page prints the same lines under `?dump`, and [WebParityDumpTest] writes the JVM's side of a whole
 * year. A board is a pure function of its seed and `Rng`, so these also pin the generator: a change
 * to the draw, the density, the acceptance rules or the solver's verdict moves boards and goes red.
 */
class NonogramWebParityTest {

    @Test
    fun `the web route reaches the same Nonogram boards as the app`() {
        val lines = PARITY_DATES.flatMap { date ->
            Difficulty.entries.map { tier ->
                val seed = DailySeed.seedFor(date, Nonogram.id, tier)
                val epochDay = SeedHash.epochDay(date.year, date.monthValue, date.dayOfMonth)
                assertEquals(seed, SeedHash.daily(epochDay, Nonogram.id, tier))
                ParityFingerprint.line(Nonogram, date.toString(), tier, seed)
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
            -709644446, -176013402, 80316099, 1096799170, 536503228, 1289023416,
            -1389945940, 1124852156, 1729280159,
        )
    }
}
