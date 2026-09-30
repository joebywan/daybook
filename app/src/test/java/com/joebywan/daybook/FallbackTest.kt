package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Atoms
import com.joebywan.daybook.puzzles.AtomsState
import com.joebywan.daybook.puzzles.Lits
import com.joebywan.daybook.puzzles.LitsState
import com.joebywan.daybook.puzzles.Shikaku
import com.joebywan.daybook.puzzles.ShikakuState
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
