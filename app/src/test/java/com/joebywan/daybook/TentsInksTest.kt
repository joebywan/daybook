package com.joebywan.daybook

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.joebywan.daybook.puzzles.TentsInks
import com.joebywan.daybook.ui.theme.ArcadeBoard
import com.joebywan.daybook.ui.theme.ArcadeFill
import com.joebywan.daybook.ui.theme.BoardHues.contrast
import org.junit.Assert.assertTrue
import org.junit.Test

class TentsInksTest {
    private fun atLeast(need: Float, fg: Color, bg: Color, what: String) =
        assertTrue("$what is ${contrast(fg, bg)}:1, needs $need", contrast(fg, bg) >= need)

    private fun both(need: Float, fill: ArcadeFill, what: String) {
        atLeast(need, fill.ink, fill.from, "$what, first stop")
        atLeast(need, fill.ink, fill.to, "$what, second stop")
    }

    @Test fun `marks and clues read, in both schemes`() {
        for ((name, inks, a) in listOf(Triple("light", TentsInks.Light, ArcadeBoard.Light), Triple("dark", TentsInks.Dark, ArcadeBoard.Dark))) {
            both(3f, inks.tree, "$name tree")
            both(3f, inks.grass, "$name grass note")
            both(4.5f, inks.met, "$name met clue")
            atLeast(3f, inks.tent, a.selected.from, "$name tent, first stop")
            atLeast(3f, inks.tent, a.selected.to, "$name tent, second stop")
            atLeast(3f, inks.tentDoor, inks.tent, "$name door")
            for (stop in listOf(inks.grass.from, inks.grass.to)) {
                atLeast(3f, inks.grass.ink, stop.copy(alpha = TentsInks.SPROUTED).compositeOver(a.square), "$name sprouted grass")
            }
        }
    }
}
