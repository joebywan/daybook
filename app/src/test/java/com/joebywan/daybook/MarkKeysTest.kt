package com.joebywan.daybook

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Kings
import com.joebywan.daybook.puzzles.KingsKeyAction
import com.joebywan.daybook.puzzles.KingsState
import com.joebywan.daybook.puzzles.Lits
import com.joebywan.daybook.puzzles.LitsState
import com.joebywan.daybook.puzzles.Mark
import com.joebywan.daybook.puzzles.Mosaic
import com.joebywan.daybook.puzzles.MosaicKeyAction
import com.joebywan.daybook.puzzles.MosaicState
import com.joebywan.daybook.puzzles.applyKey
import com.joebywan.daybook.puzzles.fillKey
import com.joebywan.daybook.puzzles.kingsKeyAction
import com.joebywan.daybook.puzzles.lay
import com.joebywan.daybook.puzzles.litsToggleKey
import com.joebywan.daybook.puzzles.mosaicKeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kings, LITS and Mosaic: what their keys map to and the states they end in. */
class MarkKeysTest {
    @Test
    fun kingsSpacePencilsKCrownsBackspaceEmpties() {
        val s = Kings.generate(3, Difficulty.STANDARD) as KingsState
        assertEquals(KingsKeyAction.Pencil, kingsKeyAction(Key.Spacebar))
        assertEquals(KingsKeyAction.Crown, kingsKeyAction(Key.K))
        assertNull(kingsKeyAction(Key.Q))
        val pencilled = s.applyKey(5, KingsKeyAction.Pencil)!!
        assertEquals(Mark.BLOCKED, pencilled.marks[5])
        assertEquals(Mark.EMPTY, pencilled.applyKey(5, KingsKeyAction.Pencil)!!.marks[5])
        val crowned = pencilled.applyKey(5, KingsKeyAction.Crown)!! // a crown outranks a pencil mark
        assertEquals(Mark.KING, crowned.marks[5])
        assertNull(crowned.applyKey(5, KingsKeyAction.Pencil)) // a pencil key leaves a king alone
        assertEquals(Mark.EMPTY, crowned.applyKey(5, KingsKeyAction.Clear)!!.marks[5])
        assertNull(s.applyKey(5, KingsKeyAction.Clear))
    }

    @Test
    fun litsToggleAndShiftLay() {
        val s = Lits.generate(4, Difficulty.STANDARD) as LitsState
        assertTrue(litsToggleKey(Key.Spacebar))
        assertFalse(litsToggleKey(Key.A))
        val on = s.toggle(0)
        assertTrue(on.shaded[0])
        val laid = on.lay(0, 1)!!
        assertTrue(laid.shaded[1])
        assertNull(laid.lay(0, 1)) // already matches: no undo entry
        assertNotNull(laid.lay(2, 1)) // an unshaded square rubs out the one it lands on
        assertFalse(laid.lay(2, 1)!!.shaded[1])
    }

    @Test
    fun mosaicDigitsPickOnlyColoursThatExistAndSpacePoursOnlyWhatChanges() {
        val s = Mosaic.generate(5, Difficulty.STANDARD) as MosaicState
        assertEquals(MosaicKeyAction.Pick(0), mosaicKeyAction(Key.One, s.colours))
        assertEquals(MosaicKeyAction.Fill, mosaicKeyAction(Key.Enter, s.colours))
        assertNull(mosaicKeyAction(Key.Nine, 5))
        assertNull(mosaicKeyAction(Key.Zero, 5))
        assertNull(s.fillKey(0, s.cells[0]))
        val other = (0 until s.colours).first { it != s.cells[0] }
        val poured = s.fillKey(0, other)!!
        assertEquals(other, poured.cells[0])
        assertEquals(s.moves + 1, poured.moves)
    }
}
