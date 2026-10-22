package com.joebywan.daybook

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.puzzles.Pointers
import com.joebywan.daybook.puzzles.PointersKeyAction
import com.joebywan.daybook.puzzles.PointersState
import com.joebywan.daybook.puzzles.cursorAfter
import com.joebywan.daybook.puzzles.pointersKeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PointersKeysTest {

    // 4x4: top row (0,1,2) points left, second row (4,5,6) right, the right-hand column (3,7,11,15) down,
    // a hook (8,12,13,14) and a short one (10,9).
    private val board = Pointers.tutorialBoard()

    @Test
    fun `keys map`() {
        assertEquals(PointersKeyAction.Move(-1, 0), pointersKeyAction(Key.DirectionUp))
        assertEquals(PointersKeyAction.Move(0, 1), pointersKeyAction(Key.DirectionRight))
        assertEquals(PointersKeyAction.Tap, pointersKeyAction(Key.Enter))
        assertEquals(PointersKeyAction.Tap, pointersKeyAction(Key.Spacebar))
        assertNull(pointersKeyAction(Key.A))
    }

    @Test
    fun `the first arrow key lands on the first arrow's head`() {
        assertEquals(0, board.cursorAfter(null, 0, 1))
        assertEquals(6, board.copy(removed = listOf(0)).cursorAfter(null, 1, 0))
    }

    @Test
    fun `the cursor moves to the nearest other arrow that way, skipping its own squares`() {
        // From the top row's 0, down is the second row's 4; right is the side column's 3 (past the row's own 1 and 2).
        assertEquals(4, board.cursorAfter(0, 1, 0))
        assertEquals(3, board.cursorAfter(0, 0, 1))
        // From 5 in the second row, up is the top row's 1, down is the short arrow's 9.
        assertEquals(1, board.cursorAfter(5, -1, 0))
        assertEquals(9, board.cursorAfter(5, 1, 0))
        // Nothing further left of 0: the cursor stays.
        assertEquals(0, board.cursorAfter(0, 0, -1))
        // A cleared arrow is skipped: with the second row gone, down from 0 is the hook's 8.
        assertEquals(8, board.copy(removed = listOf(1)).cursorAfter(0, 1, 0))
    }

    @Test
    fun `Enter on the cursor is the same tap a finger makes`() {
        // The board applies Tap as state.tap(cursor): pinned here so the two cannot drift.
        val viaKeys = board.tap(board.cursorAfter(null, 0, 1)!!)
        assertEquals(board.tap(Pointers.TUTORIAL_ARROWS[0][0]), viaKeys)
    }
}
