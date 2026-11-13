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
 * Logic Web, the graph Who's Who. Truth here is read straight off the stored answer (who holds each item), never
 * through the closure, so the closure is checked against something it does not share.
 */
class WhosWhoGraphTest {

    private fun board(day: Int, difficulty: Difficulty) =
        WhosWhoGraph.generate(1000L + day, difficulty) as WhosWhoGraphState

    private fun same(s: WhosWhoGraphState, a: Int, b: Int) = WhosWhoGraphLogic.holder(s.board, a) == WhosWhoGraphLogic.holder(s.board, b)

    private fun pairs(s: WhosWhoGraphState) = (0 until s.size).flatMap { a -> (a + 1 until s.size).filter { a / s.n != it / s.n }.map { a to it } }

    @Test
    fun `it graduated from the lab under its own id`() {
        assertSame(WhosWhoGraph, PuzzleRegistry.byId("logicweb"))
        assertTrue(WhosWhoGraph in PuzzleRegistry.all)
        assertFalse(PuzzleRegistry.isLab("logicweb"))
        // The lab build's id is gone: a saved route or game under it finds no puzzle and is left alone.
        assertNull(PuzzleRegistry.byId("test-whoswho"))
        assertFalse(WhosWhoGraph.id == WhosWho.id)
    }

    /** The item of [category] that goes with [a] in the answer. */
    private fun partner(s: WhosWhoGraphState, a: Int, category: Int) = (category * s.n until category * s.n + s.n).first { same(s, a, it) }

    @Test
    fun `two lines from a person into houses mean one of them, nothing more`() {
        for (difficulty in Difficulty.entries) for (day in 0 until 10) {
            val s0 = board(day, difficulty)
            val n = s0.n
            val fred = day % n
            val right = partner(s0, fred, 1)
            val wrong = (n until 2 * n).first { it != right }
            val s = s0.cycle(fred, right).cycle(fred, wrong)
            val c = WhosWhoGraphLogic.close(s)
            assertFalse("$difficulty day $day: clash", c.clash)
            for (h in n until 2 * n) {
                assertEquals("$difficulty day $day: house $h", if (h == right || h == wrong) 0 else -1, c.at(fred, h))
                assertTrue(h == right || h == wrong || !s.drew(fred, h))
            }
            assertFalse(s.drew(right, fred))
            // Nothing about Fred's other categories, or about the houses' own.
            for (x in 2 * n until s.size) assertEquals(0, c.at(fred, x))
            for (x in 2 * n until s.size) assertEquals(0, c.at(right, x))
            assertFalse(WhosWhoGraph.teach(s)?.mistake ?: false)
        }
    }

    @Test
    fun `true possibility sets never imply a false mark or clash`() {
        val rng = Random(11)
        for (difficulty in Difficulty.entries) for (day in 0 until 30) {
            var s = board(day, difficulty)
            val n = s.n
            repeat(rng.nextInt(1, 8)) {
                val a = rng.nextInt(s.size)
                val category = (0..s.board.columns).filter { it != a / n }.random(rng)
                val others = (category * n until category * n + n).filter { !same(s, a, it) }.shuffled(rng)
                for (z in listOf(partner(s, a, category)) + others.take(rng.nextInt(0, n))) s = s.withLine(a, z)
            }
            for ((a, b) in pairs(s).shuffled(rng).take(rng.nextInt(0, 5))) {
                if (!same(s, a, b) && s.mark(a, b) == WhosWhoGraphLogic.OPEN) s = s.withMark(a, b, WhosWhoGraphLogic.ANTI)
            }
            val c = WhosWhoGraphLogic.close(s)
            assertFalse("$difficulty day $day: true marks clash", c.clash)
            for ((a, b) in pairs(s)) {
                val v = c.at(a, b)
                if (v != 0) assertEquals("$difficulty day $day: $a-$b", same(s, a, b), v == 1)
            }
            assertFalse("$difficulty day $day", WhosWhoGraph.teach(s)?.mistake ?: false)
        }
    }

    @Test
    fun `lines that leave out the right one are the hint's mistake`() {
        val s0 = board(5, Difficulty.HARD)
        val n = s0.n
        val wrongs = (2 * n until 3 * n).filter { !same(s0, 1, it) }.take(2)
        val s = wrongs.fold(s0) { acc, z -> acc.withLine(1, z) }
        val step = WhosWhoGraph.teach(s)!!
        assertTrue(step.mistake)
        assertEquals(setOf(1) + wrongs, step.targets)
        val fixed = step.apply(s) as WhosWhoGraphState
        assertEquals(s.moves + 1, fixed.moves)
        assertEquals(s0.links, fixed.links)
        assertTrue(step.isReached(fixed))
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
                s = s.cycle(p, house)
                for (x in 2 * n until s.size) if (same(s, house, x)) s = s.cycle(house, x)
            }
            assertTrue("$difficulty day $day", s.solved)
            // A person's line to a pet is implied through the house, not drawn: the finish says it was elimination.
            assertTrue(WhosWhoGraphLogic.leftImplied(s))
            assertNotNull(WhosWhoGraph.finishNote(s))
        }
    }

    @Test
    fun `anti-links alone that force every link solve the board, with no line drawn`() {
        for (difficulty in Difficulty.entries) for (day in 0 until 10) {
            var s = board(day, difficulty)
            val n = s.n
            for ((a, b) in pairs(s)) if (a < n && !same(s, a, b)) s = s.withMark(a, b, WhosWhoGraphLogic.ANTI)
            assertFalse(s.links.any { WhosWhoGraphLogic.isLine(it) })
            val c = WhosWhoGraphLogic.close(s)
            for (p in 0 until n) for (x in n until s.size) assertEquals(if (same(s, p, x)) 1 else -1, c.at(p, x))
            assertTrue("$difficulty day $day: elimination alone is a win", s.solved)
            assertNull(WhosWhoGraph.teach(s))
            assertTrue(WhosWhoGraphLogic.leftImplied(s))
        }
    }

    @Test
    fun `the finish note praises an elimination, varies, and says nothing when every link was drawn`() {
        val n0 = board(2, Difficulty.STANDARD).n
        var implied = board(2, Difficulty.STANDARD)
        for ((a, b) in pairs(implied)) if (a < n0 && !same(implied, a, b)) implied = implied.withMark(a, b, WhosWhoGraphLogic.ANTI)
        assertTrue(implied.solved)
        // Every draw is one of the set, and a run of solves meets most of them.
        val seen = (0 until 200).map { WhosWhoGraph.finishNote(implied, Random(it))!! }.toSet()
        assertTrue(seen.all { it in WhosWhoGraph.ELIMINATION_NOTES })
        assertTrue(seen.size >= WhosWhoGraph.ELIMINATION_NOTES.size - 1)
        // The same draw gives the same note: the frame keeps the one it was given.
        assertEquals(WhosWhoGraph.finishNote(implied, Random(5)), WhosWhoGraph.finishNote(implied, Random(5)))
        assertTrue(WhosWhoGraph.ELIMINATION_NOTES.all { it.isNotBlank() && it.length <= 100 })
        assertEquals(WhosWhoGraph.ELIMINATION_NOTES.size, WhosWhoGraph.ELIMINATION_NOTES.toSet().size)

        // Every person's line to each of their items drawn, from either end: no note.
        var drawn = implied
        for (p in 0 until n0) for (x in n0 until drawn.size) if (same(drawn, p, x)) drawn = if (x % 2 == 0) drawn.withLine(p, x) else drawn.withLine(x, p)
        assertTrue(drawn.solved)
        assertFalse(WhosWhoGraphLogic.leftImplied(drawn))
        assertNull(WhosWhoGraph.finishNote(drawn, Random(1)))
        // Nor on a board not yet solved.
        assertNull(WhosWhoGraph.finishNote(board(2, Difficulty.STANDARD), Random(1)))
    }

    @Test
    fun `a wrong mark is never solved and is the hint's mistake`() {
        val s = board(3, Difficulty.HARD)
        val (a, b) = pairs(s).first { !same(s, it.first, it.second) }
        val wrong = s.withMark(a, b, WhosWhoGraphLogic.LINK)
        assertFalse(wrong.solved)
        val step = WhosWhoGraph.teach(wrong)!!
        assertTrue(step.mistake)
        assertEquals(setOf(a, b), step.targets)
        // Each end's line is its own mistake: the hints take both back.
        val once = step.apply(wrong) as WhosWhoGraphState
        val twice = WhosWhoGraph.teach(once)!!.also { assertTrue(it.mistake) }.apply(once) as WhosWhoGraphState
        assertEquals(WhosWhoGraphLogic.OPEN, twice.mark(a, b))
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
    fun `a tap draws the chosen item's line, then rules the pair out, then clears it`() {
        val s = board(0, Difficulty.STANDARD)
        val h = s.n
        val one = s.cycle(0, h)
        assertEquals(WhosWhoGraphLogic.FROM_LOW, one.mark(h, 0))
        assertTrue(one.drew(0, h))
        assertFalse(one.drew(h, 0))
        val two = one.cycle(0, h)
        assertEquals(WhosWhoGraphLogic.ANTI, two.mark(0, h))
        val three = two.cycle(0, h)
        assertEquals(s.links, three.links)
        assertEquals(3, three.moves)
        // From the other end: its line is added beside the first, then both go to an anti-link.
        val back = s.cycle(h, 0)
        assertEquals(WhosWhoGraphLogic.FROM_HIGH, back.mark(0, h))
        val both = one.cycle(h, 0)
        assertEquals(WhosWhoGraphLogic.LINK, both.mark(0, h))
        assertEquals(WhosWhoGraphLogic.ANTI, both.cycle(h, 0).mark(0, h))
        // An old save's link is drawn from both ends: a tap from either rules it out.
        val old = s.withMark(0, h, WhosWhoGraphLogic.LINK)
        assertTrue(old.drew(0, h) && old.drew(h, 0))
        assertEquals(1, WhosWhoGraphLogic.close(old).at(0, h))
        assertEquals(WhosWhoGraphLogic.ANTI, old.cycle(h, 0).mark(0, h))
    }
}
