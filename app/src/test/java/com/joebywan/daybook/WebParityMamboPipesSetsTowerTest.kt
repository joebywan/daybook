package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.SeedHash
import com.joebywan.daybook.puzzles.Mambo
import com.joebywan.daybook.puzzles.MamboState
import com.joebywan.daybook.puzzles.Pipes
import com.joebywan.daybook.puzzles.PipesState
import com.joebywan.daybook.puzzles.Sets
import com.joebywan.daybook.puzzles.SetsState
import com.joebywan.daybook.puzzles.Sym
import com.joebywan.daybook.puzzles.Tower
import com.joebywan.daybook.puzzles.TowerState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Mambo, Pipes, Sets and Tower on the web route, pinned the way [WebParityTest] pins Kings. The
 * web page prints the same lines under `?puzzle=<id>&dump` (see `web/.../MoreBoards.kt`), so the
 * two can be diffed line for line.
 *
 * Set `DAYBOOK_PARITY_DUMP` to a file path to also write every tier for 365 days from 2026-01-01
 * — the JVM half of the year-long diff against the browser, and against origin/main.
 */
class WebParityMamboPipesSetsTowerTest {

    @Test
    fun `the web route reaches the same Mambo, Pipes, Sets and Tower boards as the app`() {
        val lines = PUZZLES.flatMap { id ->
            PARITY_DATES.flatMap { date ->
                Difficulty.entries.map { tier ->
                    val seed = DailySeed.seedFor(date, id, tier)
                    val epochDay = SeedHash.epochDay(date.year, date.monthValue, date.dayOfMonth)
                    assertEquals(seed, SeedHash.daily(epochDay, id, tier))
                    fingerprint(id, date.toString(), tier, seed)
                }
            }
        }
        lines.forEach(::println)
        assertEquals(FINGERPRINTS, lines.map { it.hashCode() })
    }

    @Test
    fun `dump a year of boards when asked`() {
        val out = System.getenv("DAYBOOK_PARITY_DUMP") ?: return
        val start = LocalDate.of(2026, 1, 1)
        val lines = PUZZLES.flatMap { id ->
            (0 until 365).flatMap { i ->
                val date = start.plusDays(i.toLong())
                Difficulty.entries.map { tier ->
                    fingerprint(id, date.toString(), tier, DailySeed.seedFor(date, id, tier))
                }
            }
        }
        File(out).writeText(lines.joinToString("\n", postfix = "\n"))
    }

    companion object {
        val PUZZLES = listOf(Mambo.id, Pipes.id, Sets.id, Tower.id)

        val PARITY_DATES = listOf(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 9, 30),
            LocalDate.of(2027, 2, 28),
        )

        /** Same format as `boardFingerprint` in the web build's MoreBoards.kt. */
        fun fingerprint(id: String, date: String, tier: Difficulty, seed: Long): String {
            val head = "$id $date ${tier.name} seed=$seed"
            return when (id) {
                Mambo.id -> {
                    val s = Mambo.generate(seed, tier) as MamboState
                    "$head n=${s.size} givens=${s.givens.joinToString("") { if (it) "1" else "0" }} " +
                        "links=${s.links.joinToString(",") { "${it.a}${if (it.same) "=" else "x"}${it.b}" }} " +
                        "solution=${s.solution.joinToString("") { if (it == Sym.SUN) "S" else "M" }}"
                }
                Pipes.id -> {
                    val s = Pipes.generate(seed, tier) as PipesState
                    "$head w=${s.width} h=${s.height} source=${s.source} " +
                        "cells=${s.cells.joinToString("") { it.toString(16) }}"
                }
                Sets.id -> {
                    val s = Sets.generate(seed, tier) as SetsState
                    "$head target=${s.target} " +
                        "cards=${s.cards.joinToString(",") { c -> c.traits.joinToString("") }} " +
                        "sets=${Sets.allSets(s.cards).joinToString(";") { it.joinToString(",") }}"
                }
                Tower.id -> {
                    val s = Tower.generate(seed, tier) as TowerState
                    "$head slots=${s.slots} colours=${s.colours} max=${s.maxGuesses} " +
                        "secret=${s.secret.joinToString("")}"
                }
                else -> error("no fingerprint for $id")
            }
        }

        /** Hashes of the 36 lines, from origin/main; [String.hashCode] keeps long lines out of the source. */
        val FINGERPRINTS = listOf(
            -74729595, 72816106, -1651204067, -403225035, 2118756166, -501873515,
            -963207489, -2128964559, -1588366004, 353526674, -364134997, 556468274,
            1422106462, 1149481969, 1249282849, -1614399372, -1175674472, 1121722490,
            -1080634186, -1608293369, 614368307, 2039857322, -1069404979, -1213940784,
            1152618777, 99657470, 1663685138, -474855772, -1649481873, 596114753,
            -815522338, -573593446, 974012700, 1860367855, 851938813, -528490920,
        )
    }
}
