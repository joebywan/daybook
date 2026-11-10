package com.joebywan.daybook

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.puzzles.GuessBoard
import com.joebywan.daybook.ui.home.tileInk
import com.joebywan.daybook.ui.theme.BoardHues
import com.joebywan.daybook.ui.theme.BoardHues.contrast
import com.joebywan.daybook.ui.home.classicTileInk
import com.joebywan.daybook.ui.theme.LightScheme
import com.joebywan.daybook.ui.theme.Look
import com.joebywan.daybook.ui.theme.classicAccent
import com.joebywan.daybook.ui.theme.classicLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG contrast of the colours drawn as text (4.5:1) or as a small mark (3:1), in both schemes of both looks (Classic and
 * Arcade, issue #418), so none can regress.
 */
class ContrastTest {
    /** Each test runs in every look over that look's two schemes. */
    private fun schemes(block: (name: String, s: ColorScheme) -> Unit) = forEachLook { look ->
        for ((name, s) in lookSchemes(look)) block(name, s)
    }

    /** A home tile's ink in the look showing: Classic's tint ink or Arcade's surface-mixed one. */
    private fun homeInk(accent: Color, surface: Color) = if (classicLook()) classicTileInk(accent, surface) else tileInk(accent, surface)

    private fun atLeast(ratio: Float, fg: Color, bg: Color, what: String) {
        val c = contrast(fg, bg)
        assertTrue("$what is $c:1, needs $ratio", c >= ratio)
    }

    private fun ColorScheme.grounds() = listOf("surface" to surface, "background" to background)

    @Test fun `every accent drawn as text reads on what it sits on`() {
        schemes { name, s ->
            for (p in PuzzleRegistry.all) {
                val accent = Color(p.accent)
                for ((g, bg) in s.grounds()) atLeast(4.5f, BoardHues.textOn(accent, bg), bg, "${p.id} accent text on $name $g")
                // A home tile's name and footer, on the tile tinted with the accent (the harder ground) and on the plain one.
                val tinted = accent.copy(alpha = 0.20f).compositeOver(s.surface)
                atLeast(4.5f, homeInk(accent, s.surface), tinted, "${p.id} tile name on $name solved tile")
                atLeast(4.5f, homeInk(accent, s.surface), s.surface, "${p.id} tile name on $name tile")
                atLeast(3f, BoardHues.textOn(accent, s.surface, 3f), s.surface, "${p.id} tier dot on $name surface")
            }
        }
    }

    @Test fun `every raw accent reads as a mark on both schemes`() {
        // UI marks drawn in the raw accent (icons, rings, board strokes, Levels' dots, a selected pen's fill): 3:1, WCAG's
        // floor for graphics and large text, on the surface and the background of both schemes. This is the band the set
        // was designed in (relative luminance 0.18-0.255), so any accent works anywhere a mark goes without a lookup.
        // Arcade: the raw accent itself. Classic: the accent it draws ([classicAccent]), raw for every accent from before Arcade.
        forEachLook { look ->
            for ((name, s) in lookSchemes(look)) for (p in PuzzleRegistry.all) for ((g, bg) in s.grounds()) {
                val drawn = if (look == Look.CLASSIC) classicAccent(p.accent, BoardHues.isDark(s)) else Color(p.accent)
                atLeast(3f, drawn, bg, "${p.id} accent mark on $name $g")
            }
        }
    }

    @Test fun `accent text and marks read on the accent's own tint`() {
        // Solved archive cells and tier tiles, Today's three chips, the finish card: the accent at 0.2-0.22 over the ground.
        // The raw accent measures as little as 2:1 there, so text goes through onTint (4.5:1) and ticks and borders onTint 3f.
        schemes { name, s ->
            for (p in PuzzleRegistry.all) for ((g, bg) in s.grounds()) {
                val accent = Color(p.accent)
                val tint = accent.copy(alpha = 0.22f).compositeOver(bg)
                atLeast(4.5f, BoardHues.onTint(accent, bg), tint, "${p.id} text on its tint over $name $g")
                atLeast(3f, BoardHues.onTint(accent, bg, 3f), tint, "${p.id} tick on its tint over $name $g")
            }
        }
    }

    @Test fun `a button filled with the accent has readable text`() {
        // The walkthrough's and training's buttons (labelLarge, not large text): 4.5:1.
        forEachLook { look ->
            for (p in PuzzleRegistry.all) atLeast(4.5f, BoardHues.onFill(Color(p.accent)), Color(p.accent), "${p.id} button label, ${look.label}")
        }
    }

    @Test fun `a colour that already reads is left alone`() {
        assertEquals(LightScheme.onSurface, BoardHues.textOn(LightScheme.onSurface, LightScheme.surface))
    }

    @Test fun `board ink reads on the surface and background for every hue`() {
        schemes { name, s ->
            for (h in BoardHues.all) {
                val ink = BoardHues.ink(h, BoardHues.isDark(s))
                for ((g, bg) in s.grounds()) atLeast(4.5f, ink, bg, "ink of hue $h on $name $g")
            }
        }
    }

    @Test fun `error text reads, and so does text on an error fill`() {
        schemes { name, s ->
            for ((g, bg) in s.grounds()) atLeast(4.5f, s.error, bg, "error on $name $g")
            atLeast(4.5f, s.onError, s.error, "onError on $name error")
            atLeast(4.5f, BoardHues.onFill(s.error), s.error, "Tally's red chip text, $name")
        }
    }

    @Test fun `Lexicon letters read on green and gold tiles`() {
        for (m in listOf(GuessBoard.LightMarks, GuessBoard.DarkMarks)) for (f in listOf(m.correct, m.present)) for (c in listOf(f.from, f.to)) {
            atLeast(4.5f, f.ink, c, "letter on a marked tile")
        }
        // Classic's flat green and gold, their letter picked per fill (GuessBoard.classicMarks).
        forEachLook { look ->
            if (look != Look.CLASSIC) return@forEachLook
            for (c in listOf(GuessBoard.CLASSIC_CORRECT, GuessBoard.CLASSIC_PRESENT)) atLeast(4.5f, BoardHues.onFill(c), c, "Classic letter on a marked tile")
        }
    }
}
