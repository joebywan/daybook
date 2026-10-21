package com.joebywan.daybook

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.puzzles.Sand
import com.joebywan.daybook.puzzles.SandKeyAction
import com.joebywan.daybook.puzzles.press
import com.joebywan.daybook.puzzles.sandKeyAction
import com.joebywan.daybook.puzzles.tap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SandKeysTest {

    @Test
    fun `digits pick tubes one to ten, zero is the tenth, Escape puts down`() {
        assertEquals(SandKeyAction.Tube(0), sandKeyAction(Key.One))
        assertEquals(SandKeyAction.Tube(8), sandKeyAction(Key.NumPad9))
        assertEquals(SandKeyAction.Tube(9), sandKeyAction(Key.Zero))
        assertEquals(SandKeyAction.PutDown, sandKeyAction(Key.Escape))
        assertNull(sandKeyAction(Key.A))
        assertNull(sandKeyAction(Key.Enter))
    }

    @Test
    fun `keys do what taps do`() {
        val s = Sand.tutorialBoard()
        // First press lifts, second pours.
        val up = s.press(null, SandKeyAction.Tube(0))
        assertEquals(0, up.lifted)
        assertNull(up.state)
        val poured = s.press(up.lifted, SandKeyAction.Tube(1))
        assertEquals(s.tap(0, 1).state, poured.state)
        assertEquals(s.pour(0, 1), poured.state)
        assertNull(poured.lifted)
        // The same key again puts it down; Escape too.
        assertNull(s.press(0, SandKeyAction.Tube(0)).lifted)
        assertNull(s.press(0, SandKeyAction.PutDown).lifted)
        // A refused pour shakes the target and keeps the tube lifted.
        val refused = s.press(0, SandKeyAction.Tube(3))
        assertEquals(3, refused.refused)
        assertEquals(0, refused.lifted)
        assertNull(refused.state)
        // An empty tube cannot be lifted; a tube past the board's last is no key.
        assertNull(s.press(null, SandKeyAction.Tube(4)).lifted)
        assertEquals(0, s.press(0, SandKeyAction.Tube(9)).lifted)
        assertNull(s.press(0, SandKeyAction.Tube(9)).state)
    }
}
