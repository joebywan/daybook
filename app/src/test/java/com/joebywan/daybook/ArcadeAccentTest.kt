package com.joebywan.daybook

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.joebywan.daybook.ColourDistance.de2000
import com.joebywan.daybook.ColourDistance.deut
import com.joebywan.daybook.ColourDistance.lab
import com.joebywan.daybook.ColourDistance.prot
import com.joebywan.daybook.ColourDistance.rgb
import com.joebywan.daybook.ColourDistance.trit
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.ui.theme.ARCADE_ACCENTS
import com.joebywan.daybook.ui.theme.ArcadeBoard
import com.joebywan.daybook.ui.theme.BoardHues
import com.joebywan.daybook.ui.theme.BoardHues.contrast
import com.joebywan.daybook.ui.theme.DarkScheme
import com.joebywan.daybook.ui.theme.LightScheme
import com.joebywan.daybook.ui.theme.Look
import com.joebywan.daybook.ui.theme.arcadeAccent
import com.joebywan.daybook.ui.theme.useLook
import com.joebywan.daybook.ui.theme.useVividScheme
import com.joebywan.daybook.ui.theme.vivid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2

/**
 * Arcade's drawn accents (`ARCADE_ACCENTS`, docs/COLOUR.md) are a set per scheme, as the raw accents are (`AccentSpacingTest`):
 * every puzzle has a pair, each reaches 3:1 where it is drawn, stays out of the reserved hues and off the error red and the
 * success green, and the closest pair is at least the floor. Floors just under what the solve reached (4.56 light, 8.14 dark).
 */
class ArcadeAccentTest {
    private val raw = (PuzzleRegistry.all + PuzzleRegistry.lab).map { it.accent }.distinct()

    /** The design's distance: normal vision, or twice the worst colour-blind one, whichever is smaller. */
    private fun apart(a: Color, b: Color) = minOf(de2000(a, b, null), 2 * listOf(deut, prot, trit).minOf { de2000(a, b, it) })

    private fun hue(c: Color): Double {
        val (_, a, b) = lab(rgb(c)).toList()
        return (Math.toDegrees(atan2(b, a)) + 360) % 360
    }

    @Test fun `every puzzle has a pair, and vivid draws it in Arcade`() {
        useLook(Look.ARCADE)
        for (dark in listOf(false, true)) {
            useVividScheme(dark)
            for (a in raw) {
                assertTrue("%08X has no Arcade pair".format(a.toInt()), a in ARCADE_ACCENTS)
                assertEquals(arcadeAccent(a, dark), vivid(a))
            }
        }
        useVividScheme(false)
    }

    @Test fun `each scheme's set reads, keeps clear of right and wrong, and is spread`() {
        for ((dark, s, floor) in listOf(Triple(false, LightScheme, 4.5), Triple(true, DarkScheme, 8.0))) {
            val set = raw.map { it to arcadeAccent(it, dark) }
            val green = Color.hsl(145f, .8f, .56f)
            for ((a, c) in set) {
                val name = "%08X dark=$dark".format(a.toInt())
                assertTrue("$name under 3:1 on the surface", contrast(c, s.surface) >= 3f)
                assertTrue("$name under 3:1 on the background", contrast(c, s.background) >= 3f)
                val h = hue(c)
                assertTrue("$name in a reserved hue ($h)", !(h > 355 || h < 64 || (h > 125 && h < 179)))
                assertTrue("$name near the error red", apart(c, s.error) >= 12.0)
                assertTrue("$name near the success green", apart(c, green) >= 12.0)
                // What the app draws from an accent still reads: text on the page, text on its tint, a label on it.
                for (ground in listOf(s.surface, s.background)) {
                    assertTrue("$name text", contrast(BoardHues.textOn(c, ground), ground) >= 4.5f)
                    assertTrue("$name on its tint", contrast(BoardHues.onTint(c, ground), c.copy(alpha = .22f).compositeOver(ground)) >= 4.5f)
                }
                assertTrue("$name button label", contrast(BoardHues.onFill(c), c) >= 4.5f)
                // The finish frame's banner and the results pill are the tile made from the drawn accent.
                val banner = ArcadeBoard.arcadeTile(c, dark)
                for (stop in listOf(banner.from, banner.to)) assertTrue("$name banner ink", contrast(banner.ink, stop) >= 4.5f)
            }
            // The hint's glow and the hint card's edge stand off the board's squares and the page.
            val b = if (dark) ArcadeBoard.Dark else ArcadeBoard.Light
            for (ground in listOf(b.square, s.background, s.surface)) {
                assertTrue("hint on $ground, dark=$dark", contrast(b.hint, ground) >= 3f)
            }
            for (i in set.indices) for (j in i + 1 until set.size) {
                val d = apart(set[i].second, set[j].second)
                assertTrue("%08X and %08X are %.2f apart, dark=$dark, floor $floor".format(set[i].first.toInt(), set[j].first.toInt(), d), d >= floor)
            }
        }
    }
}
