package com.joebywan.daybook.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/*
 * Aiming a drag on a grid board: the hand hides the squares under it, so the row and column in hand are
 * tinted out past it, and a drag whose size is the clue (Nonogram's run, Shikaku's area) shows that size
 * clear of the finger. Shared, never copied: Nonogram, Shikaku, Thermometers, Tally, Tents, Kings, Lamps, LITS.
 * Nothing here is `PuzzleState`; the board hands over the square under the finger while a drag is held.
 */

/** The row and column in hand: a tint of the accent, light enough to leave every mark readable. */
const val AIM_ALPHA = 0.16f

/** [accent] (the board's `vivid(accent)`) at [AIM_ALPHA]. */
fun aimTint(accent: Color): Color = accent.copy(alpha = AIM_ALPHA)

/** How far the drag count stays from the finger's centre: a fingertip is about 10 mm across. */
val FINGER_CLEAR = 40.dp

/**
 * Tints the row and column of square [at] (null: nothing) over everything this node draws, its children
 * included. The node must be exactly the [cols] x [rows] grid. The crossing square is tinted once.
 */
fun Modifier.aimLines(at: Int?, cols: Int, rows: Int, tint: Color): Modifier =
    if (at == null || at !in 0 until cols * rows) this
    else drawWithContent {
        drawContent()
        val uw = size.width / cols
        val uh = size.height / rows
        val r = at / cols
        val c = at % cols
        drawRect(tint, Offset(0f, r * uh), Size(size.width, uh))
        drawRect(tint, Offset(c * uw, 0f), Size(uw, r * uh))
        drawRect(tint, Offset(c * uw, (r + 1) * uh), Size(uw, size.height - (r + 1) * uh))
    }

/** Whether a drag from square [start] to [at] runs more down (or up) than across, on a grid [cols] wide. */
fun headingDown(start: Int, at: Int, cols: Int): Boolean =
    abs(at / cols - start / cols) > abs(at % cols - start % cols)

/**
 * The drag's size, [count], in a pill held clear of the finger ([dragCountAt]); nothing for one square or
 * fewer, where a drag is a slipped tap. [finger] is the centre of the square under it, in this box's pixels;
 * [down] is [headingDown]. Drawn over the whole box, so the pill may sit outside the grid.
 */
@Composable
fun BoxScope.DragCount(count: Int, finger: Offset, down: Boolean) {
    if (count <= 1) return
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val label = measurer.measure(count.toString(), MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
    Canvas(Modifier.matchParentSize()) {
        val pad = Offset(12.dp.toPx(), 4.dp.toPx())
        val pill = Size(maxOf(label.size.width + 2 * pad.x, label.size.height + 2 * pad.y), label.size.height + 2 * pad.y)
        val at = dragCountAt(finger, pill, size, FINGER_CLEAR.toPx(), down)
        drawRoundRect(scheme.inverseSurface, at, pill, CornerRadius(pill.height / 2f))
        drawText(label, scheme.inverseOnSurface, at + Offset((pill.width - label.size.width) / 2f, pad.y))
    }
}

/**
 * Where a [pill] of [box] goes beside a finger at [finger] (top-left), at least [clear] from it. The hand
 * comes from below, so above the finger if there is room and the drag runs across ([down] false: above a
 * drag heading down or up it would cover the squares the finger is heading for); else beside it, on the
 * side with more room, its foot level with the finger. Never below: that is under the hand.
 */
fun dragCountAt(finger: Offset, pill: Size, box: Size, clear: Float, down: Boolean = false): Offset {
    fun x(at: Float) = at.coerceIn(0f, maxOf(0f, box.width - pill.width))
    val above = finger.y - clear - pill.height
    if (!down && above >= 0f) return Offset(x(finger.x - pill.width / 2f), above)
    val left = finger.x > box.width / 2f
    return Offset(if (left) finger.x - clear - pill.width else finger.x + clear, maxOf(0f, finger.y - pill.height))
}
