package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.SeedHash
import com.joebywan.daybook.puzzles.Kings
import com.joebywan.daybook.puzzles.KingsState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * The web build (`web/`) has no `java.time`, so it reaches the daily seed through
 * [SeedHash.epochDay] and [SeedHash.daily] instead of [DailySeed.seedFor]. These pin the two
 * routes to the same board.
 *
 * [KINGS_FINGERPRINTS] is also what the web page prints with `?dump`; the Playwright check in
 * `web/` compares the browser's output against this list line for line.
 */
class WebParityTest {

    @Test
    fun `epochDay agrees with LocalDate for every day across four centuries`() {
        var date = LocalDate.of(1900, 1, 1)
        val end = LocalDate.of(2300, 12, 31)
        while (!date.isAfter(end)) {
            assertEquals(
                "epochDay($date)",
                date.toEpochDay(),
                SeedHash.epochDay(date.year, date.monthValue, date.dayOfMonth),
            )
            date = date.plusDays(1)
        }
    }

    @Test
    fun `the web route reaches the same Kings boards as the app`() {
        val lines = PARITY_DATES.flatMap { date ->
            Difficulty.entries.map { tier ->
                val seed = DailySeed.seedFor(date, Kings.id, tier)
                assertEquals(
                    seed,
                    SeedHash.daily(SeedHash.epochDay(date.year, date.monthValue, date.dayOfMonth), Kings.id, tier),
                )
                fingerprint(date.toString(), tier, seed, Kings.generate(seed, tier) as KingsState)
            }
        }
        lines.forEach(::println)
        assertEquals(KINGS_FINGERPRINTS, lines)
    }

    private fun fingerprint(date: String, tier: Difficulty, seed: Long, s: KingsState) =
        "$date ${tier.name} seed=$seed regions=${s.region.joinToString("")} " +
            "kings=${s.solution.sorted().joinToString(",")}"

    private companion object {
        val PARITY_DATES = listOf(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 9, 30),
            LocalDate.of(2027, 2, 28),
        )

        val KINGS_FINGERPRINTS = listOf(
            "2026-01-01 STANDARD seed=7195351499143569672 regions=1110000111023314442234444333646455564665556666555 kings=3,7,18,27,29,40,44",
            "2026-01-01 HARD seed=-5638635270955423271 regions=1102555511025555122222551442335544444455444665554466665746666667 kings=2,8,19,29,33,46,52,63",
            "2026-01-01 EXPERT seed=-3298197129111590415 regions=000000222000103222033333222444333332466635322466665777666667777668868777666888777 kings=1,12,26,33,36,50,56,70,76",
            "2026-09-30 STANDARD seed=-5283952271002325829 regions=1100003111000311220331113335411153544455556665555 kings=4,8,17,26,28,41,44",
            "2026-09-30 HARD seed=-8420814797415091093 regions=1110000011111003211110336611553366455333664555536666555366665557 kings=5,11,16,30,34,44,49,63",
            "2026-09-30 EXPERT seed=3264081092823804759 regions=111110000111112000111112200311222220331222224377566644777666444778886844777888884 kings=7,11,24,27,44,48,59,64,76",
            "2027-02-28 STANDARD seed=1468448476811450933 regions=0044111203444120334415333344553364455566645566664 kings=1,13,14,24,33,37,46",
            "2027-02-28 HARD seed=-3266383649279483527 regions=1110000211110002415102224451322244553525445555554655555566667755 kings=6,10,23,28,32,43,49,61",
            "2027-02-28 EXPERT seed=-3068739174108119540 regions=222000000222000001224444033222444333224444443856647433866667733886677333888677777 kings=6,17,18,34,40,46,57,68,74",
        )
    }
}
