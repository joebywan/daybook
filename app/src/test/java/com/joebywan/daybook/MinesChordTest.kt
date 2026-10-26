package com.joebywan.daybook

import com.joebywan.daybook.core.TutorialStep
import com.joebywan.daybook.puzzles.Mines
import com.joebywan.daybook.puzzles.MinesLogic
import com.joebywan.daybook.puzzles.MinesState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chording: a tap on a revealed number whose flags make its count reveals the rest around it, as one move. */
class MinesChordTest {

    /**
     * ```
     * * . .
     * . . .
     * . . *
     * ```
     * Square 4 (the centre) is a 2; nothing else is open.
     */
    private val mines = "100" + "000" + "001"
    private fun board() = MinesState(3, 3, mines, 4, "...." + "o" + "....")

    @Test fun `flags that make the count reveal every other hidden neighbour in one move`() {
        val s = board().flag(0)!!.flag(8)!!
        val c = s.chord(4)!!
        assertEquals(s.moves + 1, c.moves)
        assertEquals("f" + "oooo" + "ooo" + "f", c.marks)
        assertEquals(3, c.lives)
        assertTrue(c.solved)
    }

    @Test fun `nothing happens unless the flags make the count and something is left`() {
        val one = board().flag(0)!!
        assertNull("one flag short", one.chord(4))
        assertNull("too many flags", one.flag(8)!!.flag(1)!!.chord(4))
        assertNull("a hidden square", board().chord(0))
        val done = board().flag(0)!!.flag(8)!!.chord(4)!!
        assertNull("a won board", done.chord(4))
    }

    @Test fun `a mine hit counts toward the number like a flag`() {
        val s = board().reveal(0)!!.flag(8)!!
        assertEquals(2, s.lives)
        assertTrue(s.chord(4)!!.solved)
    }

    @Test fun `a wrong flag opens a mine, a life each, and the chord stops when the lives run out`() {
        val wrong = board().flag(1)!!.flag(2)!!.chord(4)!!
        assertEquals(listOf(0, 8), wrong.bumps)
        assertEquals(1, wrong.lives)
        assertFalse(wrong.failed)
        // One life left (two mines hit, two flags: the 4 is made): the chord opens 5, then the mine at 6 ends it.
        val b = "101" + "000" + "101"
        val oneLeft = MinesState(3, 3, b, 4, "...." + "o" + "....").reveal(0)!!.reveal(2)!!.flag(1)!!.flag(3)!!
        assertEquals(1, oneLeft.lives)
        val lost = oneLeft.chord(4)!!
        assertEquals(listOf(0, 2, 6), lost.bumps)
        assertTrue(lost.failed)
        assertEquals('o', lost.marks[5])
        assertEquals("never reached", '.', lost.marks[7])
    }

    @Test fun `the walkthrough's chord frame takes the chord and refuses a single reveal`() {
        val f = Mines.tutorial[4]
        val s = f.state as MinesState
        assertEquals(1, s.number(Mines.CHORD_ONE))
        assertTrue(s.isFlag(Mines.FLAG_SQUARE) && s.isMine(Mines.FLAG_SQUARE))
        assertEquals(Mines.CHORDED, MinesLogic.neighbours(5, 5, Mines.CHORD_ONE).filter { s.marks[it] == MinesLogic.HIDDEN })
        val chorded = s.chord(Mines.CHORD_ONE)!!
        assertEquals(TutorialStep.DONE, f.judge!!(chorded))
        assertEquals(2, chorded.lives)
        assertEquals(TutorialStep.REJECT, f.judge!!(s.reveal(Mines.CHORDED[0])!!))
        assertEquals(chorded, Mines.tutorial[5].state)
    }
}
