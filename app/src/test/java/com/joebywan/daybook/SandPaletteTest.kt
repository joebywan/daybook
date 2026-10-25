package com.joebywan.daybook

import androidx.compose.ui.graphics.Color
import com.joebywan.daybook.puzzles.Sand
import com.joebywan.daybook.ui.theme.BoardHues
import org.junit.Assert.assertEquals
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.SandLogic
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sand's colours, told apart by colour as well as by their symbol: every pair of the first n colours is at least a set
 * CIEDE2000 apart for normal vision and for simulated deuteranopia and protanopia (Machado 2009, severity 1), where n is
 * each tier's colour count. The formulas are `TowerPaletteTest`'s, written from the CIE definitions.
 */
class SandPaletteTest {
    private fun apart(a: Color, b: Color) = ColourDistance.apart(a, b, tritan = false)

    private fun closest(n: Int) = (0 until n).flatMap { i -> (i + 1 until n).map { j -> apart(Sand.palette[i], Sand.palette[j]) } }.min()

    @Test
    fun `colours are told apart at every tier's count, colour-blind vision included`() {
        // Standard 5 colours, Hard 7, Expert 8; measured 20.0, 15.8, 14.2. The symbol on each layer carries the rest.
        val counts = Difficulty.entries.map { SandLogic.specFor(it).colours }
        assertEquals(listOf(5, 7, 8), counts)
        assertTrue("5: ${closest(5)}", closest(5) >= 19.5)
        assertTrue("7: ${closest(7)}", closest(7) >= 15.5)
        assertTrue("8: ${closest(8)}", closest(8) >= 14)
    }

    @Test
    fun `no colour is Coral or Green, and every one has a name`() {
        val reserved = listOf(8f, 145f).flatMap { listOf(BoardHues.fill(it, false), BoardHues.ink(it, false), BoardHues.mark(it)) }
        for (p in Sand.palette) assertTrue("a colour is a reserved hue", p !in reserved)
        assertEquals(8, Sand.palette.toSet().size)
        assertEquals(Sand.palette.size, Sand.colourNames.toSet().size)
    }
}
