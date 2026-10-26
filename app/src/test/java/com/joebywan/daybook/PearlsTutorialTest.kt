package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Pearls
import com.joebywan.daybook.puzzles.PearlsGrid
import com.joebywan.daybook.puzzles.PearlsLogic
import com.joebywan.daybook.puzzles.PearlsState
import com.joebywan.daybook.puzzles.PearlsTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Pearls' walkthrough, and the glue that turns a teacher's step into a hint the screen can play. */
class PearlsTutorialTest {

    private val g = PearlsGrid.of(5)

    @Test
    fun `the walkthrough board has exactly one loop, finished by the local rules, and it is the stored one`() {
        val s = Pearls.tutorial.first().state as PearlsState
        val answers = PearlsOracle.answers(5, s.pearls, limit = 5)
        assertEquals(1, answers.size)
        val on = CharArray(g.edgeCount) { '0' }
        val a = answers.single()
        for (i in a.indices) on[g.between(a[i], a[(i + 1) % a.size])] = '1'
        assertEquals(s.loop, on.concatToString())
        assertEquals(PearlsLogic.LOCAL, PearlsLogic.depth(s))
    }

    @Test
    fun `each walkthrough frame accepts its move, made by the real gestures, and rejects a wrong one`() {
        val frames = Pearls.tutorial
        assertEquals(7, frames.size)
        fun board(i: Int) = frames[i].state as PearlsState
        assertNull(frames[0].accepts)
        assertTrue(frames[6].freePlay)
        assertTrue(board(0).solved)

        // 2: one drag through the corner black pearl.
        val black = frames[1].accepts!!
        val afterBlack = board(1).drag(listOf(10, 5, 0, 1, 2))
        assertTrue(black(afterBlack))
        assertFalse("one arm only", black(board(1).drag(listOf(0, 1, 2))))
        assertFalse("too far", black(board(1).drag(listOf(10, 5, 0, 1, 2, 3))))
        assertEquals(board(2).edges, afterBlack.edges)

        // 3: one drag across the bottom white pearl.
        val white = frames[2].accepts!!
        val afterWhite = board(2).drag(listOf(20, 21, 22))
        assertTrue(white(afterWhite))
        assertFalse("half of it", white(board(2).drag(listOf(20, 21))))
        assertEquals(board(3).edges, afterWhite.edges)

        // 4: a tap on the edge below the square after the black pearl.
        val cross = frames[3].accepts!!
        val e = g.between(1, 6)
        val tapAt = Pearls.edgeNear(5, 1.5f, 1.0f)
        assertEquals(e, tapAt)
        val afterCross = board(3).tap(tapAt)
        assertTrue(cross(afterCross))
        assertFalse("another edge", cross(board(3).tap(g.between(5, 6))))
        assertEquals(board(4).edges, afterCross.edges)

        // 5: the slip, which turns the white pearl red and is a mistake.
        val slip = frames[4].accepts!!
        val afterSlip = board(4).drag(listOf(15, 16))
        assertTrue(slip(afterSlip))
        assertFalse(slip(board(4).drag(listOf(15, 10))))
        assertEquals(board(5).edges, afterSlip.edges)
        assertTrue("the white pearl turns red", 15 in Pearls.breaks(afterSlip).squares)
        assertEquals(PearlsTeacher.MISTAKE, PearlsTeacher.teach(afterSlip)!!.technique)

        // 6: drag back along it to rub it out.
        val rub = frames[5].accepts!!
        val afterRub = board(5).drag(listOf(16, 15))
        assertTrue(rub(afterRub))
        assertFalse("a tap crosses nothing it should", rub(board(5)))
        assertEquals(board(6).edges, afterRub.edges)
        assertTrue(Pearls.breaks(afterRub).isEmpty)

        frames.forEachIndexed { i, f ->
            if (i != 5) assertNull(PearlsTeacher.teach(f.state as PearlsState)?.takeIf { it.technique == PearlsTeacher.MISTAKE })
            assertTrue("caption too long (${f.caption.length}): ${f.caption}", f.caption.length <= 170)
        }
    }

    @Test
    fun `free play is finishable by hints alone, with no mistake on the way`() {
        var s = Pearls.tutorial.last().state as PearlsState
        var guard = 0
        while (!s.solved) {
            assertTrue(guard++ < 60)
            val d = Pearls.teach(s)
            assertNotNull(d)
            assertFalse(d!!.mistake)
            assertFalse(d.fallback)
            s = d.apply(s) as PearlsState
        }
    }

    // ---- the hint glue --------------------------------------------------------------------------

    private val glue = LocalDate.of(2026, 3, 1)

    @Test
    fun `a hint's move reaches its own goal, and one move is one undo entry`() {
        for (d in Difficulty.entries) for (seed in seeds(4, d, "pearls-glue", glue)) {
            var s = Pearls.generate(seed, d) as PearlsState
            while (!s.solved) {
                val deduction = Pearls.teach(s)!!
                assertFalse("not reached before it is made", deduction.isReached(s))
                val next = deduction.apply(s) as PearlsState
                assertTrue(deduction.isReached(next))
                assertEquals("one move per edge changed", deduction.targets.size + s.moves, next.moves)
                s = next
            }
        }
    }

    @Test
    fun `a mistake's hint is reached by rubbing the line out`() {
        val base = Pearls.generate(daySeed(0, "pearls-glue", Difficulty.STANDARD, glue), Difficulty.STANDARD) as PearlsState
        val e = base.loop.indexOf('0')
        val wrong = base.copy(edges = base.edges.toCharArray().also { it[e] = '-' }.concatToString())
        val d = Pearls.teach(wrong)!!
        assertTrue(d.mistake)
        assertFalse(d.isReached(wrong))
        assertTrue(d.isReached(wrong.tap(e)))
        assertEquals('.', (d.apply(wrong) as PearlsState).edges[e])
    }
}
