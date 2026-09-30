package com.joebywan.daybook

import com.joebywan.daybook.puzzles.Snap
import com.joebywan.daybook.puzzles.SnapState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Snap's walkthrough, checked against an enumerator written out again here.
 *
 * The enumerator is the naive thing, as in `SnapCluesTest`: every line from 1 through every
 * square, no pruning, with the numbers checked only once a whole line exists. It checks the rule
 * the win check uses (numbers in order, all of them), which is looser than the caption's "ending
 * on the highest number", so a board unique under it is unique under both.
 *
 * Snap has no hints ([Snap.offersHints] says why, with the measurements), so there is no teacher
 * to test; every claim a caption makes about the board is pinned here instead.
 */
class SnapTutorialTest {

    private val w = Snap.TUTORIAL_W
    private val n = w * w
    private val marks = Snap.TUTORIAL_WAYPOINTS

    private fun neighbours(cell: Int): List<Int> = buildList {
        val r = cell / w
        val c = cell % w
        if (r > 0) add(cell - w)
        if (r < w - 1) add(cell + w)
        if (c > 0) add(cell - 1)
        if (c < w - 1) add(cell + 1)
    }

    /** Every finished line that begins with [prefix] and obeys the numbers. */
    private fun answers(prefix: List<Int>): List<List<Int>> {
        val out = mutableListOf<List<Int>>()
        val trail = prefix.toMutableList()
        fun walk() {
            if (trail.size == n) {
                val order = trail.mapNotNull { c -> marks[c].takeIf { it > 0 } }
                if (order == (1..marks.count { it > 0 }).toList()) out += trail.toList()
                return
            }
            for (next in neighbours(trail.last())) {
                if (next in trail) continue
                trail += next
                walk()
                trail.removeAt(trail.lastIndex)
            }
        }
        walk()
        return out
    }

    private fun board(path: List<Int>) = SnapState(w, w, marks, path)

    private val solution = Snap.TUTORIAL_SOLUTION
    private val numbered = marks.indices.filter { marks[it] > 0 }

    @Test
    fun `the walkthrough board has exactly one answer, and it ends on the highest number`() {
        val all = answers(listOf(marks.indexOf(1)))
        assertEquals(listOf(solution), all)
        assertEquals(marks.maxOrNull(), marks[solution.last()])
        assertTrue(board(solution).solved)
        // Sparse, as the real boards are: four numbers on sixteen squares.
        assertEquals(4, numbered.size)
    }

    /**
     * Every single-emission move the board's gestures can make from [s]: a square added at the end,
     * a square rubbed off the end, the line cut back to any square on it, and the line left as it
     * was (what a drag starting on its end hands in first).
     */
    private fun movesFrom(s: SnapState): List<SnapState> = buildList {
        val path = s.path
        if (path.isEmpty()) {
            // A drag from 1 hands in the one square, then the line into the next. The first is
            // refused and not applied, but the board keeps following the drag, so the second
            // arrives as one state too.
            val one = marks.indexOf(1)
            add(board(listOf(one)))
            for (next in neighbours(one)) add(board(listOf(one, next)))
            return@buildList
        }
        add(s.copy(moves = s.moves + 1))
        for (next in neighbours(path.last())) if (next !in path) add(board(path + next))
        for (k in 1 until path.size) add(board(path.take(k)))
    }

    @Test
    fun `each gesture frame takes its one move and refuses every other the board can make`() {
        val frames = Snap.tutorial
        var gestures = 0
        for ((k, frame) in frames.withIndex()) {
            val accepts = frame.accepts ?: continue
            gestures++
            val base = frame.state as SnapState
            val wanted = movesFrom(base).filter { accepts(it) }
            assertEquals("frame $k: accepts ${wanted.map { it.path }}", 1, wanted.size)
            // A drag can reach the wanted line by way of a wrong turn and back again; the frame
            // looks only at the line, not how many moves made it.
            val target = wanted.single().path
            assertTrue("frame $k: refused the line after a detour", accepts(board(target).copy(moves = 99)))
            // Each move is the next frame's board, or that board with a wrong turn added.
            val next = frames[k + 1].state as SnapState
            assertEquals("frame $k: the next frame does not carry on from here", target, next.path.take(target.size))
        }
        assertEquals("gesture frames", 6, gestures)
        assertTrue(frames.last().freePlay)
        assertFalse((frames.last().state as SnapState).solved)
    }

    @Test
    fun `lines the frames build can be finished, and their mistakes cannot`() {
        for ((k, frame) in Snap.tutorial.withIndex()) {
            val path = (frame.state as SnapState).path
            if (path.isEmpty()) continue
            val onAnswer = path == solution.take(path.size)
            assertEquals("frame $k: $path", onAnswer, answers(path).isNotEmpty())
        }
        // Only the two frames that show a wrong turn start off the answer.
        val offAnswer = Snap.tutorial.count {
            val p = (it.state as SnapState).path
            p != solution.take(p.size)
        }
        assertEquals(2, offAnswer)
    }

    @Test
    fun `what the captions say about the board is true`() {
        val frames = Snap.tutorial
        // "A corner touches only two squares", and the first corner is beside 1.
        assertEquals(listOf(4, 1), neighbours(0))
        assertEquals(1, marks[1])
        // "The square to the right is 4": from the end of the line after two moves.
        assertEquals(4, solution[2])
        assertEquals(4, marks[solution[2] + 1])
        // "Say you dashed for 2. Now the bottom corner has one way in."
        val dashed = (frames[5].state as SnapState).path
        assertTrue(9 in dashed && marks[9] == 2)
        assertEquals(1, neighbours(12).count { it !in dashed })
        // "This line reached 3 before 2."
        val early = (frames[7].state as SnapState).path
        assertEquals(3, marks[early.last()])
        assertFalse(early.any { marks[it] == 2 })
        // "Drag from 3 back one square": the wanted line is the same line one square shorter.
        assertTrue(frames[7].accepts!!(board(early.dropLast(1))))
        // "Put your finger on the glowing square to cut back to it": the glowing square is the end
        // of the line the frame wants.
        assertEquals(setOf(8), frames[5].highlight.strong)
        assertTrue(frames[5].accepts!!(board(dashed.take(dashed.indexOf(8) + 1))))
    }

    @Test
    fun `every glowing square is on the board, and every gesture frame glows its target`() {
        for ((k, frame) in Snap.tutorial.withIndex()) {
            for (c in frame.highlight.strong + frame.highlight.soft) assertTrue("frame $k: $c", c in 0 until n)
            val accepts = frame.accepts ?: continue
            val base = frame.state as SnapState
            val target = movesFrom(base).first { accepts(it) }.path
            // The square the move ends on: the new end of the line, or the one it is cut back to,
            // or (for a drag back) the step being rubbed out.
            val landing = target.last()
            val glowing = frame.highlight.strong
            assertTrue("frame $k: neither ${target.last()} nor the step rubbed out glows",
                landing in glowing || base.path.lastOrNull() in glowing)
        }
    }

    @Test
    fun `captions fit the panel`() {
        for ((k, frame) in Snap.tutorial.withIndex()) {
            assertTrue("frame $k caption is ${frame.caption.length} chars", frame.caption.length <= 200)
            assertTrue("frame $k caption", frame.caption.first().isUpperCase() && frame.caption.endsWith("."))
            // One line each under the caption.
            assertTrue("frame $k retry", frame.retry.length <= 60)
            assertTrue("frame $k done", frame.done.length <= 60)
        }
    }

    @Test
    fun `your turn can be finished`() {
        val last = Snap.tutorial.last()
        assertTrue(last.freePlay)
        val s = last.state as SnapState
        val rest = answers(s.path)
        assertEquals(1, rest.size)
        assertNotNull(rest.single())
    }
}
