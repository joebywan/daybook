package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.ParityFingerprint
import com.joebywan.daybook.core.SeedHash
import com.joebywan.daybook.puzzles.WordWheel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Word Wheel on the web route, pinned the way [TargetWebParityTest] pins Target: the web page prints the same lines under
 * `?dump`, and [WebParityDumpTest] writes the JVM's side of a whole year. A board is a pure function of its seed, the `Rng`
 * and the generated lists, so these also pin the lists' order: adding or removing a word, or changing the layout rules,
 * moves boards and goes red on purpose.
 */
class WordWheelWebParityTest {

    @Test
    fun `the web route reaches the same Word Wheel boards as the app`() {
        val lines = PARITY_DATES.flatMap { date ->
            Difficulty.entries.map { tier ->
                val seed = DailySeed.seedFor(date, WordWheel.id, tier)
                val epochDay = SeedHash.epochDay(date.year, date.monthValue, date.dayOfMonth)
                assertEquals(seed, SeedHash.daily(epochDay, WordWheel.id, tier))
                ParityFingerprint.line(WordWheel, date.toString(), tier, seed)
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
            1018889751, 1954803247, 1266937928, -936734007, 2126808380, -1562429087,
            907427678, -773029108, 1000227698,
        )
    }
}
