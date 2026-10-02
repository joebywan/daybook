package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.undone
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.puzzles.Sets
import com.joebywan.daybook.puzzles.SetsState
import com.joebywan.daybook.puzzles.Sudoku
import com.joebywan.daybook.puzzles.SudokuState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Selecting a cell or picking a card is a state, hence an undo entry. Undo must take back *moves*:
 * it used to land on the board with the selection the move was made from (a highlight left on the
 * cell just emptied) and the next press then changed nothing visible. Driven the way PlayScreen
 * drives it: every state handed over is pushed onto the history.
 */
class UndoSelectionTest {

    private class Game(var state: PuzzleState) {
        var history: List<PuzzleState> = emptyList()
        fun push(next: PuzzleState) { history = history + state; state = next }
        fun undo(puzzle: com.joebywan.daybook.core.PuzzleType): Boolean {
            val u = undone(puzzle, state, history) ?: return false
            state = u.state; history = u.history
            return true
        }
    }

    private fun sudoku() = Sudoku.generate(
        DailySeed.seedFor(LocalDate.of(2026, 3, 5), "sudoku", Difficulty.STANDARD), Difficulty.STANDARD,
    ) as SudokuState

    @Test
    fun `undoing a Sudoku digit leaves no cell selected and the next undo goes on to the previous move`() {
        val start = sudoku()
        val empty = start.cells.indices.filter { !start.givens[it] }.take(2)
        val g = Game(start)
        g.push(start.select(empty[0])); g.push((g.state as SudokuState).withCell(empty[0], 5))
        g.push((g.state as SudokuState).select(empty[1])); g.push((g.state as SudokuState).withCell(empty[1], 6))

        assertTrue(g.undo(Sudoku))
        var s = g.state as SudokuState
        assertEquals(0, s.cells[empty[1]]); assertEquals(5, s.cells[empty[0]]); assertNull(s.selected)

        assertTrue(g.undo(Sudoku))   // the second digit's selection is not a step of its own
        s = g.state as SudokuState
        assertEquals(0, s.cells[empty[0]]); assertNull(s.selected)
        assertEquals(start.cells, s.cells)
        assertEquals(0, s.moves)

        assertTrue(!g.undo(Sudoku))   // nothing left: no dead step, no change
    }

    @Test
    fun `a bare selection is cleared by undo, then undo has nothing to do`() {
        val start = sudoku()
        val g = Game(start)
        g.push(start.select(3))
        assertTrue(g.undo(Sudoku))
        assertNull((g.state as SudokuState).selected)
        assertTrue(!g.undo(Sudoku))
    }

    @Test
    fun `undoing a claimed set restores the board without the two picks that led to it`() {
        val board = Sets.generate(
            DailySeed.seedFor(LocalDate.of(2026, 2, 1), "sets", Difficulty.HARD), Difficulty.HARD,
        ) as SetsState
        val trios = Sets.allSets(board.cards)
        val g = Game(board)
        for (index in trios[0]) g.push(Sets.tap(g.state as SetsState, index))
        assertEquals(1, (g.state as SetsState).found.size)
        for (index in trios[1]) g.push(Sets.tap(g.state as SetsState, index))
        assertEquals(2, (g.state as SetsState).found.size)

        assertTrue(g.undo(Sets))
        var s = g.state as SetsState
        assertEquals(listOf(trios[0].sorted()), s.found); assertTrue(s.selected.isEmpty())

        assertTrue(g.undo(Sets))
        s = g.state as SetsState
        assertTrue(s.found.isEmpty()); assertTrue(s.selected.isEmpty())
        assertTrue(!g.undo(Sets))
    }

    @Test
    fun `a rejected pick is not a move for undo to take back`() {
        val board = Sets.generate(
            DailySeed.seedFor(LocalDate.of(2026, 2, 1), "sets", Difficulty.HARD), Difficulty.HARD,
        ) as SetsState
        val cards = board.cards
        val trios = Sets.allSets(cards)
        val bad = cards.indices.toList().let { all ->
            all.flatMap { a -> all.filter { it > a }.flatMap { b -> all.filter { it > b }.map { c -> listOf(a, b, c) } } }
        }.first { !Sets.isSet(cards[it[0]], cards[it[1]], cards[it[2]]) }
        val g = Game(board)
        for (index in trios[0]) g.push(Sets.tap(g.state as SetsState, index))
        for (index in bad) g.push(Sets.tap(g.state as SetsState, index))
        assertTrue((g.state as SetsState).lastWrong)

        assertTrue(g.undo(Sets))
        val s = g.state as SetsState
        // The flash and the three picks are not moves, so Undo goes on to the last claim.
        assertEquals(0, s.found.size)
        assertTrue(s.selected.isEmpty() && !s.lastWrong)
    }

    @Test
    fun `undo keeps the move count of the board it restores`() {
        val start = sudoku()
        val cell = start.cells.indices.first { !start.givens[it] }
        val g = Game(start)
        g.push(start.select(cell)); g.push((g.state as SudokuState).withCell(cell, 1))
        g.push((g.state as SudokuState).select(cell)); g.push((g.state as SudokuState).withCell(cell, 2))
        g.undo(Sudoku)
        assertEquals(1, (g.state as SudokuState).moves)
    }
}
