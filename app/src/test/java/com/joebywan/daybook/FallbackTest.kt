package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Atoms
import com.joebywan.daybook.puzzles.AtomsState
import com.joebywan.daybook.puzzles.Kings
import com.joebywan.daybook.puzzles.KingsState
import com.joebywan.daybook.puzzles.Lits
import com.joebywan.daybook.puzzles.LitsState
import com.joebywan.daybook.puzzles.Shikaku
import com.joebywan.daybook.puzzles.ShikakuState
import com.joebywan.daybook.puzzles.Inequality
import com.joebywan.daybook.puzzles.Snap
import com.joebywan.daybook.puzzles.SnapState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Several generators keep a degenerate fallback so they can never fail outright. A fallback that
 * fired routinely would still pass the solvability tests while quietly shipping a trivial board,
 * so each one is pinned here by a property only the real generator produces.
 */
class FallbackTest {

    private val seeds = (0 until 20).map {
        DailySeed.seedFor(LocalDate.of(2026, 5, 1).plusDays(it.toLong()), "probe", Difficulty.HARD)
    }

    /**
     * A whole year of real daily seeds on every tier, not the twenty probe seeds the others use.
     * Before the strict attempts, 13 Expert days of 2026 (2026-01-09 the first) shipped the
     * three-atom chain — 3.6%, which twenty seeds miss about half the time. Run against that
     * generator, this test names all thirteen. It asserts on [Atoms.generateVerified], the path
     * that carries a proof, so no property the fallback happens to share can let it through.
     */
    @Test
    fun `atoms proves every daily board of a year on every tier`() {
        val start = LocalDate.of(2026, 1, 1)
        val gaveUp = mutableListOf<String>()
        for (difficulty in Difficulty.entries) {
            for (day in 0 until 365L) {
                val date = start.plusDays(day)
                val seed = DailySeed.seedFor(date, Atoms.id, difficulty)
                val proved = Atoms.generateVerified(seed, difficulty)
                if (proved == null) {
                    gaveUp += "$date/${difficulty.name}"
                    continue
                }
                assertEquals("atoms $date/${difficulty.name}", proved, Atoms.generate(seed, difficulty) as AtomsState)
            }
        }
        assertTrue("atoms fell back to the three-atom chain on ${gaveUp.size} boards: $gaveUp", gaveUp.isEmpty())
    }

    /**
     * The tier names how many atoms a board has (10 / 16 / 24), and the growth loop used to give up
     * early on a cramped seed and ship what it had: 2026-11-11 came out with 6 atoms and 2027-07-23
     * and 2027-11-19 with 9, all Standard, and still valid, still unique, so nothing else noticed.
     * Two years, since 2026 holds only one of them. The counts are written out here rather than read
     * from the generator, so a generator that quietly lowers its own target cannot pass.
     */
    @Test
    fun `atoms boards always have the atom count their tier asks for`() {
        val target = mapOf(Difficulty.STANDARD to 10, Difficulty.HARD to 16, Difficulty.EXPERT to 24)
        val start = LocalDate.of(2026, 1, 1)
        val light = mutableListOf<String>()
        for (difficulty in Difficulty.entries) {
            for (day in 0 until 730L) {
                val date = start.plusDays(day)
                val board = Atoms.generate(DailySeed.seedFor(date, Atoms.id, difficulty), difficulty) as AtomsState
                if (board.atoms.size != target.getValue(difficulty)) {
                    light += "$date/${difficulty.name}=${board.atoms.size}"
                }
            }
        }
        assertTrue("atoms boards short of their tier's atom count: $light", light.isEmpty())
    }

    /**
     * LITS proves uniqueness under the win check's rules, so a fallback is a board a player may
     * solve two ways. A full year of daily seeds per tier, like Atoms: the rate is a few in ten
     * thousand, which twenty seeds would never see.
     */
    @Test
    fun `lits proves every daily board in a year`() {
        val start = LocalDate.of(2026, 1, 1)
        val gaveUp = mutableListOf<String>()
        for (difficulty in Difficulty.entries) {
            for (day in 0 until 365L) {
                val date = start.plusDays(day)
                val seed = DailySeed.seedFor(date, Lits.id, difficulty)
                val proved = Lits.generateVerified(seed, difficulty)
                if (proved == null) { gaveUp += "$date/${difficulty.name}"; continue }
                assertEquals("lits $date/${difficulty.name}", proved, Lits.generate(seed, difficulty) as LitsState)
            }
        }
        assertTrue("lits shipped an unproved board on ${gaveUp.size} days: $gaveUp", gaveUp.isEmpty())
    }

    /**
     * Kings used to be pinned by thirty probe seeds a tier, which cannot see a fallback rate of a
     * few in a hundred. A year of real daily seeds on every tier, through [Kings.generateVerified].
     */
    @Test
    fun `kings proves every daily board of a year on every tier`() {
        val start = LocalDate.of(2026, 1, 1)
        val gaveUp = mutableListOf<String>()
        for (difficulty in Difficulty.entries) {
            for (day in 0 until 365L) {
                val date = start.plusDays(day)
                val seed = DailySeed.seedFor(date, Kings.id, difficulty)
                val proved = Kings.generateVerified(seed, difficulty)
                if (proved == null) { gaveUp += "$date/${difficulty.name}"; continue }
                assertEquals("kings $date/${difficulty.name}", proved, Kings.generate(seed, difficulty) as KingsState)
            }
        }
        assertTrue("kings shipped an unproved board on ${gaveUp.size} days: $gaveUp", gaveUp.isEmpty())
    }

    /**
     * Shikaku proves its single tiling with `countTilings`; its fallback (a clue in each
     * rectangle's corner) is trivially unique but is not what the generator is for. A year per tier.
     */
    @Test
    fun `shikaku proves every daily board of a year on every tier`() {
        val start = LocalDate.of(2026, 1, 1)
        val gaveUp = mutableListOf<String>()
        for (difficulty in Difficulty.entries) {
            for (day in 0 until 365L) {
                val date = start.plusDays(day)
                val seed = DailySeed.seedFor(date, Shikaku.id, difficulty)
                val proved = Shikaku.generateVerified(seed, difficulty)
                if (proved == null) { gaveUp += "$date/${difficulty.name}"; continue }
                assertEquals("shikaku $date/${difficulty.name}", proved, Shikaku.generate(seed, difficulty) as ShikakuState)
            }
        }
        assertTrue("shikaku shipped its corner-clue fallback on ${gaveUp.size} days: $gaveUp", gaveUp.isEmpty())
    }

    /**
     * [Snap.generateVerified] returns a board only when the search proved one answer inside
     * the clue budget; the fallback numbers every square. A year per tier.
     */
    @Test
    fun `snap proves every daily board of a year on every tier`() {
        val start = LocalDate.of(2026, 1, 1)
        val gaveUp = mutableListOf<String>()
        for (difficulty in Difficulty.entries) {
            for (day in 0 until 365L) {
                val date = start.plusDays(day)
                val seed = DailySeed.seedFor(date, Snap.id, difficulty)
                val proved = Snap.generateVerified(seed, difficulty)
                if (proved == null) { gaveUp += "$date/${difficulty.name}"; continue }
                // generate() would search the same board a second time, and Expert boards cost
                // seconds apiece, so only a fortnight per tier is checked to be what ships.
                if (day < 14) assertEquals("snap $date/${difficulty.name}", proved, Snap.generate(seed, difficulty) as SnapState)
            }
        }
        assertTrue("snap shipped its number-every-square fallback on ${gaveUp.size} days: $gaveUp", gaveUp.isEmpty())
    }

    /** [Inequality.generateVerified] returns a board only when logic finished it and a search proved one answer. */
    @Test
    fun `inequality proves every daily board of a year on every tier`() {
        val start = LocalDate.of(2026, 1, 1)
        val gaveUp = mutableListOf<String>()
        for (difficulty in Difficulty.entries) {
            for (day in 0 until 365L) {
                val date = start.plusDays(day)
                val seed = DailySeed.seedFor(date, Inequality.id, difficulty)
                val proved = Inequality.generateVerified(seed, difficulty)
                if (proved == null) { gaveUp += "$date/${difficulty.name}"; continue }
                assertEquals("inequality $date/${difficulty.name}", proved, Inequality.generate(seed, difficulty))
            }
        }
        assertTrue("inequality shipped its last resort on ${gaveUp.size} days: $gaveUp", gaveUp.isEmpty())
    }

    @Test
    fun `lits never falls back to a single region`() {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds) {
                val state = Lits.generate(seed, difficulty) as LitsState
                val regions = state.region.distinct().size
                assertTrue("lits/${difficulty.name}/$seed fell back ($regions regions)", regions >= 4)
                assertTrue(
                    "lits/${difficulty.name}/$seed has no shading in its solution",
                    state.solution.any { it },
                )
            }
        }
    }

    /**
     * Asserts the clue budget, not merely "fewer than every square". The loose version passed
     * happily while Expert boards shipped 39 numbers out of 42 — true, and useless, because the
     * fallback is not the only way to ruin a board with numbers.
     */
    @Test
    fun `snap never falls back to numbering every square`() {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds) {
                val state = Snap.generate(seed, difficulty) as SnapState
                val numbered = state.waypoints.count { it > 0 }
                assertTrue(
                    "snap/${difficulty.name}/$seed numbers $numbered of ${state.cellCount} squares",
                    numbered <= state.cellCount / 3,
                )
            }
        }
    }

    @Test
    fun `shikaku clues always tile the whole grid`() {
        for (difficulty in Difficulty.entries) {
            for (seed in seeds) {
                val state = Shikaku.generate(seed, difficulty) as ShikakuState
                val area = state.clues.filterNotNull().sum()
                assertTrue(
                    "shikaku/${difficulty.name}/$seed clues sum to $area, grid is ${state.width * state.height}",
                    area == state.width * state.height,
                )
                assertTrue(
                    "shikaku/${difficulty.name}/$seed has only one rectangle",
                    state.solution.size >= 4,
                )
            }
        }
    }
}
