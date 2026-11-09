package com.joebywan.daybook

import androidx.compose.ui.graphics.Color
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Links
import com.joebywan.daybook.puzzles.LinksLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Links' pair colours, told apart by colour as well as by the number on each end: every pair of the first n colours is
 * at least a set CIEDE2000 apart for normal vision and simulated deuteranopia and protanopia, where n is each tier's
 * most pairs. Written from the CIE formulas ([ColourDistance]), independent of the palette's own construction.
 */
class LinksPaletteTest {
    private fun apart(a: Color, b: Color) = ColourDistance.apart(a, b, tritan = false)

    private fun closest(colours: List<Color>, n: Int) =
        (0 until n).flatMap { i -> (i + 1 until n).map { j -> apart(colours[i], colours[j]) } }.min()

    @Test
    fun `pairs are told apart by colour at every tier's most pairs, colour-blind vision included`() {
        val most = Difficulty.entries.map { LinksLogic.specFor(it).maxPairs }
        val report = most.map { "$it: ${"%.1f".format(closest(Links.palette, it))}" }
        println("Links palette, closest pair by prefix: $report")
        assertTrue(report.toString(), closest(Links.palette, most[0]) >= 14)
        assertTrue(report.toString(), closest(Links.palette, most[1]) >= 13)
        assertTrue(report.toString(), closest(Links.palette, most[2]) >= 8)
    }

    @Test
    fun `there are as many colours as the most pairs, all different`() {
        assertEquals(Difficulty.entries.maxOf { LinksLogic.specFor(it).maxPairs }, Links.palette.size)
        assertEquals(Links.palette.size, Links.palette.toSet().size)
    }
}
