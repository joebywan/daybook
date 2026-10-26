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

    @Test fun `a wrong flag opens a mine, which loses the board, and the chord stops there`() {
        // Flags on 1 and 2 make the 2: the chord reveals 0, a mine, and goes no further.
        val lost = board().flag(1)!!.flag(2)!!.chord(4)!!
        assertEquals(listOf(0), lost.bumps)
        assertTrue(lost.failed)
        assertEquals("never reached", '.', lost.marks[3])
        assertNull("nothing happens on a lost board", lost.chord(4))
    }

    @Test fun `the walkthrough's chord frame takes the chord and refuses a single reveal`() {
        val f = Mines.tutorial[4]
        val s = f.state as MinesState
        assertEquals(1, s.number(Mines.CHORD_ONE))
        assertTrue(s.isFlag(Mines.FLAG_SQUARE) && s.isMine(Mines.FLAG_SQUARE))
        assertEquals(Mines.CHORDED, MinesLogic.neighbours(5, 5, Mines.CHORD_ONE).filter { s.marks[it] == MinesLogic.HIDDEN })
        val chorded = s.chord(Mines.CHORD_ONE)!!
        assertEquals(TutorialStep.DONE, f.judge!!(chorded))
        assertEquals(TutorialStep.REJECT, f.judge!!(s.reveal(Mines.CHORDED[0])!!))
        assertEquals(chorded, Mines.tutorial[5].state)
    }
}
