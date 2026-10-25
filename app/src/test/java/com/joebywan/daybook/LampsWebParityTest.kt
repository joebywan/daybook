package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.ParityFingerprint
import com.joebywan.daybook.core.SeedHash
import com.joebywan.daybook.puzzles.Lamps
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Lamps on the web route, pinned the way [DominoWebParityTest] pins Dominoes: the web page prints the same lines under
 * `?dump`, and [WebParityDumpTest] writes the JVM's side of a whole year. A board is a pure function of its seed and
 * `Rng`, so these also pin the generator: a change to the walls, the lamps, the take-back or the gate goes red.
 */
class LampsWebParityTest {

    @Test
    fun `the web route reaches the same Lamps boards as the app`() {
        val lines = PARITY_DATES.flatMap { date ->
            Difficulty.entries.map { tier ->
                val seed = DailySeed.seedFor(date, Lamps.id, tier)
                val epochDay = SeedHash.epochDay(date.year, date.monthValue, date.dayOfMonth)
                assertEquals(seed, SeedHash.daily(epochDay, Lamps.id, tier))
                ParityFingerprint.line(Lamps, date.toString(), tier, seed)
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
            1090199857, 1340683786, 316467921, 1432216665, 991699326, 1274834571,
            430313879, -2009226407, 1686763360,
        )
    }
}
