package com.joebywan.daybook

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.puzzles.Lexicon
import com.joebywan.daybook.puzzles.LexiconMark
import com.joebywan.daybook.ui.home.tileInk
import com.joebywan.daybook.ui.theme.BoardHues
import com.joebywan.daybook.ui.theme.BoardHues.contrast
import com.joebywan.daybook.ui.theme.DarkScheme
import com.joebywan.daybook.ui.theme.LightScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG contrast of the colours drawn as text (4.5:1) or as a small mark (3:1), in both schemes, so none can regress. */
class ContrastTest {
    private val schemes = listOf("light" to LightScheme, "dark" to DarkScheme)

    private fun atLeast(ratio: Float, fg: Color, bg: Color, what: String) {
        val c = contrast(fg, bg)
        assertTrue("$what is $c:1, needs $ratio", c >= ratio)
    }

    private fun ColorScheme.grounds() = listOf("surface" to surface, "background" to background)

    @Test fun `every accent drawn as text reads on what it sits on`() {
        for ((name, s) in schemes) for (p in PuzzleRegistry.all) {
            val accent = Color(p.accent)
            for ((g, bg) in s.grounds()) atLeast(4.5f, BoardHues.textOn(accent, bg), bg, "${p.id} accent text on $name $g")
            // A home tile's name and footer, on the tile tinted with the accent (the harder ground) and on the plain one.
            val tinted = accent.copy(alpha = 0.20f).compositeOver(s.surface)
            atLeast(4.5f, tileInk(accent, s.surface), tinted, "${p.id} tile name on $name solved tile")
            atLeast(4.5f, tileInk(accent, s.surface), s.surface, "${p.id} tile name on $name tile")
            atLeast(3f, BoardHues.textOn(accent, s.surface, 3f), s.surface, "${p.id} tier dot on $name surface")
        }
    }

    @Test fun `a colour that already reads is left alone`() {
        assertEquals(LightScheme.onSurface, BoardHues.textOn(LightScheme.onSurface, LightScheme.surface))
    }

    @Test fun `board ink reads on the surface and background for every hue`() {
        for ((name, s) in schemes) for (h in BoardHues.all) {
            val ink = BoardHues.ink(h, name == "dark")
            for ((g, bg) in s.grounds()) atLeast(4.5f, ink, bg, "ink of hue $h on $name $g")
        }
    }

    @Test fun `error text reads, and so does text on an error fill`() {
        for ((name, s) in schemes) {
            for ((g, bg) in s.grounds()) atLeast(4.5f, s.error, bg, "error on $name $g")
            atLeast(4.5f, s.onError, s.error, "onError on $name error")
            atLeast(4.5f, BoardHues.onFill(s.error), s.error, "Tally's red chip text, $name")
        }
    }

    @Test fun `Lexicon letters read on green and gold tiles`() {
        atLeast(4.5f, Lexicon.onMark(LexiconMark.CORRECT), Lexicon.correctColour, "letter on a green tile")
        atLeast(4.5f, Lexicon.onMark(LexiconMark.PRESENT), Lexicon.presentColour, "letter on a gold tile")
    }
}
