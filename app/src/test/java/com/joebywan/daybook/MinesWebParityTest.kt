package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.ParityFingerprint
import com.joebywan.daybook.core.SeedHash
import com.joebywan.daybook.puzzles.Mines
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Mines on the web route, pinned the way [TargetWebParityTest] pins Target: the web page
 * prints the same lines under `?dump`, and [WebParityDumpTest] writes the JVM's side of a whole year.
 * A board is a pure function of its seed and `Rng`, so these also pin the generator: a change to the
 * draw, the tier's specification, the ladder or the gate moves boards and goes red.
 */
class MinesWebParityTest {

    @Test
    fun `the web route reaches the same Mines boards as the app`() {
        val lines = PARITY_DATES.flatMap { date ->
            Difficulty.entries.map { tier ->
                val seed = DailySeed.seedFor(date, Mines.id, tier)
                val epochDay = SeedHash.epochDay(date.year, date.monthValue, date.dayOfMonth)
                assertEquals(seed, SeedHash.daily(epochDay, Mines.id, tier))
                ParityFingerprint.line(Mines, date.toString(), tier, seed)
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
            411867627, 1519893279, 746116478, 219182737, -540251624, 1891482109,
            761516205, 1794723430, 1573432635,
        )
    }
}
