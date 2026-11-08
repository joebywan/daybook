package com.joebywan.daybook

import androidx.compose.ui.graphics.Color
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.puzzles.Links
import com.joebywan.daybook.puzzles.LinksLogic
import com.joebywan.daybook.ui.theme.BoardHues
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
        assertTrue(report.toString(), closest(Links.palette, most[0]) >= 15)
        assertTrue(report.toString(), closest(Links.palette, most[1]) >= 11)
        assertTrue(report.toString(), closest(Links.palette, most[2]) >= 6)
    }

    @Test
    fun `there are as many colours as the most pairs, all different`() {
        assertEquals(Difficulty.entries.maxOf { LinksLogic.specFor(it).maxPairs }, Links.palette.size)
        assertEquals(Links.palette.size, Links.palette.toSet().size)
    }

    /** Greedy: from the hue family's three fixed steps, each next colour the one furthest from all before it. Printed, to choose from. */
    @Test
    fun `print a greedy order`() {
        val hues = listOf(8f, 34f, 46f, 145f, 175f, 215f, 268f, 330f)
        val pool = hues.flatMap { h -> listOf("fill $h" to BoardHues.pinnedFill(h, false), "mark $h" to BoardHues.pinnedMark(h), "deep $h" to BoardHues.deep(h, false)) }
        val chosen = mutableListOf(pool.first { it.first == "mark 215.0" })
        while (chosen.size < 12) {
            val next = pool.filter { it !in chosen }.maxBy { c -> chosen.minOf { apart(it.second, c.second) } }
            chosen += next
        }
        println("greedy: ${chosen.map { it.first }} closest ${(5..12).map { "%.1f".format(closest(chosen.map { it.second }, it)) }}")
    }
}
