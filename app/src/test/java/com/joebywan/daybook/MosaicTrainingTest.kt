package com.joebywan.daybook

import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.MosaicTraining
import com.joebywan.daybook.core.Training
import com.joebywan.daybook.core.praisedTechniquesOf
import com.joebywan.daybook.puzzles.Mosaic
import com.joebywan.daybook.puzzles.MosaicState
import com.joebywan.daybook.puzzles.MosaicTeacher
import com.joebywan.daybook.ui.Route
import com.joebywan.daybook.ui.decodeRoute
import com.joebywan.daybook.ui.encodeRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mosaic's course (docs/TRAINING_SPEC.md). Mosaic has no stored answer: what a practice must keep is the limit, so each
 * board is checked by a plain memoised depth-first search over every fill written here (it moves with
 * [MosaicState.flood] and nothing of the teacher's), and each rung below the practice's own by counting colours, fills,
 * swallowed neighbours and steps between areas.
 */
class MosaicTrainingTest {

    private fun reps(b: MosaicState) = b.cells.indices.filter { b.area(it).min() == it }

    private fun everyFill(b: MosaicState) = reps(b).flatMap { r -> (0 until b.colours).filter { it != b.cells[r] }.map { r to it } }

    private fun left(b: MosaicState) = b.limit - b.moves

    private fun winsWithin(b: MosaicState, fills: Int): Boolean {
        val memo = HashMap<List<Int>, Int>()
        fun search(s: MosaicState, r: Int): Boolean {
            if (s.solved) return true
            if (r <= 0 || s.cells.distinct().size - 1 > r || (memo[s.cells] ?: -1) >= r) return false
            for ((c, k) in everyFill(s)) if (search(s.flood(c, k), r - 1)) return true
            memo[s.cells] = r
            return false
        }
        return search(b, fills)
    }

    private fun keeps(b: MosaicState, cell: Int, colour: Int) = winsWithin(b.flood(cell, colour), left(b) - 1)

    /** Areas of [colour] beside [cell]'s area. */
    private fun gain(b: MosaicState, cell: Int, colour: Int): Int {
        val mine = b.area(cell).toSet()
        return mine.flatMap { x -> neighbours(b, x) }.filter { it !in mine && b.cells[it] == colour }.map { b.area(it).min() }.toSet().size
    }

    private fun neighbours(b: MosaicState, x: Int) = listOfNotNull(
        (x - b.width).takeIf { x >= b.width }, (x + b.width).takeIf { x + b.width < b.cells.size },
        (x - 1).takeIf { x % b.width > 0 }, (x + 1).takeIf { x % b.width < b.width - 1 },
    )

    /** Each area's (by its least cell) greatest distance to another, by breadth-first over cells weighted 0 within an area. */
    private fun eccentricity(b: MosaicState): Map<Int, Int> = reps(b).associateWith { from ->
        val repOf = IntArray(b.cells.size) { b.area(it).min() }
        val d = mutableMapOf(from to 0)
        var frontier = setOf(from)
        while (frontier.isNotEmpty()) {
            val next = frontier.flatMap { a -> b.area(a).flatMap { neighbours(b, it) }.map { repOf[it] } }.filter { it !in d }.toSet()
            for (a in next) d[a] = d.getValue(frontier.first()) + 1
            frontier = next
        }
        d.values.max()
    }

    private fun maxGain(b: MosaicState) = everyFill(b).maxOf { gain(b, it.first, it.second) }

    // The rungs, by counting. Finish: one fill makes one colour. Count: one fill fewer than colours (three or more).
    // Biggest: a fill swallowing two or more, as many as any, that keeps the limit.
    private fun finish(b: MosaicState) = everyFill(b).any { b.flood(it.first, it.second).solved }
    private fun count(b: MosaicState) = b.cells.distinct().size.let { k -> k >= 3 && left(b) == k - 1 }
    private fun biggest(b: MosaicState): Boolean {
        val mx = maxGain(b)
        return mx >= 2 && everyFill(b).any { gain(b, it.first, it.second) == mx && keeps(b, it.first, it.second) }
    }

    @Test fun `every practice is a position still on an optimal line, whose next step is its technique and no simpler`() {
        assertEquals(praisedTechniquesOf("mosaic").toList(), MosaicTraining.techniques)
        for ((rung, t) in MosaicTraining.techniques.withIndex()) {
            val list = MosaicTraining.practices(t).take(6).toList()
            assertEquals("practices for $t", 6, list.size)
            assertEquals("distinct boards for $t", 6, list.map { it.board }.distinct().size)
            for (p in list) {
                val b = p.board as MosaicState
                val d = p.step as Deduction
                assertEquals(t, d.technique)
                assertEquals("the hint agrees", t, Mosaic.teach(b)!!.technique)
                assertTrue("the board still finishes in the fills left", winsWithin(b, left(b)))
                assertFalse("with nothing to spare", winsWithin(b, left(b) - 1))
                val after = d.apply(b) as MosaicState
                assertTrue("the step keeps the limit", winsWithin(after, left(after)))
                assertFalse("finish is on a $t board", finish(b))
                if (rung > 0) assertFalse("counting is on a $t board", count(b))
                if (rung > 1) assertFalse("a safe biggest swallow is on a $t board", biggest(b))
                val filled = b.cells.indices.first { b.cells[it] != after.cells[it] }
                when (t) {
                    MosaicTeacher.COUNT -> assertTrue(count(b))
                    MosaicTeacher.BIGGEST -> assertTrue(maxGain(b) >= 2 && gain(b, filled, after.cells[filled]) == maxGain(b))
                    else -> eccentricity(b).let { e -> assertEquals(e.values.min(), e.getValue(b.area(filled).min())) }
                }
            }
        }
    }

    @Test fun `a fill is right when it is any instance of the technique, wrong when it loses, and otherwise plays on`() {
        for ((rung, t) in MosaicTraining.techniques.withIndex()) {
            for (p in MosaicTraining.practices(t).take(4)) {
                val b = p.board as MosaicState
                val step = p.step as Deduction
                assertEquals(true, MosaicTraining.judge(p, step.apply(b)))
                assertNull("no fill yet", MosaicTraining.judge(p, b))
                val mx = maxGain(b)
                val ecc = eccentricity(b)
                for ((cell, colour) in everyFill(b)) {
                    val after = b.flood(cell, colour)
                    if (step.isReached(after)) continue
                    val technique = when (rung) {
                        0 -> true // with one fill fewer than colours, a fill that keeps the limit wipes one out
                        1 -> gain(b, cell, colour) == mx
                        else -> ecc.getValue(b.area(cell).min()) == ecc.values.min()
                    }
                    val expected = when {
                        !keeps(b, cell, colour) -> false // it loses the board
                        technique -> true // another instance of the technique
                        else -> null // still wins, but not this technique: play on, never "Not quite"
                    }
                    assertEquals("$t: ${b.cells} fill $cell with $colour", expected, MosaicTraining.judge(p, after))
                    // After a fill that is not the step, nothing later is judged: the step is gone.
                    if (expected == null) {
                        val (c2, k2) = everyFill(after).first()
                        assertNull(MosaicTraining.judge(p, after.flood(c2, k2)))
                    }
                }
            }
        }
    }

    @Test fun `the final board is a proved Expert board, the first whose walk needs every technique`() {
        val index = (0 until 60).first { k ->
            MosaicTraining.techniquesUsed(Mosaic.generate(Training.seed(k), Difficulty.EXPERT)).containsAll(MosaicTraining.techniques)
        }
        assertEquals(Training.seed(index), MosaicTraining.examSeed)
        val exam = MosaicTraining.exam() as MosaicState
        assertEquals("the limit is the proved optimum", exam.limit, Mosaic.solve(exam)!!.size)
        assertTrue(MosaicTraining.techniquesUsed(exam).containsAll(MosaicTraining.techniques))
    }

    @Test fun `mosaic routes round-trip`() {
        listOf(Route.Training(puzzle = "mosaic"), Route.Training("centre", puzzle = "mosaic"), Route.Training("exam", puzzle = "mosaic"))
            .forEach { assertEquals(it, decodeRoute(encodeRoute(it))) }
    }
}
