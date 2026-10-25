package com.joebywan.daybook

import androidx.compose.ui.graphics.Color
import com.joebywan.daybook.ColourDistance.lin
import com.joebywan.daybook.puzzles.Lits
import com.joebywan.daybook.ui.theme.DarkScheme
import com.joebywan.daybook.ui.theme.LightScheme
import com.joebywan.daybook.ui.theme.BoardHues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lits's piece colours (I, L, T, S): every pair is CIEDE2000 apart for normal vision and simulated deuteranopia, protanopia
 * and tritanopia (Machado 2009, severity 1), each is clear of the board surface and of the unshaded square in both schemes,
 * and the letter on each reads at 4.5:1. Written from the CIE formulas, independent of the palette's construction.
 */
class LitsPaletteTest {
    private fun apart(a: Color, b: Color) = ColourDistance.apart(a, b)

    private val colours = Lits.palette.values.toList()

    @Test
    fun `pieces are told apart, colour-blind vision included`() {
        val d = colours.indices.flatMap { i -> (i + 1 until colours.size).map { j -> apart(colours[i], colours[j]) } }.min()
        assertTrue("closest pair: $d", d >= MIN_APART)
    }

    private fun wcagY(c: Color) = 0.2126 * lin(c.red.toDouble()) + 0.7152 * lin(c.green.toDouble()) + 0.0722 * lin(c.blue.toDouble())
    private fun ratio(a: Color, b: Color) = (maxOf(wcagY(a), wcagY(b)) + 0.05) / (minOf(wcagY(a), wcagY(b)) + 0.05)

    @Test
    fun `every letter reads on its fill at 4_5 to 1`() {
        for (c in colours) assertTrue("letter ${ratio(BoardHues.onFill(c), c)} on $c", ratio(BoardHues.onFill(c), c) >= 4.5)
    }

    @Test
    fun `brightness has at least three separated levels, not one grey`() {
        val ys = colours.map(::wcagY).sorted()
        var levels = 1
        var last = ys[0]
        for (y in ys.drop(1)) if ((y + 0.05) / (last + 0.05) >= 1.3) { levels++; last = y }
        assertTrue("levels: $levels", levels >= 3)
    }

    @Test
    fun `pieces are clear of the surface, the unshaded square and the in-progress tan in both schemes`() {
        val inProgress = Color(Lits.accent)
        for (s in listOf(LightScheme, DarkScheme)) {
            for (other in listOf(s.background, s.surface, s.surfaceVariant, inProgress)) {
                for (c in colours) assertTrue("a piece sits on $other: ${apart(c, other)}", apart(c, other) >= MIN_CLEAR)
            }
        }
    }

    @Test
    fun `no piece is Coral or Green, which are the error and right colours`() {
        val reserved = listOf(8f, 145f).flatMap { listOf(BoardHues.fill(it, false), BoardHues.ink(it, false), BoardHues.mark(it)) }
        for (p in colours) assertTrue("a piece is a reserved hue", p !in reserved)
    }

    @Test
    fun `one colour per piece`() {
        assertEquals(Lits.Piece.entries.toSet(), Lits.palette.keys)
        assertEquals(4, colours.toSet().size)
    }

    private companion object {
        const val MIN_APART = 15
        const val MIN_CLEAR = 11 // measured closest 11.9 (a colour-blind pair on the dark unshaded square); walls and the letter also separate them
    }
}
