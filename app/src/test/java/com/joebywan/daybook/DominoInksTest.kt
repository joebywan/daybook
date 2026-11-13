package com.joebywan.daybook

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.joebywan.daybook.puzzles.DominoInks
import com.joebywan.daybook.ui.theme.ArcadeBoard
import com.joebywan.daybook.ui.theme.BoardHues
import com.joebywan.daybook.ui.theme.BoardHues.contrast
import com.joebywan.daybook.ui.theme.Look
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dominoes' Arcade colours: pips and edges read on the domino, the domino stands off a square, a badge's number reads. */
class DominoInksTest {
    private fun atLeast(need: Float, fg: Color, bg: Color, what: String) =
        assertTrue("$what is ${contrast(fg, bg)}:1, needs $need", contrast(fg, bg) >= need)

    @Test fun `dominoes and badges read, in both schemes`() = forEachLook { look ->
        if (look != Look.ARCADE) return@forEachLook // the badge's ink (onFill) is the look's own
        for ((name, inks, a) in listOf(Triple("light", DominoInks.Light, ArcadeBoard.Light), Triple("dark", DominoInks.Dark, ArcadeBoard.Dark))) {
            atLeast(3f, inks.pip, inks.body, "$name pips")
            atLeast(3f, inks.bodyEdge.compositeOver(inks.body), inks.body, "$name domino edge")
            // In light a white domino lies on a white square: its edge and shadow part them. In dark the body itself does.
            if (name == "dark") atLeast(3f, inks.body, a.square, "$name domino on a square")
            atLeast(4.5f, BoardHues.onFill(inks.good), inks.good, "$name kept rule's badge")
            atLeast(4.5f, BoardHues.onFill(a.error), a.error, "$name broken rule's badge")
        }
    }
}
