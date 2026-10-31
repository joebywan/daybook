package com.joebywan.daybook

import com.joebywan.daybook.puzzles.SquareTap
import com.joebywan.daybook.puzzles.squareTap
import org.junit.Assert.assertEquals
import org.junit.Test

class SudokuSquareTapTest {
    @Test fun `an armed digit enters only with no square selected`() {
        assertEquals(SquareTap.ENTER, squareTap(5, null, 3))
        assertEquals(SquareTap.SELECT, squareTap(5, 7, 3))
    }

    @Test fun `the selected square again lets go, so digit-first can start`() {
        assertEquals(SquareTap.DESELECT, squareTap(5, 5, null))
        assertEquals(SquareTap.DESELECT, squareTap(5, 5, 3))
    }

    @Test fun `with nothing armed a tap selects, as it always has`() {
        assertEquals(SquareTap.SELECT, squareTap(5, null, null))
        assertEquals(SquareTap.SELECT, squareTap(5, 7, null))
    }
}
