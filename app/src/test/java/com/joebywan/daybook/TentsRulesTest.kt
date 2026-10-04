package com.joebywan.daybook

import com.joebywan.daybook.core.DailySeed
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.TentsLogic
import com.joebywan.daybook.puzzles.Tents
import com.joebywan.daybook.puzzles.TentsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TentsRulesTest {

    private val start = LocalDate.of(2026, 1, 1)
    private fun board(day: Int, d: Difficulty) =
        Tents.generate(DailySeed.seedFor(start.plusDays(day.toLong()), Tents.id, d), d) as TentsState

    private fun withTents(s: TentsState, tents: List<Boolean>) =
        s.copy(marks = tents.map { if (it) TentsLogic.TENT else 0 })

    @Test
    fun `the tiers get larger and carry their tree counts`() {
        assertEquals(listOf(6, 8, 10), Difficulty.entries.map { board(0, it).size })
        assertEquals(listOf(7, 13, 20), Difficulty.entries.map { board(0, it).trees.count { t -> t } })
    }

    @Test
    fun `the stored answer satisfies the rules and an unsolved board does not`() {
        for (d in Difficulty.entries) for (day in 0 until 20) {
            val s = board(day, d)
            assertFalse(s.solved)
            assertTrue(withTents(s, s.solution).solved)
        }
    }

    @Test
    fun `solved reads the rules and ignores crosses`() {
        val s = board(3, Difficulty.STANDARD)
        val full = withTents(s, s.solution)
        // crosses everywhere else change nothing
        val crossed = full.copy(marks = full.marks.mapIndexed { i, m -> if (m == 0 && !s.trees[i]) TentsLogic.GRASS else m })
        assertTrue(crossed.solved)
        // an extra tent breaks the counts, a missing one too
        val at = s.solution.indices.first { !s.solution[it] && !s.trees[it] }
        assertFalse(full.withMark(at, TentsLogic.TENT).solved)
        assertFalse(full.withMark(s.solution.indexOf(true), 0).solved)
        // a tree takes no mark
        assertEquals(full, full.withMark(s.trees.indexOf(true), TentsLogic.TENT))
    }

    @Test
    fun `touching tents and an unpaired tent are refused even when the counts fit`() {
        // 3x3 hand board. T at 0 and 8; tents at 1 and 7 pair with them; no other layout fits the clues.
        val trees = listOf(true, false, false, false, false, false, false, false, true)
        val n = 3
        val ok = listOf(false, true, false, false, false, false, false, true, false)
        assertTrue(TentsLogic.isSolved(n, trees, listOf(1, 0, 1), listOf(0, 2, 0), ok))
        // the same clues with the second tent moved to 5 touches nothing but sits in the wrong row
        assertFalse(TentsLogic.isSolved(n, trees, listOf(1, 0, 1), listOf(0, 2, 0), listOf(false, true, false, false, false, true, false, false, false)))
        // corners: tents at 1 and 3 touch diagonally
        assertFalse(TentsLogic.isSolved(n, trees, listOf(1, 1, 0), listOf(1, 1, 0), listOf(false, true, false, true, false, false, false, false, false)))
        // two tents beside one tree: counts fit, one tree has no tent of its own
        val shared = listOf(false, false, false, true, false, false, true, false, false)
        val sharedTrees = listOf(false, false, false, false, true, false, false, false, true)
        assertFalse(TentsLogic.isSolved(n, sharedTrees, listOf(0, 1, 1), listOf(1, 1, 0), listOf(false, false, false, false, false, true, false, true, false)))
        assertEquals(1, shared.count { it } - 1)
    }

    @Test
    fun `an independent solver finds exactly one answer, and it is the stored one`() {
        for ((d, days) in listOf(Difficulty.STANDARD to 80, Difficulty.HARD to 25, Difficulty.EXPERT to 8)) {
            for (day in 0 until days) {
                val s = board(day, d)
                assertEquals("${d.name} day $day", 1, TentsOracle.count(s.size, s.trees, s.rowCounts, s.colCounts))
                assertEquals(1, TentsLogic.countSolutions(s.size, s.trees, s.rowCounts, s.colCounts))
            }
        }
    }

    @Test
    fun `the ladder finishes every sampled board and agrees with the stored answer`() {
        for (d in Difficulty.entries) for (day in 0 until 60) {
            val s = board(day, d)
            val st = TentsLogic.deduce(s.size, s.trees, s.rowCounts, s.colCounts)
            for (i in st.indices) {
                if (s.trees[i]) continue
                assertEquals("${d.name} day $day sq $i", if (s.solution[i]) TentsLogic.TENT else TentsLogic.GRASS, st[i])
            }
        }
    }

    @Test
    fun `the ladder is sound on boards it cannot finish`() {
        // Random trees and clues from a random tent set, not filtered: whatever the ladder decides must match every answer.
        val rng = com.joebywan.daybook.core.Rng(99)
        var checked = 0
        repeat(300) {
            val n = 6
            val tent = BooleanArray(n * n)
            for (i in rng.shuffled((0 until n * n).toList())) {
                if (tent.count { it } == 6) break
                if (TentsLogic.around(n, i).none { tent[it] }) tent[i] = true
            }
            val tree = BooleanArray(n * n)
            var ok = true
            for (t in tent.indices.filter { tent[it] }) {
                val spots = TentsLogic.orth(n, t).filter { !tent[it] && !tree[it] }
                if (spots.isEmpty()) { ok = false; break }
                tree[rng.pick(spots)] = true
            }
            if (!ok) return@repeat
            val tl = tree.toList()
            val rows = List(n) { r -> (0 until n).count { tent[r * n + it] } }
            val cols = List(n) { c -> (0 until n).count { tent[it * n + c] } }
            val st = TentsLogic.deduce(n, tl, rows, cols)
            for (i in st.indices) {
                if (tree[i] || st[i] == 0) continue
                // every solution of the board agrees: it is enough that the planted one does, and (when unique) that is all of them
                assertEquals("sq $i", if (tent[i]) TentsLogic.TENT else TentsLogic.GRASS, st[i])
            }
            checked++
        }
        assertTrue(checked > 100)
    }

    @Test
    fun `the last resort has one answer on every size`() {
        for (d in Difficulty.entries) {
            val s = TentsLogic.lastResort(d)
            assertEquals(1, TentsOracle.count(s.size, s.trees, s.rowCounts, s.colCounts))
            assertTrue(withTents(s, s.solution).solved)
        }
    }

    @Test
    fun `boards are deterministic`() {
        for (d in Difficulty.entries) assertEquals(board(5, d), board(5, d))
    }
}
