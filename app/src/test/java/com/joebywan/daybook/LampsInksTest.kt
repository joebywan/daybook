package com.joebywan.daybook

import androidx.compose.ui.graphics.Color
import com.joebywan.daybook.puzzles.LampsInks
import com.joebywan.daybook.ui.theme.ArcadeBoard
import com.joebywan.daybook.ui.theme.ArcadeFill
import com.joebywan.daybook.ui.theme.BoardHues.contrast
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lamps' Arcade colours: numbers read on their walls, walls stand off the squares, bulbs and crosses read on what they sit on. */
class LampsInksTest {
    private fun atLeast(need: Float, fg: Color, bg: Color, what: String) =
        assertTrue("$what is ${contrast(fg, bg)}:1, needs $need", contrast(fg, bg) >= need)

    private fun stops(f: ArcadeFill) = listOf(f.from, f.to)

    @Test fun `walls, numbers, lamps and crosses read, in both schemes`() {
        for ((name, inks, a) in listOf(Triple("light", LampsInks.Light, ArcadeBoard.Light), Triple("dark", LampsInks.Dark, ArcadeBoard.Dark))) {
            for (stop in stops(inks.wall)) {
                atLeast(4.5f, inks.wall.ink, stop, "$name wall number")
                atLeast(3f, stop, a.square, "$name wall against a square")
            }
            for (stop in stops(inks.met)) atLeast(4.5f, inks.met.ink, stop, "$name met number")
            atLeast(4.5f, a.errorInk, a.error, "$name broken number")
            for (ground in stops(inks.lit) + a.square) {
                // A lamp is its glass and its rim: one of them stands out on what it sits on (light's rim, dark's glass).
                val lamp = maxOf(contrast(inks.rim, ground), contrast(inks.bulb, ground))
                assertTrue("$name lamp on $ground is $lamp:1", lamp >= 3f)
                atLeast(3f, inks.cross, ground, "$name cross")
            }
            // The wash is decoration over the bulbs that cast it, but it must still part from an unlit square.
            for (stop in stops(inks.lit)) assertTrue("$name lit wash", ColourDistance.apart(stop, a.square) >= 10.0)
        }
    }
}
