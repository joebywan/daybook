package com.joebywan.daybook.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

/**
 * A text size for a glyph inside a board, sized from its square: it draws at exactly this many dp whatever the
 * system font size, so it always fits its slot. `(cell.value * 0.5f).sp` would grow with the font size and clip
 * (Sudoku's digits at 2x). Text outside a board (headers, buttons, captions) keeps plain `sp` and scales, except
 * chrome in a fixed slot, which stops at [CHROME_FONT_CAP] ([CappedFontScale]).
 * In a draw scope, which is a `Density`, `Dp.toSp()` does the same. `docs/PUZZLE_STANDARDS.md`; `BoardTextSizeTest`.
 */
@Composable
@ReadOnlyComposable
fun Dp.boardSp(): TextUnit = with(LocalDensity.current) { toSp() }

/**
 * The largest system font size that chrome in a fixed slot follows (the play header, a tier pill, a pen button, a home
 * tile's name): each was checked to fit at 1.3x, while at 2x the play header took three lines and squeezed the board,
 * "Standard" and "Monday" broke mid-word and "Remove" clipped. Below the cap nothing changes.
 */
const val CHROME_FONT_CAP = 1.3f

/** [content] with the system font size held to at most [CHROME_FONT_CAP]; up to it, text grows as usual. */
@Composable
fun CappedFontScale(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    if (density.fontScale <= CHROME_FONT_CAP) content()
    else CompositionLocalProvider(LocalDensity provides Density(density.density, CHROME_FONT_CAP), content = content)
}

/**
 * The one type scale for digits on a board: each role is a share of the square it sits in (or of the board's unit,
 * for a clue beside the grid) and a weight. Boards had drifted to 0.34-0.6 of a square in four weights, with
 * Nonogram's clues heavier than its grid and cage totals about 8sp on a short phone. A new board picks a role.
 *
 * - [GIVEN]: a digit the board came with (Sudoku's givens, Tally's numbers). Bold, so it differs from an entry.
 * - [ENTERED]: a digit the player placed. The same size as a given, lighter.
 * - [CLUE]: a number beside the grid (Nonogram's runs, Tally's targets): smaller and lighter than the grid's own.
 * - [CORNER]: a label in a square's corner (cage totals, Mosaic's area numbers): 0.32 is about 10.5sp on a cage
 *   square at 375x537, the largest that clears a centred digit.
 * - [NOTE]: a pencil mark, one of nine in a square.
 */
enum class BoardGlyph(val share: Float, val weight: FontWeight) {
    GIVEN(0.56f, FontWeight.Bold),
    ENTERED(0.56f, FontWeight.Normal),
    CLUE(0.48f, FontWeight.Medium),
    CORNER(0.32f, FontWeight.SemiBold),
    NOTE(0.34f, FontWeight.Medium),
}

/** The text style of [glyph] in a square of side [square], for a `Text`: the theme's family, this size and weight. */
@Composable
@ReadOnlyComposable
fun BoardGlyph.style(square: Dp): TextStyle {
    val size = (square * share).boardSp()
    return LocalTextStyle.current.merge(TextStyle(fontSize = size, lineHeight = size * 1.2f, fontWeight = weight))
}

/** The same in a draw scope or with a measurer, for a square of [squarePx] pixels. */
fun Density.boardStyle(glyph: BoardGlyph, squarePx: Float): TextStyle =
    TextStyle(fontSize = (squarePx * glyph.share).toSp(), fontWeight = glyph.weight)
