package com.joebywan.daybook

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.movedCursor
import com.joebywan.daybook.puzzles.Mambo
import com.joebywan.daybook.puzzles.MamboKeyAction
import com.joebywan.daybook.puzzles.MamboState
import com.joebywan.daybook.puzzles.Sym
import com.joebywan.daybook.puzzles.applyKey
import com.joebywan.daybook.puzzles.mamboKeyAction
import com.joebywan.daybook.puzzles.pipesRotateKey
import com.joebywan.daybook.puzzles.setsPickKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure key mappings of the cursor boards that need little logic of their own. */
class GridKeysTest {
    @Test
    fun movedCursorStartsAtZeroClampsAndIgnoresOtherKeys() {
        assertEquals(0, movedCursor(null, Key.DirectionRight, 3, 4))
        assertEquals(11, movedCursor(11, Key.DirectionDown, 3, 4))
        assertEquals(5, movedCursor(4, Key.DirectionRight, 3, 4))
        assertNull(movedCursor(4, Key.Spacebar, 3, 4))
    }

    @Test
    fun mamboKeysCycleAndPutButNeverTouchAGiven() {
        val s = Mambo.generate(7, Difficulty.STANDARD) as MamboState
        val given = s.givens.indexOf(true)
        val free = s.givens.indexOf(false)
        assertNull(s.applyKey(given, MamboKeyAction.Cycle))
        assertNull(s.applyKey(given, MamboKeyAction.Put(Sym.SUN)))
        val first = s.applyKey(free, mamboKeyAction(Key.Spacebar)!!)!!
        assertEquals(Sym.MOON, first.cells[free])
        assertEquals(Sym.SUN, first.applyKey(free, MamboKeyAction.Cycle)!!.cells[free])
        assertNull(first.applyKey(free, mamboKeyAction(Key.M)!!)) // already a moon: no undo entry
        assertEquals(Sym.SUN, first.applyKey(free, mamboKeyAction(Key.S)!!)!!.cells[free])
        assertEquals(Sym.NONE, first.applyKey(free, mamboKeyAction(Key.Backspace)!!)!!.cells[free])
        assertNull(mamboKeyAction(Key.Q))
    }

    @Test
    fun pipesAndSetsKeys() {
        assertTrue(pipesRotateKey(Key.R))
        assertTrue(pipesRotateKey(Key.Spacebar))
        assertFalse(pipesRotateKey(Key.M))
        assertTrue(setsPickKey(Key.Enter))
        assertFalse(setsPickKey(Key.R))
    }
}
