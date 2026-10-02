package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.SeedHash
import com.joebywan.daybook.puzzles.LexiconRules
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Lexicon on the web route, pinned the way [WebParityMamboPipesSetsTowerTest] pins Tower: the web
 * page prints the same lines under `?dump`, and [WebParityDumpTest] writes the JVM's side of a whole
 * year. The answer is an index into a sorted list, so these pin both the seed arithmetic and the
 * lists' order: reordering a list, or adding a word, moves every board and goes red here.
 */
class LexiconWebParityTest {

    @Test
    fun `the web route reaches the same Lexicon boards as the app`() {
        val lines = PARITY_DATES.flatMap { date ->
            Difficulty.entries.map { tier ->
                val seed = DailySeed.seedFor(date, "words", tier)
                val epochDay = SeedHash.epochDay(date.year, date.monthValue, date.dayOfMonth)
                assertEquals(seed, SeedHash.daily(epochDay, "words", tier))
                val s = LexiconRules.newBoard(seed, tier)
                "words $date ${tier.name} seed=$seed length=${s.length} max=${s.maxGuesses} answer=${s.answer}"
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
            -140410173, -1938919453, -1763740956, -838444861, -1288154454, -1817746797,
            -257695951, -1426351997, -297379001,
        )
    }
}
