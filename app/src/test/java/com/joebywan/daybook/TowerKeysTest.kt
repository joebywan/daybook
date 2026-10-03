package com.joebywan.daybook

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.puzzles.TowerKeyAction
import com.joebywan.daybook.puzzles.TowerState
import com.joebywan.daybook.puzzles.applyKey
import com.joebywan.daybook.puzzles.towerKeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TowerKeysTest {
    private val empty = TowerState(slots = 3, colours = 4, maxGuesses = 6, secret = listOf(0, 1, 2), guesses = emptyList(), current = listOf(-1, -1, -1))

    @Test
    fun digitsPickOnlyColoursThatExist() {
        assertEquals(TowerKeyAction.Colour(0), towerKeyAction(Key.One, 4))
        assertEquals(TowerKeyAction.Colour(3), towerKeyAction(Key.NumPad4, 4))
        assertNull(towerKeyAction(Key.Five, 4))
        assertNull(towerKeyAction(Key.Zero, 4))
        assertEquals(TowerKeyAction.Submit, towerKeyAction(Key.Enter, 4))
    }

    @Test
    fun colourFillsTheNextEmptyPegBackspaceTakesTheLastAndEnterNeedsAFullRow() {
        var s = empty
        assertNull(s.applyKey(TowerKeyAction.Back))
        assertNull(s.applyKey(TowerKeyAction.Submit))
        s = s.applyKey(TowerKeyAction.Colour(2))!!
        s = s.applyKey(TowerKeyAction.Colour(0))!!
        assertEquals(listOf(2, 0, -1), s.current)
        assertNull(s.applyKey(TowerKeyAction.Submit))
        s = s.applyKey(TowerKeyAction.Colour(1))!!
        assertNull(s.applyKey(TowerKeyAction.Colour(3))) // full: nothing to fill
        s = s.applyKey(TowerKeyAction.Back)!!
        assertEquals(listOf(2, 0, -1), s.current)
        s = s.applyKey(TowerKeyAction.Colour(1))!!.applyKey(TowerKeyAction.Submit)!!
        assertEquals(listOf(listOf(2, 0, 1)), s.guesses)
        assertEquals(listOf(-1, -1, -1), s.current)
    }
}
