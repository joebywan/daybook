package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A digit's move-feel unit: finished when every row, column and box holds it exactly once, read here square by square. */
class SudokuDigitUnitTest {
    // Every row, column and 3x3 box holds [d] exactly once, counted with nested loops rather than sets of indices.
    private fun oracle(cells: List<Int>, d: Int): Boolean {
        for (k in 0 until 9) {
            var row = 0; var col = 0; var box = 0
            for (j in 0 until 9) {
                if (cells[k * 9 + j] == d) row++
                if (cells[j * 9 + k] == d) col++
                if (cells[(k / 3 * 3 + j / 3) * 9 + k % 3 * 3 + j % 3] == d) box++
            }
            if (row != 1 || col != 1 || box != 1) return false
        }
        return true
    }

    @Test fun agreesWithTheOracleOnFinishedAndBrokenBoards() {
        val solved = (Sudoku.generate(3, Difficulty.HARD) as SudokuState).solution
        for (d in 1..9) assertTrue(solved.allNine(d))
        val rng = java.util.Random(7)
        var broken = 0
        repeat(3000) {
            val cells = solved.toMutableList()
            // Blank some squares, and sometimes write a digit over another.
            repeat(rng.nextInt(4)) { cells[rng.nextInt(81)] = 0 }
            repeat(rng.nextInt(3)) { cells[rng.nextInt(81)] = 1 + rng.nextInt(9) }
            // Sometimes swap two squares, which keeps every digit's count but can break its rows, columns or boxes.
            if (rng.nextBoolean()) {
                val a = rng.nextInt(81); val b = rng.nextInt(81)
                val t = cells[a]; cells[a] = cells[b]; cells[b] = t
            }
            for (d in 1..9) {
                val want = oracle(cells, d)
                if (!want && cells.count { it == d } == 9) broken++
                assertEquals(want, cells.allNine(d))
            }
        }
        // The sample did reach nine of a digit standing wrongly, the case a count alone would pass.
        assertTrue(broken > 100)
    }

    @Test fun eightOfADigitIsNotFinished() {
        val cells = (Sudoku.generate(3, Difficulty.HARD) as SudokuState).solution.toMutableList()
        cells[cells.indexOf(5)] = 0
        assertFalse(cells.allNine(5))
        assertEquals(8, cells.squaresOf(5).size)
    }
}
