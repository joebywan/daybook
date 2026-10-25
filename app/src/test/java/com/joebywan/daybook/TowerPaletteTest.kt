package com.joebywan.daybook

import androidx.compose.ui.graphics.Color
import com.joebywan.daybook.puzzles.Tower
import com.joebywan.daybook.puzzles.TowerTeacher
import com.joebywan.daybook.ui.theme.BoardHues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tower's pegs, told apart by colour as well as by their digit: every pair of the first n pegs is at least a set
 * CIEDE2000 apart for normal vision and for simulated deuteranopia and protanopia (Machado 2009, severity 1), where
 * n is each tier's colour count. Written from the CIE formulas, independent of the palette's own construction.
 */
class TowerPaletteTest {
    private fun closest(n: Int) = (0 until n).flatMap { i -> (i + 1 until n).map { j -> apart(Tower.palette[i], Tower.palette[j], tritanopia = false) } }.min()

    @Test
    fun `pegs are told apart by colour at every tier's count, colour-blind vision included`() {
        // Standard 5 colours, Hard 6, Expert 8; measured 21.2, 17.4, 11.3. The digit on each peg carries the rest.
        assertTrue("5 pegs: ${closest(5)}", closest(5) >= 20)
        assertTrue("6 pegs: ${closest(6)}", closest(6) >= 16)
        assertTrue("8 pegs: ${closest(8)}", closest(8) >= 11)
    }

    @Test
    fun `no peg is Coral or Green, which are the right and wrong colours`() {
        val reserved = listOf(8f, 145f).flatMap { listOf(BoardHues.fill(it, false), BoardHues.ink(it, false), BoardHues.mark(it)) }
        for (p in Tower.palette) assertTrue("a peg is a reserved hue", p !in reserved)
    }

    @Test
    fun `every peg has a distinct colour and a name, index for index`() {
        assertEquals(8, Tower.palette.size)
        assertEquals(8, Tower.palette.toSet().size)
        assertEquals(Tower.palette.size, TowerTeacher.colourNames.size)
    }
}
