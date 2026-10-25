package com.joebywan.daybook

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.joebywan.daybook.ColourDistance.de2000
import com.joebywan.daybook.ColourDistance.deut
import com.joebywan.daybook.ColourDistance.prot
import com.joebywan.daybook.ColourDistance.trit
import com.joebywan.daybook.core.PuzzleRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every pair of puzzle accents is at least a floor apart by CIEDE2000, under normal vision and under simulated
 * deuteranopia, protanopia and tritanopia (`docs/COLOUR.md`, layer 2). The floors sit just under today's closest pairs,
 * so they stop a new accent landing nearer than any existing one; they do not make today's pairs distinct (the name and
 * motif on every card carry the puzzle, the accent only tints it).
 */
class AccentSpacingTest {
    private val modes = listOf("normal" to null, "deuteranopia" to deut, "protanopia" to prot, "tritanopia" to trit)
    private val accents = PuzzleRegistry.all.map { it.id to Color(it.accent) }

    private fun nearest(c: Color, m: Array<DoubleArray>?) = accents.minOf { de2000(c, it.second, m) }

    @Test
    fun `every pair of accents is at least the floor apart, colour-blind vision included`() {
        // Measured closest: normal 1.59 (regions, target); deuteranopia 1.21 (regions, target); protanopia 1.20
        // (shikaku, words); tritanopia 1.87 (sand, pointers).
        for ((name, m) in modes) {
            val floor = if (m == null) NORMAL_FLOOR else COLOUR_BLIND_FLOOR
            for (i in accents.indices) for (j in i + 1 until accents.size) {
                val (a, ca) = accents[i]
                val (b, cb) = accents[j]
                val d = de2000(ca, cb, m)
                assertTrue("$a and $b accents are $d apart under $name vision, floor $floor", d >= floor)
            }
        }
    }

    @Test
    fun `the next accent is the printed candidate`() {
        // Choosing a new puzzle's accent: the hsl in the established box (s .37-.73, l .45-.67) furthest from every
        // accent, the worst of the four vision modes. Copy its hex; the test above then holds with room to spare. Hues
        // within 25 degrees of red are left out: an accent that reads as the error colour would look like a broken rule.
        val best = (0 until 360 step 2).filter { it in 25..335 }.flatMap { h -> (37..73 step 4).flatMap { s -> (45..67 step 4).map { l ->
            Color.hsl(h.toFloat(), s / 100f, l / 100f) } } }.maxBy { c -> modes.minOf { nearest(c, it.second) } }
        val gap = modes.minOf { nearest(best, it.second) }
        println("Next accent: 0x%08X, %.1f from the nearest accent".format(best.toArgb(), gap))
        assertTrue(gap >= COLOUR_BLIND_FLOOR)
    }

    private companion object {
        const val NORMAL_FLOOR = 1.5
        const val COLOUR_BLIND_FLOOR = 1.1
    }
}
