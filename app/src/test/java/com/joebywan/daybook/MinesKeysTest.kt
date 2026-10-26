package com.joebywan.daybook

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.movedCursor
import com.joebywan.daybook.puzzles.MinesKeyAction
import com.joebywan.daybook.puzzles.minesKeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MinesKeysTest {

    @Test
    fun `keys map`() {
        assertEquals(MinesKeyAction.Reveal, minesKeyAction(Key.Spacebar))
        assertEquals(MinesKeyAction.Reveal, minesKeyAction(Key.Enter))
        assertEquals(MinesKeyAction.Reveal, minesKeyAction(Key.NumPadEnter))
        assertEquals(MinesKeyAction.Flag, minesKeyAction(Key.F))
        assertNull(minesKeyAction(Key.A))
        // Arrows are the cursor's, not an action: the board asks movedCursor first.
        assertNull(minesKeyAction(Key.DirectionUp))
    }

    @Test
    fun `arrows walk the cursor over an 8-wide board, from the first square`() {
        assertEquals(0, movedCursor(null, Key.DirectionRight, 12, 8))
        assertEquals(9, movedCursor(1, Key.DirectionDown, 12, 8))
        assertEquals(7, movedCursor(7, Key.DirectionRight, 12, 8))
        assertEquals(95, movedCursor(95, Key.DirectionDown, 12, 8))
    }
}
