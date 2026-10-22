package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.ParityFingerprint
import com.joebywan.daybook.core.SeedHash
import com.joebywan.daybook.puzzles.Sand
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Sand on the web route, pinned the way [TentsWebParityTest] pins Tents: the web page
 * prints the same lines under `?dump`, and [WebParityDumpTest] writes the JVM's side of a whole year.
 * A board is a pure function of its seed and `Rng`, so these also pin the generator: a change to the
 * deal, the tier's specification, the search or the difficulty filter moves boards and goes red.
 */
class SandWebParityTest {

    @Test
    fun `the web route reaches the same Sand boards as the app`() {
        val lines = PARITY_DATES.flatMap { date ->
            Difficulty.entries.map { tier ->
                val seed = DailySeed.seedFor(date, Sand.id, tier)
                val epochDay = SeedHash.epochDay(date.year, date.monthValue, date.dayOfMonth)
                assertEquals(seed, SeedHash.daily(epochDay, Sand.id, tier))
                ParityFingerprint.line(Sand, date.toString(), tier, seed)
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
            -675395325, 1006409573, 632151471, 227227002, 724369054, 1310358744,
            -559530097, 1118755896, 2040274016,
        )
    }
}
