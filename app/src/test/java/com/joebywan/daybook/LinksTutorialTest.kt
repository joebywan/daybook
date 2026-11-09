package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Links
import com.joebywan.daybook.puzzles.LinksLogic
import com.joebywan.daybook.puzzles.LinksState
import com.joebywan.daybook.puzzles.LinksTeacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Links' walkthrough, and the glue that turns a teacher's step into a hint the screen can play. */
class LinksTutorialTest {

    @Test
    fun `the walkthrough board has exactly one answer, finished by the local rules, and it is the stored one`() {
        val s = Links.tutorial.first().state as LinksState
        assertEquals(1L, LinksOracle.count(5, s.ends))
        assertTrue(LinksOracle.obeys(5, s.ends, LinksLogic.masksOf(s.answer)))
        assertEquals(LinksLogic.LOCAL, LinksLogic.depth(LinksLogic.Board.of(5, s.ends)))
    }

    @Test
    fun `each walkthrough frame accepts its move, made by the real gestures, and rejects a wrong one`() {
        val frames = Links.tutorial
        assertEquals(7, frames.size)
        fun board(i: Int) = frames[i].state as LinksState
        assertNull(frames[0].accepts)
        assertTrue(frames[6].freePlay)
        assertTrue(board(0).solved)

        // 2: one drag from the 1, through the empty corner, up one.
        val corner = frames[1].accepts!!
        val afterCorner = board(1).drag(listOf(21, 20, 15))
        assertTrue(corner(afterCorner))
        assertFalse("half of it", corner(board(1).drag(listOf(21, 20))))
        assertFalse("too far", corner(board(1).drag(listOf(21, 20, 15, 10))))
        assertEquals(board(2).links, afterCorner.links)

        // 3: on from the line's end, up to the other 1.
        val on = frames[2].accepts!!
        val afterOn = board(2).drag(listOf(15, 10, 5, 0))
        assertTrue(on(afterOn))
        assertFalse("short of the 1", on(board(2).drag(listOf(15, 10, 5))))
        assertEquals(board(3).links, afterOn.links)
        assertTrue("path 1 is joined", LinksLogic.View(LinksLogic.Board.of(5, afterOn.ends), LinksLogic.masksOf(afterOn.links)).complete(0))

        // 4: a line from an empty square, through the corner, onto the 5.
        val loose = frames[3].accepts!!
        val afterLoose = board(3).drag(listOf(19, 24, 23))
        assertTrue(loose(afterLoose))
        assertFalse(loose(board(3).drag(listOf(24, 23))))
        assertEquals(board(4).links, afterLoose.links)
        assertEquals("it takes the 5's colour", 4, LinksLogic.View(LinksLogic.Board.of(5, afterLoose.ends), LinksLogic.masksOf(afterLoose.links)).colour[19])

        // 5: the slip, a mistake that cuts the 3 off.
        val slip = frames[4].accepts!!
        val afterSlip = board(4).drag(listOf(7, 8))
        assertTrue(slip(afterSlip))
        assertFalse(slip(board(4).drag(listOf(7, 12))))
        assertEquals(board(5).links, afterSlip.links)
        assertEquals(LinksTeacher.MISTAKE, LinksTeacher.teach(afterSlip)!!.technique)
        val v = LinksLogic.View(LinksLogic.Board.of(5, afterSlip.ends), LinksLogic.masksOf(afterSlip.links))
        assertEquals("the 3 can't reach its other end", 2, LinksLogic.reach(v).cutOff)

        // 6: drag back along it to rub it out.
        val rub = frames[5].accepts!!
        val afterRub = board(5).drag(listOf(8, 7))
        assertTrue(rub(afterRub))
        assertFalse(rub(board(5)))
        assertEquals(board(6).links, afterRub.links)

        frames.forEachIndexed { i, f ->
            if (i != 5) assertNull(LinksTeacher.teach(f.state as LinksState)?.takeIf { it.technique == LinksTeacher.MISTAKE })
            assertTrue("caption too long (${f.caption.length}): ${f.caption}", f.caption.length <= 170)
        }
    }

    @Test
    fun `free play is finishable by hints alone, with no mistake on the way`() {
        var s = Links.tutorial.last().state as LinksState
        var guard = 0
        while (!s.solved) {
            assertTrue(guard++ < 60)
            val d = Links.teach(s)
            assertNotNull(d)
            assertFalse(d!!.mistake)
            assertFalse(d.fallback)
            s = d.apply(s) as LinksState
        }
    }

    @Test
    fun `a hint's move reaches its own goal, and one move is one undo entry`() {
        for (d in Difficulty.entries) for (seed in 1L..4L) {
            var s = Links.generate(seed * 31, d) as LinksState
            while (!s.solved) {
                val deduction = Links.teach(s)!!
                assertFalse("not reached before it is made", deduction.isReached(s))
                val next = deduction.apply(s) as LinksState
                assertTrue(deduction.isReached(next))
                assertEquals(s.moves + 1, next.moves)
                s = next
            }
        }
    }

    @Test
    fun `a mistake's hint is reached by rubbing the line out`() {
        val base = Links.tutorialBoard()
        val wrong = base.drag(listOf(7, 8))
        val d = Links.teach(wrong)!!
        assertTrue(d.mistake)
        assertFalse(d.isReached(wrong))
        assertTrue(d.isReached(wrong.drag(listOf(8, 7))))
        assertEquals(base.links, (d.apply(wrong) as LinksState).links)
    }
}
