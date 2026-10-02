package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Block
import com.joebywan.daybook.puzzles.Shikaku
import com.joebywan.daybook.puzzles.ShikakuState
import com.joebywan.daybook.puzzles.Sudoku
import com.joebywan.daybook.puzzles.SudokuState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Sudoku's and Shikaku's `solved` is the rules, not a comparison with the stored answer (Kings and
 * LITS both once refused a correct answer that way; PUZZLE_STANDARDS section 4). The checkers here
 * are written apart from the code under test: Sudoku's reads each line as a string and sorts it,
 * Shikaku's paints every block onto a grid of labels and checks the paint.
 */
class SolvedRulesTest {

    private fun day(i: Int) = LocalDate.of(2026, 1, 1).plusDays(i.toLong())

    // ---- Sudoku: an independent checker ---------------------------------------------------------

    private fun sudokuLegal(g: List<Int>): Boolean {
        if (g.size != 81) return false
        val want = "123456789"
        val rows = (0 until 9).map { r -> (0 until 9).map { c -> g[r * 9 + c] } }
        val cols = (0 until 9).map { c -> (0 until 9).map { r -> g[r * 9 + c] } }
        val boxes = (0 until 9).map { b ->
            (0 until 9).map { k -> g[(b / 3 * 3 + k / 3) * 9 + b % 3 * 3 + k % 3] }
        }
        return (rows + cols + boxes).all { u -> u.sorted().joinToString("") == want }
    }

    private fun sudokuState(cells: List<Int>, givens: List<Boolean>, solution: List<Int>) =
        SudokuState(givens = givens, cells = cells, solution = solution)

    private fun sudokuBoards(): List<SudokuState> = Difficulty.entries.flatMap { d ->
        (0 until 20).map { Sudoku.generate(DailySeed.seedFor(day(it), "sudoku", d), d) as SudokuState }
    }

    /** The same grid with digits [a] and [b] exchanged: legal, and a different answer. */
    private fun relabelled(g: List<Int>, a: Int, b: Int) = g.map { if (it == a) b else if (it == b) a else it }

    /** The same grid with rows 0 and 1 exchanged: both stay in one band, so it is legal. */
    private fun rowsSwapped(g: List<Int>) = (0 until 81).map { i ->
        val r = i / 9
        g[(if (r == 0) 1 else if (r == 1) 0 else r) * 9 + i % 9]
    }

    @Test
    fun `the stored sudoku answer still wins on every tier`() {
        for (s in sudokuBoards()) {
            assertTrue(sudokuLegal(s.solution))
            assertTrue(s.copy(cells = s.solution).solved)
            assertFalse("a fresh board is not solved", s.solved)
        }
    }

    @Test
    fun `a legal sudoku fill that is not the stored one wins`() {
        val base = sudokuBoards()
        for (s in base) {
            val none = List(81) { false }
            for (alt in listOf(relabelled(s.solution, 1, 2), relabelled(s.solution, 3, 9), rowsSwapped(s.solution))) {
                assertNotEquals(s.solution, alt)
                assertTrue(sudokuLegal(alt))
                assertTrue(sudokuState(alt, none, s.solution).solved)
            }
        }
    }

    @Test
    fun `a hand-built sudoku with several answers is solved by either`() {
        // The unavoidable rectangle: 1/2 in rows 0-1, columns 0-1 can be written either way.
        val a = listOf(
            1, 2, 3, 4, 5, 6, 7, 8, 9,
            4, 5, 6, 7, 8, 9, 1, 2, 3,
            7, 8, 9, 1, 2, 3, 4, 5, 6,
            2, 3, 4, 5, 6, 7, 8, 9, 1,
            5, 6, 7, 8, 9, 1, 2, 3, 4,
            8, 9, 1, 2, 3, 4, 5, 6, 7,
            3, 4, 5, 6, 7, 8, 9, 1, 2,
            6, 7, 8, 9, 1, 2, 3, 4, 5,
            9, 1, 2, 3, 4, 5, 6, 7, 8,
        )
        assertTrue(sudokuLegal(a))
        val b = relabelled(a, 1, 2)
        // Pretend the first grid is the stored answer and the second the one the player found.
        assertTrue(sudokuState(a, List(81) { false }, a).solved)
        assertTrue(sudokuState(b, List(81) { false }, a).solved)
    }

    @Test
    fun `sudoku near misses are not solved`() {
        for (s in sudokuBoards().take(6)) {
            val full = s.solution
            val state = s.copy(cells = full)
            for (i in 0 until 81) {
                // One wrong digit: always a repeat in its row.
                for (d in 1..9) if (d != full[i]) {
                    assertFalse(state.copy(cells = full.toMutableList().also { it[i] = d }).solved)
                }
                // One empty cell.
                assertFalse(state.copy(cells = full.toMutableList().also { it[i] = 0 }).solved)
            }
            // Two cells of one row exchanged: rows still hold 1..9, columns do not (or boxes).
            val swapped = full.toMutableList().also { val t = it[0]; it[0] = it[1]; it[1] = t }
            assertEquals(sudokuLegal(swapped), state.copy(cells = swapped).solved)
            // A grid every row of which is legal but whose columns repeat.
            val rows = List(81) { (it % 9) + 1 }
            assertFalse(sudokuLegal(rows))
            assertFalse(state.copy(cells = rows).solved)
        }
    }

    @Test
    fun `sudoku solved agrees with the independent checker on random damage`() {
        var seed = 12345L
        fun next(n: Int): Int { seed = seed * 6364136223846793005L + 1442695040888963407L; return ((seed ushr 33) % n).toInt() }
        for (s in sudokuBoards()) {
            repeat(30) {
                val g = (if (next(2) == 0) relabelled(s.solution, 1 + next(9), 1 + next(9)) else s.solution).toMutableList()
                repeat(next(3)) { g[next(81)] = next(10) }
                assertEquals(sudokuLegal(g), s.copy(cells = g).solved)
            }
        }
    }

    // ---- Shikaku: an independent checker --------------------------------------------------------

    /** Paints each block with its index; legal when every square is painted once and each block has one matching clue. */
    private fun shikakuLegal(w: Int, h: Int, clues: List<Int?>, blocks: List<Block>): Boolean {
        val paint = IntArray(w * h) { -1 }
        for ((n, b) in blocks.withIndex()) {
            if (b.r0 > b.r1 || b.c0 > b.c1) return false
            for (r in b.r0..b.r1) for (c in b.c0..b.c1) {
                if (r !in 0 until h || c !in 0 until w) return false
                if (paint[r * w + c] != -1) return false
                paint[r * w + c] = n
            }
        }
        if (paint.any { it == -1 }) return false
        return blocks.indices.all { n ->
            val numbers = paint.indices.filter { paint[it] == n && clues[it] != null }.map { clues[it]!! }
            val area = paint.count { it == n }
            numbers == listOf(area)
        }
    }

    private fun shikakuBoards(): List<ShikakuState> = Difficulty.entries.flatMap { d ->
        (0 until 20).map { Shikaku.generate(DailySeed.seedFor(day(it), "shikaku", d), d) as ShikakuState }
    }

    /** 4 wide, 2 tall, a 4 at (0,0) and a 4 at (1,3): two 2x2 squares, or two 1x4 rows. */
    private fun twoWays(blocks: List<Block>, solution: List<Block>): ShikakuState {
        val clues = MutableList<Int?>(8) { null }
        clues[0] = 4
        clues[7] = 4
        return ShikakuState(4, 2, clues, blocks, solution)
    }

    private val squares = listOf(Block(0, 0, 1, 1), Block(0, 2, 1, 3))
    private val rows = listOf(Block(0, 0, 0, 3), Block(1, 0, 1, 3))

    @Test
    fun `the stored shikaku answer still wins on every tier`() {
        for (s in shikakuBoards()) {
            assertTrue(shikakuLegal(s.width, s.height, s.clues, s.solution))
            assertTrue(s.copy(blocks = s.solution).solved)
            assertTrue("order is irrelevant", s.copy(blocks = s.solution.reversed()).solved)
            assertFalse(s.solved)
        }
    }

    @Test
    fun `a shikaku tiling that is not the stored one wins`() {
        assertTrue(shikakuLegal(4, 2, twoWays(rows, squares).clues, rows))
        assertTrue(twoWays(rows, squares).solved)
        assertTrue(twoWays(squares, rows).solved)
        assertTrue(twoWays(squares, squares).solved)
    }

    @Test
    fun `shikaku near misses are not solved`() {
        val s = twoWays(emptyList(), squares)
        assertFalse(s.copy(blocks = emptyList()).solved)
        assertFalse(s.copy(blocks = listOf(squares[0])).solved)                      // a block missing
        assertFalse(s.copy(blocks = squares + Block(0, 3, 0, 3)).solved)            // overlap
        assertFalse(s.copy(blocks = squares + squares[0]).solved)                   // the same block twice
        assertFalse(s.copy(blocks = listOf(Block(0, 0, 1, 1), Block(0, 2, 1, 2), Block(0, 3, 1, 3))).solved) // 2 + 2 + 2: right cover, wrong areas
        assertFalse(s.copy(blocks = listOf(Block(0, 0, 0, 2), Block(1, 0, 1, 3), Block(0, 3, 0, 3))).solved) // a 3 on a 4
        assertFalse(s.copy(blocks = listOf(Block(0, 0, 1, 3))).solved)               // one block holding two numbers
        assertFalse(s.copy(blocks = listOf(Block(0, 0, 1, 1), Block(0, 2, 2, 3))).solved) // off the grid
        assertFalse(s.copy(blocks = listOf(Block(0, 0, 1, 1), Block(0, 2, 1, 4))).solved) // off the grid
        // A covering with a block that carries no number at all.
        val clues = MutableList<Int?>(8) { null }
        clues[0] = 4
        assertFalse(ShikakuState(4, 2, clues, squares, squares).solved)
    }

    @Test
    fun `shikaku near misses on real boards are not solved`() {
        for (s in shikakuBoards()) {
            for (b in s.solution) {
                assertFalse(s.copy(blocks = s.solution - b).solved)
                assertFalse(s.copy(blocks = s.solution + b).solved)
                if (b.r1 > b.r0) assertFalse(s.copy(blocks = s.solution - b + b.copy(r1 = b.r1 - 1)).solved)
                if (b.c1 > b.c0) assertFalse(s.copy(blocks = s.solution - b + b.copy(c1 = b.c1 - 1)).solved)
            }
        }
    }

    @Test
    fun `shikaku solved agrees with the independent checker on random damage`() {
        var seed = 987L
        fun next(n: Int): Int { seed = seed * 6364136223846793005L + 1442695040888963407L; return ((seed ushr 33) % n).toInt() }
        for (s in shikakuBoards()) {
            repeat(30) {
                val blocks = s.solution.toMutableList()
                repeat(next(3)) {
                    val i = next(blocks.size)
                    when (next(3)) {
                        0 -> blocks.removeAt(i)
                        1 -> blocks[i] = blocks[i].let { b -> b.copy(r1 = b.r1 + next(2), c1 = b.c1 - next(2).coerceAtMost(b.c1 - b.c0)) }
                        else -> blocks += blocks[i]
                    }
                    if (blocks.isEmpty()) blocks += s.solution[0]
                }
                assertEquals(shikakuLegal(s.width, s.height, s.clues, blocks), s.copy(blocks = blocks).solved)
            }
        }
    }
}
