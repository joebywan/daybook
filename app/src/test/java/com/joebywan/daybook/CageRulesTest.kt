package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.Verdict
import com.joebywan.daybook.puzzles.CageLogic
import com.joebywan.daybook.puzzles.CageState
import com.joebywan.daybook.puzzles.Cages
import com.joebywan.daybook.puzzles.Sudoku
import com.joebywan.daybook.puzzles.SudokuState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cage Sudoku's rules, its win check, and the claim that every board it makes has one answer. The checkers are
 * written apart from the code under test: [legal] reads each line as a sorted string, [CageOracle] counts
 * answers by filling squares in reading order with no candidate tables, and the combination table is rebuilt
 * here by brute force over subsets.
 */
class CageRulesTest {

    private fun board(i: Int, d: Difficulty) =
        CageLogic.generateVerified(daySeed(i, Cages.id, d), d) ?: error("no proved board day $i ${d.name}")

    /** An independent rule check: a Sudoku grid whose cages add up and hold no digit twice. */
    private fun legal(g: List<Int>, cageOf: List<Int>, sums: List<Int>): Boolean {
        if (g.size != 81) return false
        val want = "123456789"
        val rows = (0 until 9).map { r -> (0 until 9).map { c -> g[r * 9 + c] } }
        val cols = (0 until 9).map { c -> (0 until 9).map { r -> g[r * 9 + c] } }
        val boxes = (0 until 9).map { b -> (0 until 9).map { k -> g[(b / 3 * 3 + k / 3) * 9 + b % 3 * 3 + k % 3] } }
        if (!(rows + cols + boxes).all { u -> u.sorted().joinToString("") == want }) return false
        return sums.indices.all { k ->
            val members = (0 until 81).filter { cageOf[it] == k }.map { g[it] }
            members.toSet().size == members.size && members.sum() == sums[k]
        }
    }

    private fun connected(cells: List<Int>): Boolean {
        val seen = mutableSetOf(cells.first())
        val queue = ArrayDeque(listOf(cells.first()))
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            for (n in listOf(c - 9, c + 9, c - 1, c + 1)) {
                if (n !in 0..80 || (n % 9 - c % 9).let { it > 1 || it < -1 }) continue
                if (n in cells && seen.add(n)) queue.addLast(n)
            }
        }
        return seen.size == cells.size
    }

    /** A valid Sudoku grid, and every cell a cage of its own: only the grid's own rules decide. */
    private val grid = Sudoku.TUTORIAL_SOLUTION
    private fun singles() = List(81) { it } to grid

    @Test
    fun `the combination table is every set of different digits with that total`() {
        for (k in 0..9) for (sum in 0..45) {
            val expected = (1 until 512).filter { m ->
                Integer.bitCount(m) == k && (0 until 9).filter { m shr it and 1 == 1 }.sumOf { it + 1 } == sum
            }.map { it shl 1 }.sorted()
            assertEquals("k=$k sum=$sum", expected, CageLogic.combos[k][sum].toList().sorted())
        }
    }

    @Test
    fun `every generated board is a legal answer to its own cages`() {
        for (d in Difficulty.entries) for (i in 0 until 40) {
            val s = board(i, d)
            assertTrue("${d.name} day $i", legal(s.grid.solution, s.cageOf, s.sums))
            for (k in s.sums.indices) assertTrue(connected((0 until 81).filter { s.cageOf[it] == k }))
            assertFalse("a fresh board is not solved", s.solved)
            for (j in 0 until 81) if (s.grid.givens[j]) assertEquals(s.grid.solution[j], s.grid.cells[j]) else assertEquals(0, s.grid.cells[j])
            assertEquals((0 until 81).map { s.cageOf[it] }.distinct().size, s.sums.size)
            assertTrue(CageLogic.cageCells(s.cageOf).all { it.size <= CageLogic.specFor(d).cap })
        }
    }

    @Test
    fun `every sampled board has exactly one answer, by the independent oracle`() {
        for ((d, days) in listOf(Difficulty.STANDARD to 25, Difficulty.HARD to 15, Difficulty.EXPERT to 3)) for (i in 0 until days) {
            val s = board(i, d)
            assertEquals("${d.name} day $i", 1, CageOracle.count(s.cageOf, s.sums, s.grid.cells))
        }
    }

    @Test
    fun `the oracle sees several answers when there are several`() {
        // Dominoes over an empty grid: nothing pins it down.
        val cageOf = CageLogic.dominoes()
        val sums = CageLogic.sumsOf(cageOf, grid)
        assertEquals(2, CageOracle.count(cageOf, sums, List(81) { 0 }))
        // A cage total nothing can make has none.
        val broken = sums.toMutableList().also { it[0] = 40 }
        assertEquals(0, CageOracle.count(cageOf, broken, List(81) { 0 }))
    }

    @Test
    fun `the prover agrees with the oracle on ambiguous and impossible boards`() {
        val cageOf = CageLogic.dominoes()
        val sums = CageLogic.sumsOf(cageOf, grid)
        assertTrue(CageLogic.solutions(CageLogic.Board(cageOf, sums), List(81) { 0 }) is Verdict.Several)
        val broken = sums.toMutableList().also { it[0] = 40 }
        assertTrue(CageLogic.solutions(CageLogic.Board(cageOf, broken), List(81) { 0 }) is Verdict.None)
    }

    @Test
    fun `the last resort is unsolved, legal, and has one answer`() {
        val s = CageLogic.lastResort()
        assertFalse(s.solved)
        assertTrue(legal(s.grid.solution, s.cageOf, s.sums))
        assertEquals(1, CageOracle.count(s.cageOf, s.sums, s.grid.cells))
        val verdict = CageLogic.solutions(CageLogic.Board(s.cageOf, s.sums), s.grid.cells)
        assertTrue(verdict is Verdict.ExactlyOne && verdict.value == s.grid.solution)
    }

    @Test
    fun `the stored answer wins, and so does any other legal fill`() {
        for (d in Difficulty.entries) for (i in 0 until 10) {
            val s = board(i, d)
            assertTrue(s.copy(grid = s.grid.copy(cells = s.grid.solution)).solved)
            // The stored answer is only for hints: with a wrong one stored, the true digits still win.
            val liar = s.grid.copy(cells = s.grid.solution, solution = s.grid.solution.map { it % 9 + 1 })
            assertTrue(s.copy(grid = liar).solved)
        }
    }

    @Test
    fun `each rule alone refuses a fill`() {
        // Sudoku legal, cage totals wrong: digits 1 and 2 swapped everywhere on a board whose cages hold a 1 or a 2.
        val s = board(0, Difficulty.STANDARD)
        val swapped = s.grid.solution.map { if (it == 1) 2 else if (it == 2) 1 else it }
        assertTrue(Sudoku.isSolved(swapped))
        assertNotEquals(s.grid.solution, swapped)
        assertFalse(legal(swapped, s.cageOf, s.sums))
        assertFalse(s.copy(grid = s.grid.copy(cells = swapped)).solved)

        // Sudoku legal, cage totals right, a digit twice in one cage: two 5s as a cage of 10, every other square a cage of its own.
        val fives = grid.indices.filter { grid[it] == 5 }
        // Two 5s of a valid grid never share a row, column or box, so only the cage objects.
        val (a, b) = fives[0] to fives[1]
        val twice = List(81) { if (it == b) a else it }.let { CageLogic.canonical(it) }
        val twiceSums = CageLogic.sumsOf(twice, grid)
        assertEquals(10, twiceSums[twice[a]])
        assertTrue(Sudoku.isSolved(grid))
        assertFalse("a cage of two 5s", CageLogic.isSolved(grid, twice, twiceSums))
        assertFalse(legal(grid, twice, twiceSums))
        // The same total from two different digits is fine.
        val other = grid.indices.first { grid[it] == 4 }
        val fine = CageLogic.canonical(List(81) { if (it == other) a else it })
        assertTrue(CageLogic.isSolved(grid, fine, CageLogic.sumsOf(fine, grid)))

        // Cages right, rows wrong: two squares of a row exchanged, totals taken from the exchanged grid.
        val bad = grid.toMutableList().also { val t = it[0]; it[0] = it[1]; it[1] = t }
        val (cageOf, _) = singles()
        assertFalse(CageLogic.isSolved(bad, cageOf, CageLogic.sumsOf(cageOf, bad)))
    }

    @Test
    fun `the board marks what the cages already show to be wrong`() {
        val s = board(1, Difficulty.STANDARD)
        val cage = CageLogic.cageCells(s.cageOf).first { it.size >= 2 }
        val k = s.cageOf[cage[0]]
        val total = s.sums[k]
        val empty = List(81) { 0 }
        // Under its total with room to spare: nothing to say.
        assertEquals(emptySet<Int>(), CageLogic.cageConflicts(empty.toMutableList().also { it[cage[0]] = 1 }, s.cageOf, s.sums))
        // A digit twice.
        val twice = empty.toMutableList().also { it[cage[0]] = 1; it[cage[1]] = 1 }
        assertTrue(CageLogic.cageConflicts(twice, s.cageOf, s.sums).containsAll(listOf(cage[0], cage[1])))
        // Over the total.
        val over = empty.toMutableList().also { it[cage[0]] = 9 }
        if (total < 9) assertEquals(setOf(cage[0]), CageLogic.cageConflicts(over, s.cageOf, s.sums).filter { it in cage }.toSet())
        // Full and wrong, then full and right.
        val right = empty.toMutableList().also { for (c in cage) it[c] = s.grid.solution[c] }
        assertEquals(emptySet<Int>(), CageLogic.cageConflicts(right, s.cageOf, s.sums))
        val off = right.toMutableList().also { it[cage[0]] = it[cage[0]] % 9 + 1 }
        if (off[cage[0]] != right[cage[1]] && cage.size == 2) {
            assertEquals(cage.toSet(), CageLogic.cageConflicts(off, s.cageOf, s.sums))
        }
    }

    @Test
    fun `placing a digit strikes it from the marks of the rest of its cage in the same state`() {
        val s = board(2, Difficulty.STANDARD)
        val cage = CageLogic.cageCells(s.cageOf).first { c -> c.size >= 2 && c.all { !s.grid.givens[it] } }
        val a = cage[0]
        val b = cage[1]
        // A 7 pencilled in a cage-mate that does not share a row, column or box with the square, and a 3 beside it.
        var now = s.toggleNote(b, 7).toggleNote(b, 3)
        val before = now
        now = now.withCell(a, 7)
        assertEquals(1, now.moves - before.moves)
        assertFalse("the 7 mark is gone", now.grid.hasNote(b, 7))
        assertTrue("the 3 mark stays", now.grid.hasNote(b, 3))
        assertTrue(before.grid.hasNote(b, 7))
        // Erasing does not put it back.
        assertFalse(now.withCell(a, 0).grid.hasNote(b, 7))
    }

    @Test
    fun `the tiers get harder in the order they are offered`() {
        val givens = Difficulty.entries.map { d -> (0 until 60).sumOf { board(it, d).grid.givens.count { g -> g } } / 60.0 }
        println("mean givens $givens")
        assertTrue("fewer givens each tier: $givens", givens[0] > givens[1] && givens[1] > givens[2])
        assertEquals("Expert shows cages alone", 0.0, givens[2], 0.0)
        val cages = Difficulty.entries.map { d -> (0 until 60).sumOf { board(it, d).sums.size } / 60.0 }
        println("mean cages $cages")
        assertTrue("no more cages on Expert than on Standard: $cages", cages[2] < cages[0])
        val biggest = Difficulty.entries.map { d -> (0 until 60).sumOf { board(it, d).let { s -> CageLogic.cageCells(s.cageOf).maxOf { c -> c.size } } } / 60.0 }
        println("mean biggest cage $biggest")
        assertTrue("larger cages each tier: $biggest", biggest[0] < biggest[1] && biggest[1] < biggest[2])
    }

    @Test
    fun `generation is a pure function of the seed`() {
        val rng = Rng(5)
        for (d in Difficulty.entries) repeat(5) {
            val seed = rng.nextLong()
            assertEquals(Cages.generate(seed, d), Cages.generate(seed, d))
        }
    }

    @Test
    fun `a tier gives away no more than it should`() {
        for (d in Difficulty.entries) {
            val spec = CageLogic.specFor(d)
            for (i in 0 until 60) {
                val shown = board(i, d).grid.givens.count { it }
                assertTrue("${d.name} day $i shows $shown givens", shown <= maxOf(spec.maxGivens, spec.floorGivens))
                assertTrue("${d.name} day $i shows $shown givens, under its floor", shown >= spec.floorGivens)
            }
        }
    }
}
