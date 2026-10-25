package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.puzzles.Atoms
import com.joebywan.daybook.puzzles.Cages
import com.joebywan.daybook.puzzles.AtomsState
import com.joebywan.daybook.puzzles.Kings
import com.joebywan.daybook.puzzles.Lits
import com.joebywan.daybook.puzzles.LitsState
import com.joebywan.daybook.puzzles.Shikaku
import com.joebywan.daybook.puzzles.ShikakuState
import com.joebywan.daybook.puzzles.Cipher
import com.joebywan.daybook.puzzles.DominoLogic
import com.joebywan.daybook.puzzles.Inequality
import com.joebywan.daybook.puzzles.Snap
import com.joebywan.daybook.puzzles.Regions
import com.joebywan.daybook.puzzles.Tally
import com.joebywan.daybook.puzzles.TallyLogic
import com.joebywan.daybook.puzzles.Sand
import com.joebywan.daybook.puzzles.Pointers
import com.joebywan.daybook.puzzles.Target
import com.joebywan.daybook.puzzles.WordWheel
import com.joebywan.daybook.puzzles.Tents
import com.joebywan.daybook.puzzles.Sudoku
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
     * Walks every tier of 2026's daily seeds through [verified] (null when nothing was proved), asserts no day gave up,
     * and that the first [shipsChecked] days of each tier are what [PuzzleType.generate] ships.
     */
    private fun assertYearProved(
        p: PuzzleType,
        fellBack: String = "shipped its last resort",
        unit: String = "days",
        shipsChecked: Long = 365,
        verified: (Long, Difficulty) -> Any?,
    ) {
        val start = LocalDate.of(2026, 1, 1)
        val gaveUp = mutableListOf<String>()
        for (difficulty in Difficulty.entries) {
            for (day in 0 until 365L) {
                val date = start.plusDays(day)
                val seed = DailySeed.seedFor(date, p.id, difficulty)
                val proved = verified(seed, difficulty)
                if (proved == null) { gaveUp += "$date/${difficulty.name}"; continue }
                if (day < shipsChecked) assertEquals("${p.id} $date/${difficulty.name}", proved, p.generate(seed, difficulty))
            }
        }
        assertTrue("${p.id} $fellBack on ${gaveUp.size} $unit: $gaveUp", gaveUp.isEmpty())
    }

    /**
     * A whole year of real daily seeds on every tier, not the twenty probe seeds the others use.
     * Before strict growth, 13 Expert days of 2026 (2026-01-09 the first) shipped the
     * three-atom chain — 3.6%, which twenty seeds miss about half the time. Run against that
     * generator, this test names all thirteen. It asserts on [Atoms.generateVerified], the path
     * that carries a proof, so no property the fallback happens to share can let it through.
     */
    @Test
    fun `atoms proves every daily board of a year on every tier`() =
        assertYearProved(Atoms, fellBack = "fell back to the three-atom chain", unit = "boards") { seed, d -> Atoms.generateVerified(seed, d) }

    /**
     * The tier names how many atoms a board has (10 / 18 / 28), and the growth loop used to give up
     * early on a cramped seed and ship what it had: 2026-11-11 came out with 6 atoms and 2027-07-23
     * and 2027-11-19 with 9, all Standard, and still valid, still unique, so nothing else noticed.
     * Two years, since 2026 holds only one of them. The counts are written out here rather than read
     * from the generator, so a generator that quietly lowers its own target cannot pass.
     */
    @Test
    fun `atoms boards always have the atom count their tier asks for`() {
        val target = mapOf(Difficulty.STANDARD to 10, Difficulty.HARD to 18, Difficulty.EXPERT to 28)
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
    fun `lits proves every daily board in a year`() = assertYearProved(Lits, fellBack = "shipped an unproved board") { seed, d -> Lits.generateVerified(seed, d) }

    /**
     * Kings used to be pinned by thirty probe seeds a tier, which cannot see a fallback rate of a
     * few in a hundred. A year of real daily seeds on every tier, through [Kings.generateVerified].
     */
    @Test
    fun `kings proves every daily board of a year on every tier`() = assertYearProved(Kings, fellBack = "shipped an unproved board") { seed, d -> Kings.generateVerified(seed, d) }

    /**
     * Shikaku proves its single tiling with `countTilings` and gates the hint walk; its last resort (one fixed
     * seed's board) is proved but is not what the generator is for. A year per tier.
     */
    @Test
    fun `shikaku proves every daily board of a year on every tier`() = assertYearProved(Shikaku, fellBack = "shipped its corner-clue fallback") { seed, d -> Shikaku.generateVerified(seed, d) }

    /**
     * [Snap.generateVerified] returns a board only when the search proved one answer inside
     * the clue budget; the fallback numbers every square. A year per tier.
     */
    @Test
    fun `snap proves every daily board of a year on every tier`() =
        // generate() would search the same board a second time, and Expert boards cost
        // seconds apiece, so only a fortnight per tier is checked to be what ships.
        assertYearProved(Snap, fellBack = "shipped its number-every-square fallback", shipsChecked = 14) { seed, d -> Snap.generateVerified(seed, d) }

    /** [Inequality.generateVerified] returns a board only when logic finished it and a search proved one answer. */
    @Test
    fun `inequality proves every daily board of a year on every tier`() = assertYearProved(Inequality) { seed, d -> Inequality.generateVerified(seed, d) }

    /** [Cipher.generateVerified] returns a board only when the ladder finished its sum and the search proved one answer. */
    @Test
    fun `cipher proves every daily board of a year on every tier`() = assertYearProved(Cipher) { seed, d -> Cipher.generateVerified(seed, d) }

    /** [Target.generateVerified] returns a board only when the exhaustive table put the target exactly the tier's number of steps away. */
    @Test
    fun `target proves every daily board of a year on every tier`() = assertYearProved(Target) { seed, d -> Target.generateVerified(seed, d) }

    /** [WordWheel.generateVerified] returns a board only when it was laid to the tier's size and hints leave a small choice at every step. */
    @Test
    fun `wordwheel proves every daily board of a year on every tier`() = assertYearProved(WordWheel) { seed, d -> WordWheel.generateVerified(seed, d) }

    /** [Sand.generateVerified] returns a board only when the search found a sorting road and the naive player does not always sort it. */
    @Test
    fun `sand proves every daily board of a year on every tier`() = assertYearProved(Sand) { seed, d -> Sand.generateVerified(seed, d) }

    /**
     * [Pointers.generateVerified] returns a board only when it covered the grid to the tier's density and the greedy
     * clear emptied it; the last resort is rows of arrows all pointing right.
     */
    @Test
    fun `pointers proves every daily board of a year on every tier`() = assertYearProved(Pointers) { seed, d -> Pointers.generateVerified(seed, d) }

    /** [Regions.generateVerified] returns a board only when logic finished it and a search proved one answer. */
    @Test
    fun `regions proves every daily board of a year on every tier`() = assertYearProved(Regions) { seed, d -> Regions.generateVerified(seed, d) }

    /** [Tents.generateVerified] returns a board only when logic finished it and a search proved one answer. */
    @Test
    fun `tents proves every daily board of a year on every tier`() = assertYearProved(Tents) { seed, d -> Tents.generateVerified(seed, d) }

    /** [Sudoku.generateVerified] returns a board only when the tier's ladder finished it and a search proved one answer. */
    @Test
    fun `sudoku proves every daily board of a year on every tier`() = assertYearProved(Sudoku) { seed, d -> Sudoku.generateVerified(seed, d) }

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
     * [Cages.generateVerified] returns a board only when the ladder finished it and a search proved one answer. Also
     * measures how many attempts a board takes (printed; asserted well under the allowance).
     */
    @Test
    fun `cages proves every daily board of a year on every tier`() {
        val start = LocalDate.of(2026, 1, 1)
        val gaveUp = mutableListOf<String>()
        for (difficulty in Difficulty.entries) {
            val used = IntArray(com.joebywan.daybook.puzzles.CageLogic.ATTEMPTS + 1)
            for (day in 0 until 365L) {
                val date = start.plusDays(day)
                val seed = DailySeed.seedFor(date, Cages.id, difficulty)
                val proved = Cages.generateVerified(seed, difficulty)
                if (proved == null) { gaveUp += "$date/${difficulty.name}"; continue }
                assertEquals("cages $date/${difficulty.name}", proved, Cages.generate(seed, difficulty))
                used[(0 until com.joebywan.daybook.puzzles.CageLogic.ATTEMPTS).first {
                    com.joebywan.daybook.puzzles.CageLogic.attempt(seed, difficulty, it) != null
                }]++
            }
            println("cages ${difficulty.name}: boards by attempts used ${used.toList()}")
            assertTrue("${difficulty.name} came close to the allowance: ${used.toList()}", used.drop(30).all { it == 0 })
        }
        assertTrue("cages shipped its last resort on ${gaveUp.size} days: $gaveUp", gaveUp.isEmpty())
    }

    /**
     * [Tally.generateVerified] returns a board only when the tier's ladder finished it, needing exactly the tier's
     * deepest step, and the count proved one answer. Also measures the redraws (printed): a draw is redrawn when a
     * line keeps all or none of its numbers, when the ladder does not finish it or needs the wrong depth, or when
     * it has several answers. Expert, which must need a what-if, redraws most.
     */
    @Test
    fun `tally proves every daily board of a year on every tier`() {
        val start = LocalDate.of(2026, 1, 1)
        val gaveUp = mutableListOf<String>()
        for (difficulty in Difficulty.entries) {
            var total = 0
            var most = 0
            for (day in 0 until 365L) {
                val date = start.plusDays(day)
                val seed = DailySeed.seedFor(date, Tally.id, difficulty)
                val proved = Tally.generateVerified(seed, difficulty)
                if (proved == null) { gaveUp += "$date/${difficulty.name}"; continue }
                assertEquals("tally $date/${difficulty.name}", proved, Tally.generate(seed, difficulty))
                val used = 1 + (0 until TallyLogic.ATTEMPTS).first { TallyLogic.attempt(seed, difficulty, it) != null }
                total += used
                most = maxOf(most, used)
            }
            println("tally ${difficulty.name}: draws per board mean ${"%.1f".format(total / 365.0)}, most $most of ${TallyLogic.ATTEMPTS}")
            assertTrue("${difficulty.name} came close to the allowance: $most", most < TallyLogic.ATTEMPTS / 2)
        }
        assertTrue("tally shipped its last resort on ${gaveUp.size} days: $gaveUp", gaveUp.isEmpty())
    }

    /**
     * [DominoLogic.generateVerified] returns a board only when the tier's ladder finished it, needing at least the
     * tier's deepest step, and the count proved one answer. Also measures the redraws (printed).
     */
    @Test
    fun `dominoes proves every daily board of a year on every tier`() {
        val start = LocalDate.of(2026, 1, 1)
        val gaveUp = mutableListOf<String>()
        for (difficulty in Difficulty.entries) {
            var total = 0
            var most = 0
            for (day in 0 until 365L) {
                val date = start.plusDays(day)
                val seed = DailySeed.seedFor(date, "dominoes", difficulty)
                val proved = DominoLogic.generateVerified(seed, difficulty)
                if (proved == null) { gaveUp += "$date/${difficulty.name}"; continue }
                assertEquals("dominoes $date/${difficulty.name}", proved, DominoLogic.generate(seed, difficulty))
                val used = 1 + (0 until DominoLogic.ATTEMPTS).first { DominoLogic.attempt(seed, difficulty, it) != null }
                total += used
                most = maxOf(most, used)
            }
            println("dominoes ${difficulty.name}: draws per board mean ${"%.1f".format(total / 365.0)}, most $most of ${DominoLogic.ATTEMPTS}")
            assertTrue("${difficulty.name} came close to the allowance: $most", most < DominoLogic.ATTEMPTS / 4)
        }
        assertTrue("dominoes shipped its last resort on ${gaveUp.size} days: $gaveUp", gaveUp.isEmpty())
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
