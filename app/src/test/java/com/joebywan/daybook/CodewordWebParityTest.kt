package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.ParityFingerprint
import com.joebywan.daybook.core.SeedHash
import com.joebywan.daybook.puzzles.Codeword
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Codeword on the web route, pinned the way [CipherWebParityTest] pins Cipher: the web page prints the same lines
 * under `?dump`, and [WebParityDumpTest] writes the JVM's side of a whole year. A board is an entry of `CodewordGrids`
 * picked by its seed's `Rng` and numbered by the same `Rng`, its answer proved on the device, so these also pin the
 * list's order, the pick and the numbering: adding, removing or reordering a grid moves boards and goes red.
 */
class CodewordWebParityTest {

    @Test
    fun `the web route reaches the same Codeword boards as the app`() {
        val lines = CipherWebParityTest.PARITY_DATES.flatMap { date ->
            Difficulty.entries.map { tier ->
                val seed = DailySeed.seedFor(date, Codeword.id, tier)
                val epochDay = SeedHash.epochDay(date.year, date.monthValue, date.dayOfMonth)
                assertEquals(seed, SeedHash.daily(epochDay, Codeword.id, tier))
                ParityFingerprint.line(Codeword, date.toString(), tier, seed)
            }
        }
        lines.forEach(::println)
        assertEquals(FINGERPRINTS, lines.map { it.hashCode() })
    }

    companion object {
        /** Hashes of the 9 lines, in the order of [CipherWebParityTest.PARITY_DATES] and the tiers. */
        val FINGERPRINTS = listOf<Int>()
    }
}
