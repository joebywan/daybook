package com.joebywan.daybook

import com.joebywan.daybook.puzzles.KingsState
import com.joebywan.daybook.puzzles.Mark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The marking aids are the part of Kings a player leans on hardest, and the part that would rot
 * silently: eliminations are derived on every render, so a mistake here shows up as marks that
 * linger after an undo rather than as a crash. These pin the derivation, and pin the sweep to one
 * emission — the screen pushes an undo entry per emitted state, so "one gesture, one undo" is a
 * property of this class and not of the UI.
 */
class KingsMarkingTest {

    private val size = 5

    /**
     * Regions that deliberately cut across rows and columns, so a derivation that quietly ignored
     * the region (or stood in a row for it) would still fail.
     */
    private val regions = listOf(
        0, 0, 1, 1, 1,
        0, 0, 1, 1, 1,
        2, 2, 2, 3, 3,
        2, 4, 4, 3, 3,
        4, 4, 4, 3, 3,
    )

    private fun board(vararg marks: Pair<Int, Mark>): KingsState = KingsState(
        size = size,
        region = regions,
        marks = List(size * size) { Mark.EMPTY }.toMutableList()
            .also { list -> marks.forEach { (i, m) -> list[i] = m } },
        solution = setOf(12),
    )

    @Test
    fun `a king rules out exactly its row, column, region and neighbours`() {
        val s = board(12 to Mark.KING)

        // Row 2 (10..14), column 2 (2, 7, 12, 17, 22), region 2 (10, 11, 12, 15) and the eight
        // squares touching r2c2 (6, 7, 8, 11, 13, 16, 17, 18) — less the king's own square.
        val expected = setOf(2, 6, 7, 8, 10, 11, 13, 14, 15, 16, 17, 18, 22)
        assertEquals(expected, s.eliminated())
        assertEquals(expected + 12, s.eliminatedBy(12))
    }

    @Test
    fun `an empty board rules out nothing`() {
        assertEquals(emptySet<Int>(), board().eliminated())
        assertEquals(emptySet<Int>(), board(0 to Mark.BLOCKED).eliminated())
    }

    @Test
    fun `two kings rule out the union of their squares, kings excepted`() {
        val s = board(12 to Mark.KING, 4 to Mark.KING)
        val expected = (s.eliminatedBy(12) + s.eliminatedBy(4)) - setOf(12, 4)
        assertEquals(expected, s.eliminated())
        assertTrue(12 !in s.eliminated() && 4 !in s.eliminated())
    }

    @Test
    fun `taking a king away takes its eliminations with it and leaves manual marks alone`() {
        val placed = board(0 to Mark.BLOCKED, 24 to Mark.BLOCKED, 12 to Mark.KING)
        assertTrue(placed.eliminated().isNotEmpty())

        // The third tap on a king clears it; nothing else in the board state changes.
        val cleared = placed.cycle(12)
        assertEquals(emptySet<Int>(), cleared.eliminated())
        assertEquals(Mark.BLOCKED, cleared.marks[0])
        assertEquals(Mark.BLOCKED, cleared.marks[24])
        assertEquals(Mark.EMPTY, cleared.marks[12])
    }

    @Test
    fun `a manual mark on an already ruled-out square stands on its own`() {
        val s = board(12 to Mark.KING)
        assertTrue(14 in s.eliminated())

        val marked = s.cycle(14)
        assertEquals(Mark.BLOCKED, marked.marks[14])
        assertEquals(s.eliminated(), marked.eliminated())

        // And it survives the king going away, because it was never an auto-mark.
        val cleared = marked.cycle(12)
        assertEquals(Mark.BLOCKED, cleared.marks[14])
        assertEquals(emptySet<Int>(), cleared.eliminated())
    }

    @Test
    fun `a sweep takes its one action from the square it started on`() {
        val s = board(1 to Mark.BLOCKED, 12 to Mark.KING)
        assertEquals(Mark.BLOCKED, s.sweepMark(0))
        assertEquals(Mark.EMPTY, s.sweepMark(1))
        assertNull(s.sweepMark(12))
    }

    @Test
    fun `a sweep is a single state, whatever it crosses`() {
        val s = board(6 to Mark.BLOCKED, 7 to Mark.KING)
        val run = listOf(5, 6, 7, 8, 9)

        val swept = s.paint(run, Mark.BLOCKED)
        assertEquals(Mark.BLOCKED, swept.marks[5])
        assertEquals(Mark.BLOCKED, swept.marks[8])
        assertEquals(Mark.BLOCKED, swept.marks[9])
        // Already blocked, and the king in the path: neither is disturbed.
        assertEquals(Mark.BLOCKED, swept.marks[6])
        assertEquals(Mark.KING, swept.marks[7])
        assertEquals(s.moves + 3, swept.moves)

        // Undo is one step back because the gesture produced one state, and the board it replaced
        // is still whole: nothing was mutated in place on the way through the run.
        assertEquals(Mark.EMPTY, s.marks[5])
        assertEquals(Mark.EMPTY, s.marks[9])
    }

    @Test
    fun `a sweep that changes nothing is not a state at all`() {
        val s = board(5 to Mark.BLOCKED, 6 to Mark.BLOCKED)
        // Same instance, so the screen has nothing to push and Undo stays where it was.
        assertSame(s, s.paint(listOf(5, 6), Mark.BLOCKED))
        assertSame(s, s.paint(emptyList(), Mark.BLOCKED))
    }

    @Test
    fun `clearing a sweep leaves kings standing`() {
        val s = board(5 to Mark.BLOCKED, 6 to Mark.BLOCKED, 7 to Mark.KING)
        val cleared = s.paint(listOf(5, 6, 7), Mark.EMPTY)
        assertEquals(Mark.EMPTY, cleared.marks[5])
        assertEquals(Mark.EMPTY, cleared.marks[6])
        assertEquals(Mark.KING, cleared.marks[7])
        assertEquals(s.moves + 2, cleared.moves)
    }
}
