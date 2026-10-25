package com.joebywan.daybook

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.puzzles.Codeword
import com.joebywan.daybook.puzzles.CodewordKeyAction
import com.joebywan.daybook.puzzles.CodewordLogic.EMPTY
import com.joebywan.daybook.puzzles.applyKey
import com.joebywan.daybook.puzzles.codewordKeyAction
import com.joebywan.daybook.puzzles.enter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodewordKeysTest {
    private val board = Codeword.tutorialBoard() // seven numbers, 2 given (T)

    @Test
    fun `keys map`() {
        assertEquals(CodewordKeyAction.Letter(0), codewordKeyAction(Key.A))
        assertEquals(CodewordKeyAction.Letter(25), codewordKeyAction(Key.Z))
        assertEquals(CodewordKeyAction.Clear, codewordKeyAction(Key.Backspace))
        assertEquals(CodewordKeyAction.Clear, codewordKeyAction(Key.Delete))
        assertEquals(CodewordKeyAction.Move(1), codewordKeyAction(Key.DirectionRight))
        assertEquals(CodewordKeyAction.Move(1), codewordKeyAction(Key.DirectionDown))
        assertEquals(CodewordKeyAction.Move(-1), codewordKeyAction(Key.DirectionLeft))
        assertEquals(CodewordKeyAction.Move(-1), codewordKeyAction(Key.DirectionUp))
        assertNull(codewordKeyAction(Key.Spacebar))
        assertNull(codewordKeyAction(Key.One))
    }

    @Test
    fun `a letter sets the picked number as a tap does, a repeat or Backspace clears`() {
        val one = board.select(1)
        val e = one.applyKey(CodewordKeyAction.Letter(4))!!
        assertEquals(one.enter(1, 4), e)
        assertEquals(4, e.letters[0])
        assertEquals(1, e.moves)
        assertEquals(EMPTY, e.applyKey(CodewordKeyAction.Letter(4))!!.letters[0])
        assertEquals(EMPTY, e.applyKey(CodewordKeyAction.Clear)!!.letters[0])
        // A letter another number holds is not refused.
        assertEquals(19, one.applyKey(CodewordKeyAction.Letter(19))!!.letters[0])
    }

    @Test
    fun `arrows walk the numbers past the givens without wrapping, and nothing picked means no letter`() {
        assertNull(board.applyKey(CodewordKeyAction.Letter(3)))
        assertNull(board.applyKey(CodewordKeyAction.Clear))
        val first = board.applyKey(CodewordKeyAction.Move(1))!!
        assertEquals(1, first.selected)
        assertNull(first.applyKey(CodewordKeyAction.Move(-1)))
        assertEquals(3, first.applyKey(CodewordKeyAction.Move(1))!!.selected) // 2 is given
        assertNull(board.select(7).applyKey(CodewordKeyAction.Move(1)))
    }
}
