package com.joebywan.daybook

import androidx.compose.ui.input.key.Key
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
}
