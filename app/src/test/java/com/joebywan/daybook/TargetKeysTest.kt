package com.joebywan.daybook

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.puzzles.Target
import com.joebywan.daybook.puzzles.TargetEntry
import com.joebywan.daybook.puzzles.TargetKeyAction
import com.joebywan.daybook.puzzles.TargetOp
import com.joebywan.daybook.puzzles.TargetState
import com.joebywan.daybook.puzzles.press
import com.joebywan.daybook.puzzles.tapOp
import com.joebywan.daybook.puzzles.tapTile
import com.joebywan.daybook.puzzles.targetKeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TargetKeysTest {
    private val board = Target.tutorialBoard() // 25 4 3 2 7 1 -> 106

    private fun keys(s: TargetState, vararg actions: TargetKeyAction): Triple<TargetState, TargetEntry, String?> {
        var state = s
        var e = TargetEntry()
        var note: String? = null
        for (a in actions) {
            val step = state.press(e, a)
            e = step.entry
            note = step.note
            step.state?.let { state = it }
        }
        return Triple(state, e, note)
    }

    private fun digits(n: Int) = n.toString().map { TargetKeyAction.Digit(it - '0') }.toTypedArray()

    @Test
    fun `keys map`() {
        assertEquals(TargetKeyAction.Digit(7), targetKeyAction(Key.Seven))
        assertEquals(TargetKeyAction.Digit(7), targetKeyAction(Key.NumPad7))
        assertEquals(TargetKeyAction.Digit(0), targetKeyAction(Key.Zero))
        assertEquals(TargetKeyAction.Operator(TargetOp.Add), targetKeyAction(Key.Plus))
        assertEquals(TargetKeyAction.Operator(TargetOp.Add), targetKeyAction(Key.Equals, shift = true))
        assertNull(targetKeyAction(Key.Equals))
        assertEquals(TargetKeyAction.Operator(TargetOp.Sub), targetKeyAction(Key.Minus))
        assertEquals(TargetKeyAction.Operator(TargetOp.Mul), targetKeyAction(Key.Eight, shift = true))
        assertEquals(TargetKeyAction.Operator(TargetOp.Mul), targetKeyAction(Key.X))
        assertEquals(TargetKeyAction.Operator(TargetOp.Mul), targetKeyAction(Key.NumPadMultiply))
        assertEquals(TargetKeyAction.Operator(TargetOp.Div), targetKeyAction(Key.Slash))
        assertEquals(TargetKeyAction.Operator(TargetOp.Div), targetKeyAction(Key.NumPadDivide))
        assertEquals(TargetKeyAction.Enter, targetKeyAction(Key.Enter))
        assertEquals(TargetKeyAction.Back, targetKeyAction(Key.Backspace))
        assertNull(targetKeyAction(Key.Q))
        assertNull("shifted digits are not digits", targetKeyAction(Key.Three, shift = true))
    }

    @Test
    fun `typing a number, an operator and a number makes the same move as three taps`() {
        val byKeys = keys(board, *digits(25), TargetKeyAction.Operator(TargetOp.Mul), *digits(4), TargetKeyAction.Enter).first
        val first = board.tapTile(TargetEntry(), 0).entry
        val byTaps = board.tapTile(board.tapOp(first, TargetOp.Mul).entry, 1).state!!
        assertEquals(byTaps, byKeys)
        assertEquals(listOf(100, 0, 3, 2, 7, 1), byKeys.slots)
    }

    @Test
    fun `an operator confirms the number typed before it, and subtract puts the bigger tile first`() {
        val (s, _, _) = keys(board, *digits(2), TargetKeyAction.Operator(TargetOp.Sub), *digits(7), TargetKeyAction.Enter)
        assertEquals("7 − 2 = 5", s.log.single().text())
        assertEquals(5, s.slots[3]) // the answer goes where the first tile (2, slot 3) was
        assertEquals(0, s.slots[4])
    }

    @Test
    fun `two equal tiles are two different tiles`() {
        val twins = TargetState(listOf(5, 5, 3, 2, 1, 1), 100, 0)
        val (s, _, _) = keys(twins, *digits(5), TargetKeyAction.Operator(TargetOp.Add), *digits(5), TargetKeyAction.Enter)
        assertEquals(10, s.slots[0])
        assertEquals(0, s.slots[1])
    }

    @Test
    fun `a number that is not a tile is a note, and a refusal keeps the move so another tile can be tried`() {
        val (_, e0, note) = keys(board, *digits(26), TargetKeyAction.Enter)
        assertEquals("No tile shows 26", note)
        assertEquals(TargetEntry(), e0)

        // 7 / 2 is not whole: refused, the first tile and operator stay.
        val (s, e, n) = keys(board, *digits(7), TargetKeyAction.Operator(TargetOp.Div), *digits(2), TargetKeyAction.Enter)
        assertEquals(board, s)
        assertNotNull(n)
        assertEquals(TargetOp.Div, e.op)
        assertEquals(4, e.first)
        // ...and then 7 / 1 works.
        val (done, _, _) = keys(board, *digits(7), TargetKeyAction.Operator(TargetOp.Div), *digits(2), TargetKeyAction.Enter, *digits(1), TargetKeyAction.Enter)
        assertEquals("7 \u00F7 1 = 7", done.log.single().text())
    }

    private fun refused(tiles: List<Int>, op: TargetOp): String? {
        val t = TargetState(tiles, 100, 0)
        return t.tapTile(t.tapOp(t.tapTile(TargetEntry(), 0).entry, op).entry, 1).note
    }

    @Test
    fun `refusals read as sums`() {
        assertEquals("5 \u2212 5 would be zero", refused(listOf(5, 5, 3, 2, 1, 1), TargetOp.Sub))
        assertEquals("5 \u00F7 2 is not a whole number", refused(listOf(5, 2, 3, 2, 1, 1), TargetOp.Div))
        assertNull("9900 is fine", refused(listOf(100, 99, 3, 2, 1, 1), TargetOp.Mul))
        assertEquals("Over 9999", refused(listOf(100, 100, 3, 2, 1, 1), TargetOp.Mul))
    }

    @Test
    fun `backspace takes back a digit, then the operator, then the first tile`() {
        val e1 = keys(board, *digits(2), TargetKeyAction.Operator(TargetOp.Add), *digits(2)).second
        assertEquals("2", e1.typed)
        val e2 = board.press(e1, TargetKeyAction.Back).entry
        assertEquals("", e2.typed)
        assertEquals(TargetOp.Add, e2.op)
        val e3 = board.press(e2, TargetKeyAction.Back).entry
        assertNull(e3.op)
        assertEquals(3, e3.first)
        assertEquals(TargetEntry(), board.press(e3, TargetKeyAction.Back).entry)
    }

    @Test
    fun `digits stop at four, a leading zero is ignored, an operator with no tile is a note`() {
        assertEquals("1234", keys(board, *digits(12345)).second.typed)
        assertEquals("", keys(board, TargetKeyAction.Digit(0)).second.typed)
        val (_, e, note) = keys(board, TargetKeyAction.Operator(TargetOp.Add))
        assertEquals("Pick a number first", note)
        assertNull(e.op)
    }

    @Test
    fun `taps select, switch, deselect, and an empty place does nothing`() {
        val picked = board.tapTile(TargetEntry(), 0).entry
        assertEquals(0, picked.first)
        assertEquals(2, board.tapTile(picked, 2).entry.first) // switching the first tile
        assertEquals(TargetEntry(), board.tapTile(picked, 0).entry) // again: put it down
        val done = board.tapTile(board.tapOp(picked, TargetOp.Mul).entry, 1).state!!
        assertEquals(0, done.slots[1])
        val empty = done.tapTile(TargetEntry(), 1)
        assertEquals(TargetEntry(), empty.entry)
        assertNull(empty.state)
        val withOp = board.tapOp(picked, TargetOp.Mul).entry
        assertEquals(TargetOp.Mul, withOp.op)
        assertNull("the chosen operator again clears it", board.tapOp(withOp, TargetOp.Mul).entry.op)
    }
}
