package com.joebywan.daybook

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.puzzles.Cipher
import com.joebywan.daybook.puzzles.CipherKeyAction
import com.joebywan.daybook.puzzles.CipherLogic.EMPTY
import com.joebywan.daybook.puzzles.applyKey
import com.joebywan.daybook.puzzles.cipherKeyAction
import com.joebywan.daybook.puzzles.enter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CipherKeysTest {
    private val board = Cipher.tutorialBoard() // TO + GO = OUT: letters G O T U

    @Test
    fun `keys map`() {
        assertEquals(CipherKeyAction.Letter('o'), cipherKeyAction(Key.O))
        assertEquals(CipherKeyAction.Letter('z'), cipherKeyAction(Key.Z))
        assertEquals(CipherKeyAction.Digit(0), cipherKeyAction(Key.Zero))
        assertEquals(CipherKeyAction.Digit(7), cipherKeyAction(Key.NumPad7))
        assertEquals(CipherKeyAction.Clear, cipherKeyAction(Key.Backspace))
        assertEquals(CipherKeyAction.Clear, cipherKeyAction(Key.Delete))
        assertEquals(CipherKeyAction.Move(1), cipherKeyAction(Key.DirectionRight))
        assertEquals(CipherKeyAction.Move(1), cipherKeyAction(Key.DirectionDown))
        assertEquals(CipherKeyAction.Move(-1), cipherKeyAction(Key.DirectionLeft))
        assertEquals(CipherKeyAction.Move(-1), cipherKeyAction(Key.DirectionUp))
        assertNull(cipherKeyAction(Key.Spacebar))
    }

    @Test
    fun `a letter selects it, a digit sets it as the pad does, a repeat or Backspace clears`() {
        assertNull(board.applyKey(CipherKeyAction.Letter('z'))) // not in the sum
        val o = board.applyKey(CipherKeyAction.Letter('o'))!!
        assertEquals(1, o.selected)
        val one = o.applyKey(CipherKeyAction.Digit(1))!!
        assertEquals(o.enter(1, 1), one)
        assertEquals(1, one.digits[1])
        assertEquals(1, one.moves)
        assertEquals(EMPTY, one.applyKey(CipherKeyAction.Digit(1))!!.digits[1])
        assertEquals(EMPTY, one.applyKey(CipherKeyAction.Clear)!!.digits[1])
        assertEquals(0, one.applyKey(CipherKeyAction.Digit(0))!!.digits[1]) // 0 is a digit here, not a clear
    }

    @Test
    fun `arrows walk the letters without wrapping, and nothing selected means no digit`() {
        assertNull(board.applyKey(CipherKeyAction.Digit(3)))
        assertNull(board.applyKey(CipherKeyAction.Clear))
        val first = board.applyKey(CipherKeyAction.Move(1))!!
        assertEquals(0, first.selected)
        assertNull(first.applyKey(CipherKeyAction.Move(-1)))
        assertEquals(1, first.applyKey(CipherKeyAction.Move(1))!!.selected)
        assertNull(first.select(3).applyKey(CipherKeyAction.Move(1)))
    }
}
