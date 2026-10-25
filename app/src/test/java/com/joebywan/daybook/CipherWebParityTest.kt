package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.ParityFingerprint
import com.joebywan.daybook.core.SeedHash
import com.joebywan.daybook.puzzles.Cipher
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Cipher on the web route, pinned the way [InequalityWebParityTest] pins Inequality: the web page
 * prints the same lines under `?dump`, and [WebParityDumpTest] writes the JVM's side of a whole year.
 * A board is an entry of `CipherSums` picked by its seed's `Rng`, its answer proved on the device, so these
 * also pin the list's order and the pick: adding, removing or reordering a sum moves boards and goes red.
 */
class CipherWebParityTest {

    @Test
    fun `the web route reaches the same Cipher boards as the app`() {
        val lines = PARITY_DATES.flatMap { date ->
            Difficulty.entries.map { tier ->
                val seed = DailySeed.seedFor(date, Cipher.id, tier)
                val epochDay = SeedHash.epochDay(date.year, date.monthValue, date.dayOfMonth)
                assertEquals(seed, SeedHash.daily(epochDay, Cipher.id, tier))
                ParityFingerprint.line(Cipher, date.toString(), tier, seed)
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
            -79979298, 1671228490, 601322572, -592290008, 778419263, 639822651,
            -1610681059, 575364404, 783509597,
        )
    }
}
