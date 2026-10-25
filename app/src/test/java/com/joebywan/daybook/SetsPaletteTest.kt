package com.joebywan.daybook

import androidx.compose.ui.graphics.Color
import com.joebywan.daybook.ColourDistance.lab
import com.joebywan.daybook.ColourDistance.lin
import com.joebywan.daybook.ColourDistance.rgb
import com.joebywan.daybook.puzzles.Card
import com.joebywan.daybook.puzzles.Sets
import com.joebywan.daybook.puzzles.SetsTeacher
import com.joebywan.daybook.ui.theme.BoardHues
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sets' three card colours, told apart by colour as well as by the card's other three traits: every pair is at least a
 * set CIEDE2000 apart for normal vision and for simulated deuteranopia and protanopia (Machado 2009, severity 1), and
 * each is clear of the card surface in both schemes. Written from the CIE formulas, independent of the palette's own
 * construction.
 */
class SetsPaletteTest {
    private fun apart(a: Color, b: Color) = ColourDistance.apart(a, b)

    // WCAG 2 contrast ratio from relative luminance.
    private fun luminance(c: Color): Double = rgb(c).map(::lin).let { 0.2126 * it[0] + 0.7152 * it[1] + 0.0722 * it[2] }
    private fun contrast(a: Color, b: Color) = (maxOf(luminance(a), luminance(b)) + 0.05) / (minOf(luminance(a), luminance(b)) + 0.05)

    // The card surface of ui/theme/Palette.kt: light, then dark.
    private val surfaces = listOf(Color(0xFFFFFBF2) to false, Color(0xFF1B2F29) to true)

    @Test
    fun `the three card colours are far apart in both schemes, all three kinds of colour-blindness included`() {
        // Measured min: light 24.2, dark 20.3 (colour-blind distances are the binding ones).
        for ((_, dark) in surfaces) {
            val p = Sets.palette(dark)
            for (i in 0 until 3) for (j in i + 1 until 3) {
                val d = apart(p[i], p[j])
                assertTrue("dark=$dark colours $i and $j: $d", d >= 20)
            }
        }
    }

    @Test
    fun `each card colour is at least 3 to 1 against the card surface in both schemes`() {
        // Measured min: light 3.47 (Teal), dark 3.91 (Violet).
        for ((surface, dark) in surfaces) for (c in Sets.palette(dark)) {
            val r = contrast(c, surface)
            assertTrue("dark=$dark: only $r:1", r >= 3.0)
        }
    }

    @Test
    fun `the colours differ in lightness as well as hue, so no two collapse to one grey`() {
        // Amber and Teal are the close pair (1.36 light, 1.24 dark); Violet is clear of both.
        for ((_, dark) in surfaces) {
            val p = Sets.palette(dark)
            for (i in 0 until 3) for (j in i + 1 until 3) {
                val dl = abs(lab(rgb(p[i]))[0] - lab(rgb(p[j]))[0])
                assertTrue("dark=$dark colours $i and $j: dL* $dl", dl >= 6)
            }
        }
    }

    @Test
    fun `no card colour is a Coral or Green step, which are the right and wrong colours`() {
        val reserved = listOf(8f, 145f).flatMap { h ->
            listOf(false, true).flatMap { d -> listOf(BoardHues.fill(h, d), BoardHues.ink(h, d), BoardHues.mark(h)) }
        }
        for ((_, dark) in surfaces) for (p in Sets.palette(dark)) assertTrue("a card colour is a reserved hue", p !in reserved)
    }

    @Test
    fun `three distinct colours, named in the teacher index for index`() {
        for ((_, dark) in surfaces) assertEquals(3, Sets.palette(dark).toSet().size)
        val names = listOf("amber", "teal", "violet")
        for (i in 0 until 3) {
            val words = SetsTeacher.describe(Card(count = 0, shape = 0, shading = 0, colour = i)).split(" ")
            assertEquals(names[i], words[2])
        }
    }
}

