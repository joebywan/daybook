package com.joebywan.daybook

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.ui.theme.ArcadeBoard
import com.joebywan.daybook.ui.theme.BoardHues
import com.joebywan.daybook.ui.theme.DarkScheme
import com.joebywan.daybook.ui.theme.LightScheme
import com.joebywan.daybook.ui.theme.Look
import com.joebywan.daybook.ui.theme.arcadePageBrush
import com.joebywan.daybook.ui.theme.classicAccent
import com.joebywan.daybook.ui.theme.classicLook
import com.joebywan.daybook.ui.theme.lookScheme
import com.joebywan.daybook.ui.theme.vivid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Classic look (issue #418) is what the app drew before Arcade (`3369cc46^`): the switches in `ui/theme` hand back the
 * old schemes, the raw accents and the old recipes, and Arcade's are untouched beside them.
 */
class ClassicLookTest {
    @Test fun `Classic's schemes are the parchment ones from before Arcade`() = forEachLook { look ->
        val light = lookScheme(false)
        val dark = lookScheme(true)
        if (look == Look.CLASSIC) {
            assertEquals(Color(0xFFF6F1E7), light.background)
            assertEquals(Color(0xFFFFFBF2), light.surface)
            assertEquals(Color(0xFF1F6F5C), light.primary)
            assertEquals(Color(0xFF11201C), dark.background)
            assertEquals(Color(0xFF1B2F29), dark.surface)
            assertEquals(Color(0xFF3F9E7F), dark.primary)
        } else {
            assertSame(LightScheme, light)
            assertSame(DarkScheme, dark)
        }
    }

    @Test fun `in Classic an accent is drawn raw, as before Arcade, wherever the raw one reads`() = forEachLook { look ->
        for (p in PuzzleRegistry.all) {
            if (look == Look.CLASSIC) {
                for (dark in listOf(false, true)) {
                    val s = lookScheme(dark)
                    val reads = listOf(s.surface, s.background).all { BoardHues.contrast(Color(p.accent), it) >= 3f }
                    if (reads) assertEquals(p.id, Color(p.accent), classicAccent(p.accent, dark))
                }
                // Every accent from before Arcade reads raw on the light scheme.
                assertEquals(p.id, Color(p.accent), classicAccent(p.accent, false))
            } else {
                assertNotEquals(p.id, Color(p.accent), vivid(p.accent))
            }
        }
    }

    @Test fun `in Classic the board hues are the old recipes`() = forEachLook { look ->
        for (h in BoardHues.all) for (dark in listOf(false, true)) {
            if (look == Look.CLASSIC) {
                assertEquals(BoardHues.pinnedFill(h, dark), BoardHues.fill(h, dark))
                assertEquals(BoardHues.pinnedMark(h), BoardHues.mark(h))
                // Ink at saturation .55, stepped against the old schemes: Blue's light ink needs no step, hsl(215, .55, .38).
                if (h == 215f && !dark) assertEquals(Color.hsl(215f, .55f, .38f), BoardHues.ink(h, false))
            } else {
                assertNotEquals(BoardHues.pinnedMark(h), BoardHues.mark(h))
            }
        }
        // The first ink a fill's digit tries is the scheme's own dark ink.
        assertEquals(if (look == Look.CLASSIC) Color(0xFF1B2F29) else Color(0xFF21145F), BoardHues.onFill(Color.White))
    }

    @Test fun `in Classic the shared tokens are flat, the page plain and a home tile the surface`() = forEachLook { look ->
        for (dark in listOf(false, true)) {
            val tokens = ArcadeBoard.of(dark)
            val tile = ArcadeBoard.tile(Color(0xFF6F7DF7), dark)
            val page = arcadePageBrush(dark, Size(100f, 200f))
            if (look == Look.CLASSIC) {
                assertTrue(classicLook())
                assertSame(if (dark) ArcadeBoard.ClassicDark else ArcadeBoard.ClassicLight, tokens)
                assertEquals(lookScheme(dark).surface, tile.from)
                assertEquals(tile.from, tile.to)
                assertEquals(SolidColor(lookScheme(dark).background), page)
            } else {
                assertFalse(classicLook())
                assertSame(if (dark) ArcadeBoard.Dark else ArcadeBoard.Light, tokens)
                assertNotEquals(tile.from, tile.to)
                assertFalse(page is SolidColor)
            }
        }
    }
}
