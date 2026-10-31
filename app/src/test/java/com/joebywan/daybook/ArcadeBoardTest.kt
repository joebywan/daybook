package com.joebywan.daybook

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.puzzles.SudokuHintLight
import com.joebywan.daybook.puzzles.SudokuTwin
import com.joebywan.daybook.puzzles.boxShade
import com.joebywan.daybook.ui.theme.ArcadeBoard
import com.joebywan.daybook.ui.theme.ArcadeFill
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class ArcadeBoardTest {
    private fun ratio(a: Color, b: Color): Float {
        val hi = max(a.luminance(), b.luminance())
        val lo = min(a.luminance(), b.luminance())
        return (hi + .05f) / (lo + .05f)
    }

    private fun atLeast(need: Float, fg: Color, bg: Color, what: String) =
        assertTrue("$what is ${ratio(fg, bg)}:1, needs $need", ratio(fg, bg) >= need)

    private fun readsOn(fill: ArcadeFill, what: String) {
        atLeast(4.5f, fill.ink, fill.from, "$what, first stop")
        atLeast(4.5f, fill.ink, fill.to, "$what, second stop")
    }

    @Test fun `every tile's name reads on both its stops, and the tile is two hues`() {
        for (dark in listOf(false, true)) for (p in PuzzleRegistry.all) {
            val tile = ArcadeBoard.tile(Color(p.accent), dark)
            readsOn(tile, "${p.id} dark=$dark")
            assertTrue("${p.id} dark=$dark has one colour", tile.from != tile.to)
        }
    }

    @Test fun `board tokens read, in both schemes`() {
        for ((name, b) in listOf("light" to ArcadeBoard.Light, "dark" to ArcadeBoard.Dark)) {
            atLeast(4.5f, b.squareInk, b.square, "$name square ink")
            atLeast(4.5f, b.givenInk, b.given, "$name given ink")
            atLeast(4.5f, b.errorInk, b.error, "$name error ink")
            readsOn(b.selected, "$name selected")
            readsOn(b.control, "$name control")
            readsOn(b.letter, "$name letter")
            atLeast(3f, b.selectedEdge, b.square, "$name selected edge on a square")
            // Amber on a white square misses 3:1, so light's edge must part from the amber too; dark's amber stands off its square alone.
            if (ratio(b.selected.from, b.square) < 3f) {
                atLeast(3f, b.selectedEdge, b.selected.from, "$name selected edge on its first stop")
                atLeast(3f, b.selectedEdge, b.selected.to, "$name selected edge on its second stop")
            }
            atLeast(3f, b.error, b.square, "$name error square beside a plain one")
            atLeast(1.3f, b.frame.compositeOver(b.square), b.square, "$name grid line") // decorative: the squares' shape holds the grid
        }
    }

    @Test fun `Sudoku's own colours read, in both schemes`() {
        for ((name, b) in listOf("light" to ArcadeBoard.Light, "dark" to ArcadeBoard.Dark)) {
            readsOn(SudokuTwin, "$name twin")
            atLeast(4.5f, b.squareInk, b.boxShade(), "$name ink on a shaded box")
            atLeast(4.5f, b.givenInk, b.square, "$name note")
            atLeast(4.5f, b.givenInk, b.boxShade(), "$name note on a shaded box")
            // A cage's dash on its halo: the one or the other parts from any fill a square can have.
            for (fill in listOf(b.control.from, b.control.to, b.selected.from, b.selected.to, b.error, b.given, b.square, SudokuTwin.from, SudokuTwin.to)) {
                assertTrue("$name dash and halo both lost on $fill", max(ratio(b.squareInk, fill), ratio(b.square, fill)) >= 3f)
            }
        }
        atLeast(3f, SudokuHintLight, ArcadeBoard.Light.square, "hint ring on a square")
        atLeast(3f, SudokuHintLight, ArcadeBoard.Light.frame, "hint ring on the frame")
        atLeast(3f, ArcadeBoard.Dark.selected.from, ArcadeBoard.Dark.square, "dark hint ring on a square")
    }
}
