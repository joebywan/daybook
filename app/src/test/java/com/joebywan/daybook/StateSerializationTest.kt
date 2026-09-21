package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.data.SavedGame
import com.joebywan.daybook.puzzles.AtomsState
import com.joebywan.daybook.puzzles.KingsState
import com.joebywan.daybook.puzzles.LitsState
import com.joebywan.daybook.puzzles.MamboState
import com.joebywan.daybook.puzzles.MosaicState
import com.joebywan.daybook.puzzles.PipesState
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.puzzles.Sets
import com.joebywan.daybook.puzzles.SetsState
import com.joebywan.daybook.puzzles.ShikakuState
import com.joebywan.daybook.puzzles.SnapState
import com.joebywan.daybook.puzzles.SudokuState
import com.joebywan.daybook.puzzles.Sym
import com.joebywan.daybook.puzzles.TowerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * A puzzle state that will not round-trip is a board the player silently loses, and the failure is
 * per-puzzle: one forgotten `@Serializable` on a nested class is enough. So every genre is put
 * through the real save path, part-solved, rather than a sample of them pristine.
 */
class StateSerializationTest {

    private val seed = DailySeed.seedFor(LocalDate.of(2026, 4, 7), "roundtrip", Difficulty.HARD)

    @Test
    fun `every puzzle survives the round trip mid-game`() {
        for (puzzle in PuzzleRegistry.all) {
            for (difficulty in Difficulty.entries) {
                val label = "${puzzle.id}/${difficulty.name}"
                val pristine = puzzle.generate(seed, difficulty)
                val played = mutate(pristine)
                assertNotEquals("$label: the test never touched the board", pristine, played)

                // The undo stack goes through the same encoder, so it is exercised here too.
                val saved = SavedGame(played, history = listOf(pristine), hints = 2, seconds = 91)
                val restored = SavedGame.decode(saved.encode())

                assertEquals("$label does not survive being saved", saved, restored)
            }
        }
    }

    @Test
    fun `a long game saves a bounded undo stack rather than a bundle that will not fit`() {
        val board = PuzzleRegistry.all.first().generate(seed, Difficulty.EXPERT)
        val long = SavedGame(board, history = List(400) { board })

        val restored = SavedGame.decode(long.encode())

        assertEquals(SavedGame.UNDO_DEPTH, restored?.history?.size)
        assertEquals("the most recent step must be the one kept", board, restored?.history?.last())
    }

    @Test
    fun `the computed win condition is never written`() {
        for (puzzle in PuzzleRegistry.all) {
            val json = SavedGame(mutate(puzzle.generate(seed, Difficulty.HARD))).encode()
            // `solved` and `failed` are derived from the board every time they are read. Writing
            // them would let a stale flag outlive the position it was computed from.
            assertFalse("${puzzle.id} writes its computed solved flag", json.contains("\"solved\""))
            assertFalse("${puzzle.id} writes its computed failed flag", json.contains("\"failed\""))
            assertTrue("${puzzle.id} wrote nothing recognisable", json.contains("\"moves\""))
        }
    }

    /**
     * One real interaction per genre, chosen to fill the collections a pristine board leaves empty
     * — the blocks in Shikaku, the guesses in Tower, the sets found in Sets. Exhaustive because
     * [PuzzleState] is sealed, so a new puzzle cannot be added without landing here.
     */
    private fun mutate(state: PuzzleState): PuzzleState = when (state) {
        is AtomsState -> state.pairs.indices.take(2).fold(state) { acc, i -> acc.cycle(i) }
        is KingsState -> state.cycle(0).cycle(1)
        is LitsState -> state.toggle(0)
        is MamboState -> state.withCell(state.cells.indices.first { !state.givens[it] }, Sym.SUN)
        is MosaicState -> state.cycle(0).cycle(1)
        is PipesState -> state.rotate(0)
        is SetsState -> state.copy(
            found = listOf(Sets.allSets(state.cards).first()),
            selected = listOf(1),
            lastWrong = true,
            moves = 1,
        )
        is ShikakuState -> state.place(state.solution.first())
        is SnapState -> state.copy(path = listOf(state.waypoints.indexOf(1)), moves = 1)
        is SudokuState -> {
            val cell = state.givens.indexOfFirst { !it }
            state.select(cell).withCell(cell, 5)
        }
        is TowerState -> (0 until state.slots)
            .fold(state) { acc, slot -> acc.withPeg(slot, slot % state.colours) }
            .submit()
    }
}
