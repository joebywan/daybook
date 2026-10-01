package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Snap
import com.joebywan.daybook.puzzles.SnapState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The win check against the rules as written, and the drag against a slow drag.
 *
 * The rules: one line from 1, through every square exactly once, one step at a time, meeting the
 * numbers in ascending order and ending on the highest.
 */
class SnapRulesTest {

    /** The rules, written out again here and sharing nothing with `Snap`. */
    private fun obeys(s: SnapState, path: List<Int>): Boolean {
        val n = s.width * s.height
        if (path.size != n || path.toSet().size != n || path.any { it !in 0 until n }) return false
        for (i in 1 until n) {
            val a = path[i - 1]
            val b = path[i]
            val dr = kotlin.math.abs(a / s.width - b / s.width)
            val dc = kotlin.math.abs(a % s.width - b % s.width)
            if (dr + dc != 1) return false
        }
        val order = path.map { s.waypoints[it] }.filter { it > 0 }
        val top = s.waypoints.max()
        return order == (1..top).toList() && s.waypoints[path.first()] == 1 && s.waypoints[path.last()] == top
    }

    // 3x3, numbers 1 at the corner (0) and 2 at square 1, so several lines meet them in order:
    //  1 2 .
    //  . . .
    //  . . .
    private val loose = SnapState(3, 3, listOf(1, 2, 0, 0, 0, 0, 0, 0, 0), emptyList())

    @Test
    fun `a line through every square in order that does not end on the top number is not solved`() {
        val path = listOf(0, 1, 2, 5, 4, 3, 6, 7, 8)
        assertFalse("independent checker", obeys(loose, path))
        assertFalse(loose.copy(path = path).solved)
    }

    @Test
    fun `a line through every square in order that ends on the top number is solved`() {
        // 1 at the corner, 2 at the far corner: the snake ends on 2.
        val s = SnapState(3, 3, listOf(1, 0, 0, 0, 0, 0, 0, 0, 2), emptyList())
        val path = listOf(0, 1, 2, 5, 4, 3, 6, 7, 8)
        assertTrue("independent checker", obeys(s, path))
        assertTrue(s.copy(path = path).solved)
    }

    @Test
    fun `the win check agrees with the independent rules on assorted lines`() {
        val snake = listOf(0, 1, 2, 5, 4, 3, 6, 7, 8)
        val boards = listOf(
            loose,
            SnapState(3, 3, listOf(1, 0, 0, 0, 0, 0, 0, 0, 2), emptyList()),
            SnapState(3, 3, listOf(0, 0, 2, 0, 0, 0, 0, 0, 1), emptyList()),
            SnapState(3, 3, listOf(1, 0, 0, 0, 3, 0, 0, 0, 2), emptyList()),
        )
        val paths = listOf(
            snake, snake.reversed(), snake.dropLast(1), snake + 0,
            listOf(0, 3, 6, 7, 4, 1, 2, 5, 8), listOf(0, 1, 2, 5, 8, 7, 6, 3, 4),
            listOf(0, 4, 8, 1, 2, 3, 5, 6, 7), // jumps between squares
        )
        for (b in boards) for (p in paths) {
            assertEquals("board ${b.waypoints} line $p", obeys(b, p), b.copy(path = p).solved)
        }
    }

    /** Every line the independent rules accept, up to [cap]; naive depth-first from 1. */
    private fun answers(s: SnapState, cap: Int): List<List<Int>> {
        val n = s.cellCount
        val trail = ArrayList<Int>()
        val found = mutableListOf<List<Int>>()
        fun walk(cell: Int) {
            if (found.size >= cap) return
            trail += cell
            if (trail.size == n) {
                if (obeys(s, trail)) found += trail.toList()
            } else {
                val r = cell / s.width
                val c = cell % s.width
                val next = buildList {
                    if (r > 0) add(cell - s.width)
                    if (r < s.height - 1) add(cell + s.width)
                    if (c > 0) add(cell - 1)
                    if (c < s.width - 1) add(cell + 1)
                }
                for (m in next) if (m !in trail) walk(m)
            }
            trail.removeAt(trail.lastIndex)
        }
        walk(s.waypoints.indexOf(1))
        return found
    }

    @Test
    fun `generated boards have exactly one answer under the rules including the end`() {
        // The generator proves uniqueness under a looser rule (any end), which implies this one.
        for (seed in listOf(1L, 404L, 7919L, 20260924L, 555555L)) {
            val s = Snap.generate(seed, Difficulty.STANDARD) as SnapState
            val all = answers(s, cap = 2)
            assertEquals("STANDARD/$seed", 1, all.size)
            assertTrue(s.copy(path = all.single()).solved)
        }
        for (seed in listOf(1L, 404L)) {
            val s = Snap.generate(seed, Difficulty.HARD) as SnapState
            assertEquals("HARD/$seed", 1, answers(s, cap = 2).size)
        }
    }

    // ---- drag ---------------------------------------------------------------------------------

    private val board = SnapState(5, 5, MutableList(25) { 0 }.also { it[0] = 1; it[24] = 2 }, emptyList())

    /** Cell centres in cell units. */
    private fun cx(cell: Int, w: Int = 5) = cell % w + 0.5f
    private fun cy(cell: Int, w: Int = 5) = cell / w + 0.5f

    /** A pointer moving through [cells] centre to centre, one event per square. */
    private fun slow(start: SnapState, cells: List<Int>): SnapState {
        var s = Snap.dragStart(start, cx(cells[0]), cy(cells[0]))
        for (i in 1 until cells.size) {
            s = Snap.dragMove(s, cx(cells[i - 1]), cy(cells[i - 1]), cx(cells[i]), cy(cells[i]))
        }
        return s
    }

    @Test
    fun `a fast straight drag across several squares draws the line a slow one does`() {
        val fast = Snap.dragMove(Snap.dragStart(board, cx(0), cy(0)), cx(0), cy(0), cx(4), cy(0))
        assertEquals(listOf(0, 1, 2, 3, 4), fast.path)
        assertEquals(slow(board, listOf(0, 1, 2, 3, 4)).path, fast.path)
    }

    @Test
    fun `a fast drag with a turn in it visits every crossed square in order`() {
        // Start on 1, then one jump from (0,0) round to (2,3): across, down.
        val start = Snap.dragStart(board, cx(0), cy(0))
        val jumped = Snap.dragMove(start, cx(0), cy(0), cx(1), cy(0) + 0f)
        val far = Snap.dragMove(jumped, cx(1), cy(0), cx(1), cy(16))
        assertEquals(listOf(0, 1, 6, 11, 16), far.path)
    }

    @Test
    fun `a fast drag backs up along the line like a slow one`() {
        val drawn = slow(board, listOf(0, 1, 2, 3, 4))
        val fast = Snap.dragMove(drawn, cx(4), cy(0), cx(1), cy(0))
        assertEquals(listOf(0, 1), fast.path)
    }

    @Test
    fun `a fast drag takes the squares it can and refuses the illegal one`() {
        // 1 at 0, line is 0,1. A jump down the board from 1 to 21 crosses 6, 11, 16, 21: all legal.
        val drawn = slow(board, listOf(0, 1))
        val down = Snap.dragMove(drawn, cx(1), cy(1), cx(1), cy(21))
        assertEquals(listOf(0, 1, 6, 11, 16, 21), down.path)
        // And one jump straight back up the column rubs it out to 1, as five slow steps would.
        val back = Snap.dragMove(down, cx(21), cy(21), cx(1), cy(1))
        assertEquals(listOf(0, 1), back.path)
        assertEquals(slowPathBack(down), back.path)
    }

    private fun slowPathBack(from: SnapState): List<Int> {
        var s = from
        var prev = s.path.last()
        for (cell in listOf(16, 11, 6, 1)) {
            s = Snap.dragMove(s, cx(prev), cy(prev), cx(cell), cy(cell))
            prev = cell
        }
        return s.path
    }

    @Test
    fun `a jump emits one state, not one per square`() {
        // dragMove is one pointer event: it hands back a single state however many squares it crossed.
        val start = Snap.dragStart(board, cx(0), cy(0))
        val fast = Snap.dragMove(start, cx(0), cy(0), cx(4), cy(0))
        assertEquals(5, fast.path.size)
        assertEquals(start.moves + 4, fast.moves)
    }

    @Test
    fun `positions off the board are clamped, as they always were`() {
        val start = Snap.dragStart(board, cx(0), cy(0))
        val off = Snap.dragMove(start, cx(0), cy(0), 40f, -3f)
        assertEquals(listOf(0, 1, 2, 3, 4), off.path)
    }
}
