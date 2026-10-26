package com.joebywan.daybook.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
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
