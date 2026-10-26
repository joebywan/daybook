package com.joebywan.daybook.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

/**
 * A text size for a glyph inside a board, sized from its square: it draws at exactly this many dp whatever the
 * system font size, so it always fits its slot. `(cell.value * 0.5f).sp` would grow with the font size and clip
 * (Sudoku's digits at 2x). Text outside a board (headers, buttons, captions) keeps plain `sp` and scales.
 * In a draw scope, which is a `Density`, `Dp.toSp()` does the same. `docs/PUZZLE_STANDARDS.md`; `BoardTextSizeTest`.
 */
@Composable
@ReadOnlyComposable
fun Dp.boardSp(): TextUnit = with(LocalDensity.current) { toSp() }
