package com.joebywan.daybook

import com.joebywan.daybook.core.TutorialStep
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
        assertEquals("one-move frames", 4, gestures)
        assertEquals("frames with a move", 6, frames.count { it.judge != null })
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
        // Every frame starts on the answer; the wrong turns are the player's, and cannot be finished.
        val offAnswer = Snap.tutorial.count {
            val p = (it.state as SnapState).path
            p != solution.take(p.size)
        }
        assertEquals(0, offAnswer)
        for ((line, detour) in detours) assertTrue("$line + $detour", answers(line + detour).isEmpty())
    }

    /** The two mistake frames, 6 and 8: the line each starts on and the wrong turn the player makes. */
    private val detours = listOf(5 to listOf(9, 10), 7 to listOf(14))
        .map { (k, d) -> (Snap.tutorial[k].state as SnapState).path to d }

    @Test
    fun `a mistake frame takes the wrong turn and back, made by the real gestures, and nothing else`() {
        val frames = Snap.tutorial
        for ((k, d) in listOf(5 to listOf(9, 10), 7 to listOf(14))) {
            val judge = frames[k].steps!!
            val base = frames[k].state as SnapState
            var s = base
            // A drag starting on the end of the line hands it in unchanged: not a refusal.
            assertEquals(TutorialStep.PROGRESS, judge(s.copy()))
            for (cell in d) {
                for (m in movesFrom(s)) if (m.path != base.path && (1..d.size).none { m.path == base.path + d.take(it) }) {
                    assertEquals("frame $k: ${m.path}", TutorialStep.REJECT, judge(m))
                }
                s = Snap.extend(s, cell)
                assertEquals(TutorialStep.PROGRESS, judge(s))
            }
            // Back: frame 6 by a drag starting on the glowing square, frame 8 by dragging back a square.
            val back = if (k == 5) Snap.dragStart(s, 0.5f, 2.5f) else Snap.extend(s, base.path.last())
            assertEquals("frame $k", base.path, back.path)
            assertEquals("frame $k", TutorialStep.DONE, judge(back))
            assertTrue("frame $k glows where to go back to", base.path.last() in frames[k].glow!!(s).strong)
            assertTrue("frame $k glows the wrong turn first", d.first() in frames[k].glow!!(base).strong)
            assertEquals("frame $k: the next frame carries on", base.path, (frames[k + 1].state as SnapState).path.take(base.path.size))
        }
    }

    @Test
    fun `what the captions say about the board is true`() {
        // "A corner touches only two squares", and the first corner is beside 1.
        assertEquals(listOf(4, 1), neighbours(0))
        assertEquals(1, marks[1])
        // "The square to the right is 4": from the end of the line after two moves.
        assertEquals(4, solution[2])
        assertEquals(4, marks[solution[2] + 1])
        // "Try dashing for 2 ... the bottom corner is left one way in."
        val (line6, dash) = detours[0]
        assertTrue(marks[9] == 2 && dash.first() == 9)
        assertEquals(1, neighbours(12).count { it !in line6 + dash })
        // "Say you went for 3 first ... 2 has to come before it."
        val (line8, early) = detours[1]
        assertEquals(3, marks[early.single()])
        assertFalse(line8.any { marks[it] == 2 })
        assertEquals(13, line8.last())
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
