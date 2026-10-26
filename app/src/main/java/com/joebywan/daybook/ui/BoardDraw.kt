package com.joebywan.daybook.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.BoardHighlight

/**
 * The ink of a board's heavy walls and frame (Lits, Mosaic, Regions, Tally): the scheme's softer ink, eased, so a wall
 * reads as structure rather than a black (or, dark, a white) bar over the colours. One colour, so the boards agree.
 */
val ColorScheme.wall: Color get() = onSurfaceVariant.copy(alpha = 0.7f)

/** A box a gesture reads the latest board through: plain, not state, so writing it recomposes nothing. */
internal class Cell<T>(var v: T)

/** The square of an even grid of [cols] x [rows] squares of side [step] under [offset], clamped to the grid. */
fun gridCellAt(offset: Offset, step: Float, cols: Int, rows: Int): Int =
    (offset.y / step).toInt().coerceIn(0, rows - 1) * cols + (offset.x / step).toInt().coerceIn(0, cols - 1)

/** The side of a square that fits [cols] x [rows] in this box: from both axes when the height is bounded (CLAUDE.md). */
fun BoxWithConstraintsScope.fitStep(cols: Int, rows: Int): Dp =
    if (constraints.hasBoundedHeight) minOf(maxWidth / cols, maxHeight / rows) else maxWidth / cols

/**
 * Region walls: a [stroke]-wide line wherever two neighbouring squares have a different [label], then
 * a frame round the grid, inset by [frameInset].
 */
fun DrawScope.drawWalls(
    cols: Int,
    rows: Int,
    step: Float,
    stroke: Float,
    colour: Color,
    origin: Offset = Offset.Zero,
    frameInset: Float = 0f,
    label: (Int) -> Int,
) {
    for (i in 0 until cols * rows) {
        val r = i / cols
        val c = i % cols
        if (c + 1 < cols && label(i) != label(i + 1)) {
            drawLine(
                colour,
                Offset(origin.x + (c + 1) * step, origin.y + r * step),
                Offset(origin.x + (c + 1) * step, origin.y + (r + 1) * step),
                strokeWidth = stroke,
            )
        }
        if (r + 1 < rows && label(i) != label(i + cols)) {
            drawLine(
                colour,
                Offset(origin.x + c * step, origin.y + (r + 1) * step),
                Offset(origin.x + (c + 1) * step, origin.y + (r + 1) * step),
                strokeWidth = stroke,
            )
        }
    }
    drawRect(
        color = colour,
        topLeft = Offset(origin.x + frameInset, origin.y + frameInset),
        size = Size(step * cols - frameInset * 2f, step * rows - frameInset * 2f),
        style = Stroke(width = stroke),
    )
}

/** Steps back every square of the grid a hint does not name, under a veil of [page]. */
fun DrawScope.drawHintDim(highlight: BoardHighlight, cols: Int, rows: Int, step: Float, page: Color) {
    if (highlight.isEmpty) return
    for (i in 0 until cols * rows) {
        if (i in highlight.strong || i in highlight.soft) continue
        drawRect(
            color = page.copy(alpha = 0.62f),
            topLeft = Offset((i % cols) * step, (i / cols) * step),
            size = Size(step, step),
        )
    }
}

/**
 * The outline inside each square a hint names: strong ones 4dp in [glow] at [pulse], soft ones a quiet
 * 1.5dp. [inset] keeps them clear of anything drawn along the square's edge (a wall).
 */
fun DrawScope.drawHintRings(
    highlight: BoardHighlight,
    cols: Int,
    rows: Int,
    step: Float,
    glow: Color,
    pulse: Float,
    corner: CornerRadius = CornerRadius(4.dp.toPx()),
    inset: Float = 0f,
) {
    val strongW = 4.dp.toPx()
    val softW = 1.5.dp.toPx()
    for (i in 0 until cols * rows) {
        val strong = i in highlight.strong
        if (!strong && i !in highlight.soft) continue
        val w = if (strong) strongW else softW
        val at = inset + w / 2
        drawRoundRect(
            color = if (strong) glow.copy(alpha = pulse) else glow.copy(alpha = 0.5f),
            topLeft = Offset((i % cols) * step + at, (i / cols) * step + at),
            size = Size(step - at * 2f, step - at * 2f),
            cornerRadius = corner,
            style = Stroke(w),
        )
    }
}

/** The same outline on a tile of its own: strong a [width] ring breathing at [pulse], soft a 1.5dp border. */
fun Modifier.hintOutline(
    strong: Boolean,
    soft: Boolean,
    glow: Color,
    pulse: () -> Float,
    width: Dp,
    shape: RoundedCornerShape,
): Modifier = when {
    strong -> drawWithContent {
        drawContent()
        val w = width.toPx()
        drawRoundRect(
            glow.copy(alpha = pulse()),
            topLeft = Offset(w / 2, w / 2),
            size = Size(size.width - w, size.height - w),
            cornerRadius = CornerRadius(shape.topStart.toPx(size, this)),
            style = Stroke(w),
        )
    }
    soft -> border(1.5.dp, glow.copy(alpha = 0.5f), shape)
    else -> this
}
