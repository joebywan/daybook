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
 * The web page prints the same lines, prefixed with `kings`, under `?dump` (see CLAUDE.md, "Web
 * build", and [WebParityDumpTest]); diffing the two is how parity with the browser is checked.
 */
/** The dates every web parity pin is taken on; the web page's `?dump` prints the same three. */
internal val PARITY_DATES: List<LocalDate> = listOf(
    LocalDate.of(2026, 1, 1),
    LocalDate.of(2026, 9, 30),
    LocalDate.of(2027, 2, 28),
)

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
        val KINGS_FINGERPRINTS = listOf(
            "2026-01-01 STANDARD seed=7195351499143569672 regions=1100000111100211130221333022533346253334625533662 kings=3,8,20,23,32,35,47",
            "2026-01-01 HARD seed=-5638635270955423271 regions=1110000311120333122203331442553644425536454556664555566644476666 kings=5,8,18,30,33,44,55,59",
            "2026-01-01 EXPERT seed=-3298197129111590415 regions=333000111333002111334022211334442222444442222444446655447776666777778666777778666 kings=3,16,22,27,38,53,60,64,77",
            "2026-09-30 STANDARD seed=-5283952271002325829 regions=1100002111102211122223333322333342253344445554446 kings=3,8,19,23,32,35,48",
            "2026-09-30 HARD seed=-8420814797415091093 regions=3330000033222011332200443333444435554444555554446557544466677444 kings=5,15,19,25,38,42,48,60",
            "2026-09-30 EXPERT seed=3264081092823804759 regions=441000000441110022441700222477777336444773336544777366777778666777778866777888866 kings=5,12,26,33,38,45,61,64,76",
            "2027-02-28 STANDARD seed=1468448476811450933 regions=0044111203444120334415333344553364455566645566664 kings=1,13,14,24,33,37,46",
            "2027-02-28 HARD seed=-3266383649279483527 regions=0000001102001111222011113322211133221114635544446665774467777774 kings=4,14,18,24,39,43,49,61",
            "2027-02-28 EXPERT seed=-3068739174108119540 regions=000001111000022212000222212000322222406333252466633755446333757846637777888667777 kings=4,16,23,30,36,53,56,69,73",
        )
    }
}
