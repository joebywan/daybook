package com.joebywan.daybook.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.joebywan.daybook.core.BoardHighlight

/** What a board draws for the hint: what it names, the glow's colour, and its breath (1 when still). */
class HintGlow(
    val highlight: BoardHighlight = BoardHighlight.None,
    val glow: Color = Color.Unspecified,
    val pulse: Float = 1f,
)

/**
 * The hint's rims, as every board draws them ([u] a square): a thin one round what the hint only
 * cites, then round what it names a band of [background] under a strong one that breathes. [rim]
 * draws one set at a stroke width in a colour, in the board's own shape.
 */
inline fun hintRims(highlight: BoardHighlight, u: Float, glow: Color, pulse: Float, background: Color, rim: (Set<Int>, Float, Color) -> Unit) {
    rim(highlight.soft - highlight.strong, u * 0.07f, glow.copy(alpha = 0.5f))
    rim(highlight.strong, u * 0.2f, background.copy(alpha = 0.9f * pulse))
    rim(highlight.strong, u * 0.11f, glow.copy(alpha = pulse))
}

/**
 * A rim just inside the outline of [set]'s squares on a [cols] x [rows] grid, one stroke per square
 * side facing out of it. Square `i` is drawn at [rect]; indices off the grid (clues, keys) and squares
 * [shows] refuses are skipped.
 */
inline fun DrawScope.squareRim(
    set: Set<Int>,
    width: Float,
    colour: Color,
    cols: Int,
    rows: Int,
    rect: (Int) -> Rect,
    shows: (Int) -> Boolean = { true },
) {
    val h = width / 2
    for (i in set) {
        if (i !in 0 until cols * rows || !shows(i)) continue
        val r = rect(i)
        val row = i / cols
        val col = i % cols
        if (row == 0 || i - cols !in set) drawLine(colour, Offset(r.left, r.top + h), Offset(r.right, r.top + h), width)
        if (row == rows - 1 || i + cols !in set) drawLine(colour, Offset(r.left, r.bottom - h), Offset(r.right, r.bottom - h), width)
        if (col == 0 || i - 1 !in set) drawLine(colour, Offset(r.left + h, r.top), Offset(r.left + h, r.bottom), width)
        if (col == cols - 1 || i + 1 !in set) drawLine(colour, Offset(r.right - h, r.top), Offset(r.right - h, r.bottom), width)
    }
}
