package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Lits
import com.joebywan.daybook.puzzles.LitsState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * LITS boards as the web build (`web/`) must reproduce them. The page prints the same lines, each
 * prefixed with `lits`, under `?dump` (see CLAUDE.md, "Web build", and [WebParityDumpTest]).
 *
 * LITS is where hash order came closest to the `Rng`: `quadsContaining` used to pick a candidate
 * out of a `HashSet<List<Int>>`, whose order the JVM and Kotlin/Wasm do not share. These lines were
 * recorded before that was made explicit, so they pin the Android boards as well as the web ones.
 */
class LitsWebParityTest {

    @Test
    fun `LITS boards match the recorded fingerprints`() {
        val lines = PARITY_DATES.flatMap { date -> Difficulty.entries.map { fingerprint(date, it) } }
        lines.forEach(::println)
        assertEquals(LITS_FINGERPRINTS, lines)
    }

    private fun fingerprint(date: LocalDate, tier: Difficulty): String {
        val seed = DailySeed.seedFor(date, Lits.id, tier)
        val s = Lits.generate(seed, tier) as LitsState
        return "$date ${tier.name} seed=$seed regions=${s.region.joinToString("") { it.toString(36) }} " +
            "shading=${s.solution.joinToString("") { if (it) "1" else "0" }}"
    }

    private companion object {
        val PARITY_DATES = listOf(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 9, 30),
            LocalDate.of(2027, 2, 28),
        )

        val LITS_FINGERPRINTS = listOf(
            "2026-01-01 STANDARD seed=-3193151038238997810 regions=433333432553422255402215400011400011 shading=011100010110111011101110101010111111",
            "2026-01-01 HARD seed=5424218418233004022 regions=7555554777754433677413366441233661122006012220001 shading=1011110111001101000101111011010110111010010111111",
            "2026-01-01 EXPERT seed=4934354727460898055 regions=773336695773666957711699555100998511100a8522400a882240aa8224444a shading=1111111001010101010101111111010101011111100101011111100110101111",
            "2026-09-30 STANDARD seed=-3946740114548413781 regions=333112303122301122000444004445555555 shading=110101100111101101111011010110111100",
            "2026-09-30 HARD seed=-260990439081986576 regions=1117777031117603338660233886022288502448550444455 shading=1111111101000111110111010101111111101001010111111",
            "2026-09-30 EXPERT seed=-7424408243145612178 regions=11100004151203445522334455233368572266687776668877aa9888aaa99999 shading=1111111110010101101111111110010110000111111011011011100101101110",
            "2027-02-28 STANDARD seed=3046517752040187933 regions=222222333311334441554011554001554400 shading=001111111001101001101011111110101011",
            "2027-02-28 HARD seed=408844251717672843 regions=5558888155466811444661104463000023370722237777223 shading=1111111101001011110111001110111101110011011110111",
            "2027-02-28 EXPERT seed=-3676759106390584592 regions=52222003552a003355aaa0936aa1109966441199674419986744188867777788 shading=1011110111100111101101011110010110101111101110101010111101111010",
        )
    }
}
