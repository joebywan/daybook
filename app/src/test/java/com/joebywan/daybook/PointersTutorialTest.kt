package com.joebywan.daybook

import com.joebywan.daybook.puzzles.Pointers
import com.joebywan.daybook.puzzles.PointersState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pointers' walkthrough. There is no teacher ([Pointers.offersHints] says why), so every claim a caption makes about the
 * board is pinned here, and each frame is played by every tap the board can make: one square of each arrow left.
 */
class PointersTutorialTest {

    private val frames = Pointers.tutorial
    private val arrows = Pointers.TUTORIAL_ARROWS

    private fun tapsFrom(s: PointersState): List<Pair<Int, PointersState>> =
        s.arrows.indices.filter { it !in s.removed }.map { it to s.tap(s.arrows[it][0])!! }

    @Test
    fun `the board is covered, and each arrow is free or blocked as the captions say`() {
        val s = Pointers.tutorialBoard()
        assertEquals(16, arrows.sumOf { it.size })
        assertEquals(-1, s.blocker(Pointers.TOP))
        assertEquals(Pointers.SIDE, s.blocker(Pointers.ROW))
        assertEquals(-1, s.blocker(Pointers.SIDE))
        assertEquals(Pointers.SIDE, s.blocker(Pointers.HOOK))
        assertEquals(Pointers.HOOK, s.blocker(Pointers.SHORT))
        // Top row pointing left, second row pointing right, the right-hand column pointing down.
        assertEquals(listOf(0, 1, 2), arrows[Pointers.TOP].sorted())
        assertTrue(arrows[Pointers.TOP].last() == 0)
        assertEquals(listOf(4, 5, 6), arrows[Pointers.ROW])
        assertEquals(listOf(3, 7, 11, 15), arrows[Pointers.SIDE])
    }

    @Test
    fun `each frame accepts the one tap it asks for and refuses every other`() {
        assertEquals(6, frames.size)
        assertNull(frames[0].accepts)
        for (k in 1..4) {
            val frame = frames[k]
            assertNotNull(frame.accepts)
            val accepts = frame.accepts!!
            val asked = frame.highlight.strong.single()
            for ((i, next) in tapsFrom(frame.state as PointersState)) {
                assertEquals("frame $k, tapping arrow $i", i == asked, accepts(next))
            }
        }
        // Each frame's board is the previous frame's result.
        for (k in 2..5) {
            val prev = frames[k - 1]
            val asked = prev.highlight.strong.single()
            val made = (prev.state as PointersState).tap(arrows[asked][0])
            assertEquals("frame $k", made, frames[k].state)
        }
        assertTrue(frames.last().freePlay)
    }

    @Test
    fun `the second-row tap is the bump, and it costs exactly one life`() {
        val s = frames[2].state as PointersState
        val bumped = s.tap(arrows[Pointers.ROW][0])!!
        assertEquals(s.lives - 1, bumped.lives)
        assertEquals(s.removed, bumped.removed)
    }

    @Test
    fun `the last frame is finished by taking the blocker first, and the other order bumps`() {
        val s = frames.last().state as PointersState
        assertEquals(setOf(Pointers.HOOK, Pointers.SHORT), s.arrows.indices.filter { it !in s.removed }.toSet())
        val won = s.tap(arrows[Pointers.HOOK][0])!!.tap(arrows[Pointers.SHORT][0])!!
        assertTrue(won.solved)
        val bumped = s.tap(arrows[Pointers.SHORT][0])!!
        assertEquals(s.lives - 1, bumped.lives)
        assertFalse(bumped.failed)
    }

    @Test
    fun `captions fit the walkthrough's slot`() {
        for (f in frames) {
            assertTrue(f.caption, f.caption.length <= 170)
            assertTrue(f.done, f.done.length <= 170)
            assertTrue(f.retry, f.retry.length <= 170)
        }
    }
}
