package com.joebywan.daybook

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.joebywan.daybook.puzzles.Inequality
import com.joebywan.daybook.puzzles.Kings
import com.joebywan.daybook.puzzles.Mines
import com.joebywan.daybook.ui.ArcadeMetDark
import com.joebywan.daybook.ui.ArcadeMetLight
import com.joebywan.daybook.ui.theme.ArcadeBoard
import com.joebywan.daybook.ui.theme.BoardHues.contrast
import com.joebywan.daybook.ui.theme.useLook
import com.joebywan.daybook.ui.theme.Look
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Arcade look's own colours on Inequality, Regions, Kings, Sun & Moon, Tally, Thermometers and Mines, beyond what
 * ArcadeBoardTest already holds of the shared tokens: each against what it is drawn on, in both schemes.
 */
class ArcadeNumberGridsTest {
    private fun atLeast(need: Float, fg: Color, bg: Color, what: String) =
        assertTrue("$what is ${contrast(fg, bg)}:1, needs $need", contrast(fg, bg) >= need)

    /** The dark frame is translucent: what shows is it over the page's two stops. */
    private val darkPage = listOf(Color(0xFF241A6B), Color(0xFF120D33))

    private fun frames(dark: Boolean): List<Color> =
        if (dark) darkPage.map { ArcadeBoard.Dark.frame.compositeOver(it) } else listOf(ArcadeBoard.Light.frame)

    @Test fun `Inequality's signs read on the frame`() {
        for (dark in listOf(false, true)) for (f in frames(dark)) atLeast(3f, Inequality.ARCADE_SIGN, f, "sign, dark=$dark")
        for (a in listOf(ArcadeBoard.Light, ArcadeBoard.Dark)) atLeast(3f, a.errorInk, a.error, "broken sign on its disc")
    }

    @Test fun `Regions' digit on a plain square reads`() {
        for (a in listOf(ArcadeBoard.Light, ArcadeBoard.Dark)) atLeast(4.5f, a.control.to, a.square, "entered digit on a plain square")
    }

    @Test fun `a met chip reads, and parts from an open one`() {
        for ((met, a) in listOf(ArcadeMetLight to ArcadeBoard.Light, ArcadeMetDark to ArcadeBoard.Dark)) {
            for (stop in listOf(met.from, met.to)) {
                atLeast(4.5f, met.ink, stop, "met chip's number")
                atLeast(3f, stop, a.given, "met chip beside an open one")
            }
            atLeast(4.5f, a.givenInk, a.given, "open chip's number")
        }
    }

    @Test fun `Thermometers' glass and mercury read`() {
        for ((dark, a) in listOf(false to ArcadeBoard.Light, true to ArcadeBoard.Dark)) {
            val rim = (if (dark) Color.White else a.squareInk).copy(alpha = 0.6f)
            atLeast(3f, rim.compositeOver(a.square), a.square, "glass rim, dark=$dark")
            for (stop in listOf(a.control.from, a.control.to)) atLeast(3f, stop, a.given, "mercury in the glass, dark=$dark")
            atLeast(3f, a.squareInk.copy(alpha = 0.8f).compositeOver(a.given), a.given, "a cross in the glass, dark=$dark")
            atLeast(3f, a.squareInk.copy(alpha = 0.8f).compositeOver(a.square), a.square, "a cross on a square, dark=$dark")
        }
    }

    @Test fun `Mines' numbers, flags and hidden squares read`() {
        try {
            useLook(Look.ARCADE)
            for ((dark, a) in listOf(false to ArcadeBoard.Light, true to ArcadeBoard.Dark)) {
                for (k in 1..8) atLeast(4.5f, Mines.arcadeNumber(k, a, dark), a.square, "number $k, dark=$dark")
                for (stop in listOf(a.control.from, a.control.to)) atLeast(3f, stop, a.square, "a hidden square beside an open one, dark=$dark")
                for (stop in listOf(a.selected.from, a.selected.to)) atLeast(3f, a.selected.ink, stop, "a flag, dark=$dark")
                atLeast(3f, a.errorInk, a.error, "the mine hit, dark=$dark")
            }
        } finally {
            useLook(Look.ARCADE)
        }
    }

    @Test fun `Kings' marks read on every region, and the crown in its rim`() {
        for (c in Kings.regionColours) {
            val tile = Color(c)
            atLeast(4.5f, Kings.ArcadeMarkInk, tile, "mark ink on ${c.toString(16)}")
            atLeast(3f, Kings.ArcadeMarkInk.copy(alpha = 0.75f).compositeOver(tile), tile, "cross on ${c.toString(16)}")
        }
        atLeast(3f, Kings.ArcadeCrown, Kings.ArcadeMarkInk, "crown inside its rim")
    }
}
