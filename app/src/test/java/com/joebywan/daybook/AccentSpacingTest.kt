package com.joebywan.daybook

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.joebywan.daybook.ColourDistance.de2000
import com.joebywan.daybook.ColourDistance.deut
import com.joebywan.daybook.ColourDistance.lab
import com.joebywan.daybook.ColourDistance.prot
import com.joebywan.daybook.ColourDistance.rgb
import com.joebywan.daybook.ColourDistance.trit
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.ui.theme.BoardHues.contrast
import com.joebywan.daybook.ui.theme.DarkScheme
import com.joebywan.daybook.ui.theme.LightScheme
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The puzzle accents were designed as one set (`docs/COLOUR.md`, layer 2): every accent sits in the luminance band where it
 * reaches 3:1 on both schemes' surface and background, away from the error red and the success green, and the set is
 * spread to keep the closest pair as far apart as it can, by CIEDE2000 under normal vision and simulated deuteranopia,
 * protanopia and tritanopia. The floors are just under what the set achieves (normal 5.79, Atoms/Target; colour-blind
 * 2.90, Kings/Who's Who under protanopia), so no new accent can land nearer than the closest pair today.
 */
class AccentSpacingTest {
    private val modes = listOf("normal" to null, "deuteranopia" to deut, "protanopia" to prot, "tritanopia" to trit)
    private val accents = PuzzleRegistry.all.map { it.id to Color(it.accent) }
    private val grounds = listOf(LightScheme.surface, LightScheme.background, DarkScheme.surface, DarkScheme.background)

    /** CIELAB hue (degrees) and chroma. */
    private fun lch(c: Color): Pair<Double, Double> {
        val (_, a, b) = lab(rgb(c)).toList()
        return (Math.toDegrees(atan2(b, a)) + 360) % 360 to hypot(a, b)
    }

    /** Whether [c] keeps the set's rules: 3:1 on every ground, clear of the reserved hues, neither grey nor neon. */
    private fun fits(c: Color): Boolean {
        val (h, chroma) = lch(c)
        return grounds.all { contrast(c, it) >= 3f } && RESERVED.none { (from, to) -> if (from < to) h > from && h < to else h > from || h < to } &&
            chroma in MIN_CHROMA..MAX_CHROMA
    }

    /** The design's distance: normal vision, or twice the worst colour-blind one, whichever is smaller. */
    private fun apart(a: Color, b: Color) = minOf(de2000(a, b, null), 2 * listOf(deut, prot, trit).minOf { de2000(a, b, it) })

    @Test
    fun `every pair of accents is at least the floor apart, colour-blind vision included`() {
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
    fun `every accent keeps the set's rules`() {
        for ((id, c) in accents) assertTrue("$id accent ${lch(c)} breaks the band, the reserved hues or the chroma range", fits(c))
    }

    @Test
    fun `the next accent is the printed candidate`() {
        // Choosing a new puzzle's accent: of the hsl colours that keep the set's rules ([fits]), the one furthest from every
        // accent by [apart]. Copy its hex; the tests above then hold. It may take a hue another puzzle has; the lightness or
        // chroma tells them apart, as it does for several pairs already.
        val best = (0 until 360 step 2).flatMap { h -> (30..90 step 4).flatMap { s -> (30..70 step 2).map { l ->
            Color.hsl(h.toFloat(), s / 100f, l / 100f) } } }.filter(::fits).maxBy { c -> accents.minOf { apart(c, it.second) } }
        val gap = accents.minOf { apart(best, it.second) }
        println("Next accent: 0x%08X, %.1f from the nearest accent".format(best.toArgb(), gap))
        assertTrue(modes.all { (_, m) -> accents.all { de2000(best, it.second, m) >= COLOUR_BLIND_FLOOR } })
        assertTrue(accents.all { de2000(best, it.second, null) >= NORMAL_FLOOR })
    }

    private companion object {
        const val NORMAL_FLOOR = 5.7
        const val COLOUR_BLIND_FLOOR = 2.85

        /**
         * CIELAB hue arcs no accent may take, open at both ends: the error red (Clay, hue 38-39) and the success green
         * (Lexicon's and the Green mark, 152-153), each with room to spare so an accent never reads as right or wrong.
         */
        val RESERVED = listOf(355.0 to 64.0, 125.0 to 179.0)
        const val MIN_CHROMA = 28.0
        const val MAX_CHROMA = 72.0
    }
}
