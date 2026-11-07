package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The graph Who's Who (a lab puzzle). Truth here is read straight off the stored answer (who holds each item), never
 * through the closure, so the closure is checked against something it does not share.
 */
class WhosWhoGraphTest {

    private fun board(day: Int, difficulty: Difficulty) =
        WhosWhoGraph.generate(1000L + day, difficulty) as WhosWhoGraphState

    private fun same(s: WhosWhoGraphState, a: Int, b: Int) = WhosWhoGraphLogic.holder(s.board, a) == WhosWhoGraphLogic.holder(s.board, b)

    private fun pairs(s: WhosWhoGraphState) = (0 until s.size).flatMap { a -> (a + 1 until s.size).filter { a / s.n != it / s.n }.map { a to it } }

    @Test
    fun `it is a lab puzzle, found by id and kept out of the registry`() {
        assertSame(WhosWhoGraph, PuzzleRegistry.byId("test-whoswho"))
        assertTrue(PuzzleRegistry.isLab("test-whoswho"))
        assertFalse(PuzzleRegistry.all.any { it.id == "test-whoswho" })
        assertFalse(PuzzleRegistry.isLab(WhosWho.id))
    }

    @Test
    fun `true marks never imply a false one`() {
        val rng = Random(7)
        for (difficulty in Difficulty.entries) for (day in 0 until 30) {
            var s = board(day, difficulty)
            for ((a, b) in pairs(s).shuffled(rng).take(rng.nextInt(1, 12))) {
                s = s.withMark(a, b, if (same(s, a, b)) WhosWhoGraphLogic.LINK else WhosWhoGraphLogic.ANTI)
            }
            val c = WhosWhoGraphLogic.close(s)
            assertFalse("$difficulty day $day: true marks clash", c.clash)
            for ((a, b) in pairs(s)) {
                val v = c.at(a, b)
                if (v != 0) assertEquals("$difficulty day $day: $a-$b", same(s, a, b), v == 1)
            }
        }
    }

    @Test
    fun `linking each person to one item and that item to the rest solves the board`() {
        for (difficulty in Difficulty.entries) for (day in 0 until 10) {
            var s = board(day, difficulty)
            assertFalse(s.solved)
            val n = s.n
            for (p in 0 until n) {
                val house = (n until 2 * n).first { same(s, p, it) }
                s = s.withMark(p, house, WhosWhoGraphLogic.LINK)
                for (x in 2 * n until s.size) if (same(s, house, x)) s = s.withMark(house, x, WhosWhoGraphLogic.LINK)
            }
            assertTrue("$difficulty day $day", s.solved)
        }
    }

    @Test
    fun `a wrong mark is never solved and is the hint's mistake`() {
        val s = board(3, Difficulty.HARD)
        val (a, b) = pairs(s).first { !same(s, it.first, it.second) }
        val wrong = s.withMark(a, b, WhosWhoGraphLogic.LINK)
        val step = WhosWhoGraph.teach(wrong)!!
        assertTrue(step.mistake)
        assertEquals(setOf(a, b), step.targets)
        assertEquals(WhosWhoGraphLogic.OPEN, (step.apply(wrong) as WhosWhoGraphState).mark(a, b))
    }

    @Test
    fun `hints alone solve every tier`() {
        for (difficulty in Difficulty.entries) for (day in 0 until 20) {
            var s = board(day, difficulty)
            var steps = 0
            while (!s.solved) {
                val step = WhosWhoGraph.teach(s)
                assertNotNull("$difficulty day $day: hints ran out", step)
                step!!
                assertFalse("$difficulty day $day: a mistake on a hints-only walk", step.mistake)
                assertFalse("$difficulty day $day: ${step.technique} already reached", step.isReached(s))
                s = step.apply(s) as WhosWhoGraphState
                assertTrue("$difficulty day $day: ${step.technique} not reached by its own move", step.isReached(s))
                assertTrue("$difficulty day $day: walk too long", ++steps < 200)
            }
            assertNull(WhosWhoGraph.teach(s))
        }
    }

    @Test
    fun `a tap cycles a pair through linked, anti-linked and open`() {
        val s = board(0, Difficulty.STANDARD)
        val one = s.cycle(0, s.n)
        assertEquals(WhosWhoGraphLogic.LINK, one.mark(s.n, 0))
        val two = one.cycle(s.n, 0)
        assertEquals(WhosWhoGraphLogic.ANTI, two.mark(0, s.n))
        val three = two.cycle(0, s.n)
        assertEquals(s.links, three.links)
        assertEquals(3, three.moves)
    }
}
