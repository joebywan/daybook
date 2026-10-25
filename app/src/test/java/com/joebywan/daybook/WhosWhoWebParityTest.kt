package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.ParityFingerprint
import com.joebywan.daybook.core.SeedHash
import com.joebywan.daybook.puzzles.WhosWho
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Who's Who on the web route, pinned the way [TallyWebParityTest] pins Tally: the web page prints the same lines under
 * `?dump`, and [WebParityDumpTest] writes the JVM's side of a whole year. A board is a pure function of its seed and
 * `Rng`, so these also pin the generator and the word lists: a change to the draw, the clue pool, the ladder, the count
 * or the order of a list moves boards and goes red.
 */
class WhosWhoWebParityTest {

    @Test
    fun `the web route reaches the same Who's Who boards as the app`() {
        val lines = PARITY_DATES.flatMap { date ->
            Difficulty.entries.map { tier ->
                val seed = DailySeed.seedFor(date, WhosWho.id, tier)
                val epochDay = SeedHash.epochDay(date.year, date.monthValue, date.dayOfMonth)
                assertEquals(seed, SeedHash.daily(epochDay, WhosWho.id, tier))
                ParityFingerprint.line(WhosWho, date.toString(), tier, seed)
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
            442362151, 1992391853, 1249930716, -1638168872, -1221809020, -393160183,
            -648383963, -1632276826, 1245495051,
        )
    }
}
