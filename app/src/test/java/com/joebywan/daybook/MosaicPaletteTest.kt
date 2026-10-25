package com.joebywan.daybook

import androidx.compose.ui.graphics.Color
import com.joebywan.daybook.puzzles.Mosaic
import com.joebywan.daybook.puzzles.MosaicTeacher
import com.joebywan.daybook.ui.theme.DarkScheme
import com.joebywan.daybook.ui.theme.LightScheme
import com.joebywan.daybook.ui.theme.BoardHues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mosaic's flood colours: every pair of the first n (3 / 4 / 5 by tier) is a set CIEDE2000 apart for normal vision and
 * simulated deuteranopia, protanopia and tritanopia (Machado 2009, severity 1), and each is clear of the board surface in both
 * schemes. Written from the CIE formulas, independent of the palette's own construction. The digit on every area
 * carries what the five-colour colour-blind distance cannot.
 */
class MosaicPaletteTest {
    private fun closest(n: Int) = (0 until n).flatMap { i -> (i + 1 until n).map { j -> apart(Mosaic.palette[i], Mosaic.palette[j]) } }.min()

    @Test
    fun `colours are told apart at every tier's count, colour-blind vision included`() {
        // Standard 3, Hard 4, Expert 5; measured 20.7, 15.8, 15.8 (min over normal, deut, prot, trit).
        assertTrue("3 colours: ${closest(3)}", closest(3) >= 20)
        assertTrue("4 colours: ${closest(4)}", closest(4) >= 15)
        assertTrue("5 colours: ${closest(5)}", closest(5) >= 15)
    }

    private fun wcagY(c: Color) = 0.2126 * lin(c.red.toDouble()) + 0.7152 * lin(c.green.toDouble()) + 0.0722 * lin(c.blue.toDouble())
    private fun ratio(a: Color, b: Color) = (maxOf(wcagY(a), wcagY(b)) + 0.05) / (minOf(wcagY(a), wcagY(b)) + 0.05)

    @Test
    fun `every digit reads on its fill at 4_5 to 1`() {
        // Measured: Amber 8.8 (ink), Teal 5.9 (black), Violet 5.1 (white), Rose 7.4 (white), Blue 7.2 (ink).
        for (c in Mosaic.palette) {
            val digit = BoardHues.onFill(c)
            assertTrue("digit ${ratio(digit, c)} on $c", ratio(digit, c) >= 4.5)
        }
    }

    @Test
    fun `brightness has at least three separated levels, not one grey`() {
        // Sorted by luminance, count a new level whenever the step from the last one is at least 1.3:1.
        // Measured: rose .09, violet .16 (1.47), teal .24 (1.41), blue .49 (1.83); amber .60 sits with blue (1.22).
        val ys = Mosaic.palette.map(::wcagY).sorted()
        var levels = 1
        var last = ys[0]
        for (y in ys.drop(1)) if ((y + 0.05) / (last + 0.05) >= 1.3) { levels++; last = y }
        assertTrue("levels: $levels", levels >= 3)
    }

    @Test
    fun `every colour is clear of the board surface in both schemes`() {
        // Measured closest 13.8 (Amber on parchment, colour-blind); areas also carry heavy outlines, so this is a floor.
        for (surface in listOf(LightScheme.background, DarkScheme.background, LightScheme.surface, DarkScheme.surface)) {
            for (c in Mosaic.palette) assertTrue("a colour sits on the surface: ${apart(c, surface)}", apart(c, surface) >= 12)
        }
    }

    @Test
    fun `no colour is Coral or Green, which are the error and right colours`() {
        val reserved = listOf(8f, 145f).flatMap { listOf(BoardHues.fill(it, false), BoardHues.ink(it, false), BoardHues.mark(it)) }
        for (p in Mosaic.palette) assertTrue("a colour is a reserved hue", p !in reserved)
    }

    @Test
    fun `every colour has a name, index for index`() {
        assertEquals(5, Mosaic.palette.size)
        assertEquals(5, Mosaic.palette.toSet().size)
        assertEquals(Mosaic.palette.size, MosaicTeacher.colourNames.size)
    }
}
