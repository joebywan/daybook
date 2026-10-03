package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Inequality
import com.joebywan.daybook.puzzles.InequalityLogic
import com.joebywan.daybook.puzzles.InequalityState
import com.joebywan.daybook.puzzles.InequalityTeacher
import com.joebywan.daybook.puzzles.Sign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InequalityRulesTest {

    private val start = LocalDate.of(2026, 1, 1)
    private fun board(day: Int, d: Difficulty) =
        Inequality.generate(DailySeed.seedFor(start.plusDays(day.toLong()), Inequality.id, d), d) as InequalityState

    /** Independent of the code under test: every row and column a permutation, every sign true. */
    private fun legal(s: InequalityState, cells: List<Int>): Boolean {
        val n = s.size
        if (cells.any { it !in 1..n }) return false
        for (k in 0 until n) {
            if (List(n) { cells[k * n + it] }.toSet().size != n) return false
            if (List(n) { cells[it * n + k] }.toSet().size != n) return false
        }
        return s.signs.all { cells[it.lo] < cells[it.hi] }
    }

    @Test
    fun `the tiers get larger in the order they are offered`() {
        assertEquals(listOf(4, 5, 6), Difficulty.entries.map { board(0, it).size })
    }

    @Test
    fun `an independent solver finds exactly one answer, and it is the stored one`() {
        // Naive search is slow on 6x6, so the sample thins as the boards grow.
        for ((d, days) in listOf(Difficulty.STANDARD to 60, Difficulty.HARD to 30, Difficulty.EXPERT to 12)) {
            for (day in 0 until days) {
                val s = board(day, d)
                assertEquals("${d.name} day $day", 1L, InequalityOracle.count(s.size, s.cells, s.signs))
                assertTrue(legal(s, s.solution))
                assertTrue(s.cells.indices.all { !s.givens[it] || s.cells[it] == s.solution[it] })
            }
        }
    }

    @Test
    fun `the last resort has one answer on every size`() {
        for (d in Difficulty.entries) {
            val s = InequalityLogic.lastResort(d)
            assertFalse(s.solved)
            assertEquals(1L, InequalityOracle.count(s.size, s.cells, s.signs))
            assertTrue(legal(s, s.solution))
        }
    }

    @Test
    fun `every board is irredundant and gives away little`() {
        println("tier      givens/squares  signs/pairs   (mean over 100 daily boards)")
        for (d in Difficulty.entries) {
            var givens = 0.0
            var signs = 0.0
            var squares = 0
            var pairs = 0
            for (day in 0 until 100) {
                val s = board(day, d)
                val n = s.size
                squares = n * n
                pairs = InequalityLogic.neighbours(n).size
                givens += s.givens.count { it }
                signs += s.signs.size
                // Irredundant: take back any one given or sign and the logic no longer finishes it.
                for (i in 0 until n * n) if (s.givens[i]) {
                    val fewer = s.cells.toMutableList().also { it[i] = 0 }
                    assertFalse("${d.name} day $day given $i is redundant", InequalityTeacher.solvesByLogic(n, fewer, s.signs))
                }
                for (k in s.signs.indices) {
                    val fewer = s.signs.filterIndexed { j, _ -> j != k }
                    assertFalse("${d.name} day $day sign $k is redundant", InequalityTeacher.solvesByLogic(n, s.cells, fewer))
                }
            }
            println("%-9s %5.1f/%d        %5.1f/%d".format(d.name, givens / 100, squares, signs / 100, pairs))
            assertTrue("${d.name} gives away too many squares", givens / 100 <= squares * 0.15)
            assertTrue("${d.name} shows too many signs", signs / 100 <= pairs * 0.35)
        }
    }

    @Test
    fun `each rule refuses on its own`() {
        // A repeat in a row only, then in a column only (a 4x4 grid, 0 empty).
        assertEquals(setOf(0, 1), InequalityLogic.clashes(4, listOf(1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)))
        assertEquals(setOf(0, 8), InequalityLogic.clashes(4, listOf(2, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0)))
        assertTrue(InequalityLogic.clashes(4, listOf(1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)).isEmpty())
        // A false sign only: square 0 must be below square 1, and an empty end is not false.
        val signs = listOf(Sign(0, 1))
        assertEquals(setOf(0), InequalityLogic.brokenSigns(listOf(2, 1) + List(14) { 0 }, signs))
        assertEquals(setOf(0), InequalityLogic.brokenSigns(listOf(2, 2) + List(14) { 0 }, signs))
        assertTrue(InequalityLogic.brokenSigns(listOf(1, 2) + List(14) { 0 }, signs).isEmpty())
        assertTrue(InequalityLogic.brokenSigns(listOf(4, 0) + List(14) { 0 }, signs).isEmpty())
        // The board: a given cannot be changed, and entering the same digit changes nothing.
        val s = Inequality.tutorialBoard()
        assertEquals(s, s.withCell(2, 1))
        assertEquals(s.withCell(0, 1), s.withCell(0, 1).withCell(0, 1))
        assertEquals(1, s.withCell(0, 1).moves)
    }

    @Test
    fun `the win check reads the rules, and near misses lose`() {
        val s = board(3, Difficulty.EXPERT)
        assertTrue(s.copy(cells = s.solution).solved)
        // Swap two digits in a row: the grid is full and a column repeats.
        val swapped = s.solution.toMutableList().also { val t = it[0]; it[0] = it[1]; it[1] = t }
        assertFalse(s.copy(cells = swapped).solved)
        assertFalse(s.copy(cells = s.solution.toMutableList().also { it[s.size * s.size - 1] = 0 }).solved)
    }

    @Test
    fun `the walkthrough board has exactly one answer, and it is the stored one`() {
        val s = Inequality.tutorialBoard()
        assertEquals(1L, InequalityOracle.count(4, s.cells, s.signs))
        assertTrue(legal(s, s.solution))
        // The signs are what settle the corner: without the two that touch it, it has two answers.
        val without = s.signs.filter { it != Sign(0, 1) && it != Sign(5, 4) }
        assertEquals(2L, InequalityOracle.count(4, s.cells, without))
    }
}
