package com.joebywan.daybook

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import com.joebywan.daybook.core.PuzzleRegistry
import com.joebywan.daybook.puzzles.SudokuHintLight
import com.joebywan.daybook.puzzles.SudokuTwin
import com.joebywan.daybook.puzzles.boxShade
import com.joebywan.daybook.puzzles.Lexicon
import com.joebywan.daybook.puzzles.NONOGRAM_DONE_DARK
import com.joebywan.daybook.puzzles.NONOGRAM_DONE_LIGHT
import com.joebywan.daybook.puzzles.NONOGRAM_FILLS_DARK
import com.joebywan.daybook.puzzles.NONOGRAM_FILLS_LIGHT
import com.joebywan.daybook.puzzles.WordWheel
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

    @Test fun `Lexicon's marks read and stay apart, in both schemes`() {
        for ((b, m) in listOf(ArcadeBoard.Light to Lexicon.LightMarks, ArcadeBoard.Dark to Lexicon.DarkMarks)) {
            readsOn(m.correct, "right")
            readsOn(m.present, "present")
            atLeast(4.5f, m.absentInk, m.absent, "absent ink")
            atLeast(3f, m.absent, b.square, "absent beside a plain square")
            atLeast(3f, m.absent, m.present.from, "absent beside present")
            for (c in listOf(m.correct.from, m.correct.to)) {
                atLeast(3f, c, b.square, "right beside a plain square")
                val apart = ColourDistance.apart(c, m.present.to)
                assertTrue("right and present are $apart apart in the worst vision", apart > 10.0)
            }
        }
    }

    @Test fun `Nonogram's filled squares stand off an empty one at every step of the blend, and finished clues read`() {
        for ((b, fills) in listOf(ArcadeBoard.Light to NONOGRAM_FILLS_LIGHT, ArcadeBoard.Dark to NONOGRAM_FILLS_DARK)) {
            for ((from, to) in fills) for (i in 0..10) {
                atLeast(3f, lerp(Color(from), Color(to), i / 10f), b.square, "fill ${from.toString(16)} step $i")
            }
        }
        readsOn(NONOGRAM_DONE_LIGHT, "finished clue, light")
        readsOn(NONOGRAM_DONE_DARK, "finished clue, dark")
    }

    @Test fun `word wheel's swipe path stands on its well`() {
        for ((name, b) in listOf("light" to ArcadeBoard.Light, "dark" to ArcadeBoard.Dark)) {
            val well = WordWheel.wheelWell(b)
            for (stop in listOf(b.selected.from, b.selected.to)) {
                atLeast(3f, stop, well, "$name swipe path")
                atLeast(3f, stop.copy(alpha = .7f).compositeOver(well), well, "$name swipe to the finger")
            }
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
