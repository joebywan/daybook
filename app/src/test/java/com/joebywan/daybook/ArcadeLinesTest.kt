package com.joebywan.daybook

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.joebywan.daybook.puzzles.LoopLine
import com.joebywan.daybook.puzzles.Pointers
import com.joebywan.daybook.ui.theme.ArcadeBoard
import com.joebywan.daybook.ui.theme.BoardHues
import com.joebywan.daybook.ui.theme.BoardHues.contrast
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Arcade look's line boards (Atoms, Pearls, Loop, Links, Pipes, Arrows, Trail): what each draws on [ArcadeBoard]'s squares
 * reads, in both schemes. Classic draws what it drew before, so only Arcade is measured here.
 */
class ArcadeLinesTest {
    private fun atLeast(need: Float, fg: Color, bg: Color, what: String) =
        assertTrue("$what is ${contrast(fg, bg)}:1, needs $need", contrast(fg, bg) >= need)

    private val boards = listOf("light" to ArcadeBoard.Light, "dark" to ArcadeBoard.Dark)

    @Test fun `the loop line reads on a square and on a numbered one`() {
        for ((name, a) in boards) {
            val line = LoopLine.of(a === ArcadeBoard.Dark)
            atLeast(3f, line, a.square, "$name Loop and Pearls line on a square")
            atLeast(3f, line, a.given, "$name Loop line on a numbered square")
        }
    }

    @Test fun `the control gradient that draws Trail's line, Pipes' water and a full atom reads on a square`() {
        for ((name, a) in boards) {
            for (stop in listOf(a.control.from, a.control.to)) atLeast(3f, stop, a.square, "$name control stop")
            atLeast(3f, a.control.from, a.given, "$name Pipes' water in its bore")
            for (stop in listOf(a.control.from, a.control.to)) atLeast(4.5f, a.control.ink, stop, "$name a full atom's number")
        }
    }

    @Test fun `marks drawn in the square's ink read on it`() {
        for ((name, a) in boards) {
            atLeast(4.5f, a.squareInk, a.square, "$name an atom's number, a bond")
            atLeast(4.5f, a.errorInk, a.error, "$name a broken atom's number")
            atLeast(3f, a.squareInk.copy(alpha = LoopLine.CROSS).compositeOver(a.square), a.square, "$name a cross")
            atLeast(3f, a.selectedEdge, a.square, "$name Trail's numbered ring")
            for (stop in listOf(a.selected.from, a.selected.to)) atLeast(4.5f, a.selected.ink, stop, "$name Trail's number")
        }
    }

    @Test fun `every arrow reads on the board`() {
        for ((name, a) in boards) {
            val dark = a === ArcadeBoard.Dark
            for (h in Pointers.HUES) atLeast(3f, BoardHues.ink(h, dark), a.square, "$name arrow hue $h")
        }
    }
}
