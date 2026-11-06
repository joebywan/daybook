package com.joebywan.daybook.ui

import com.joebywan.daybook.ui.theme.ArcadeBoard
import com.joebywan.daybook.ui.theme.BoardHues
import com.joebywan.daybook.ui.theme.arcadeLift
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.PenKeyAction
import com.joebywan.daybook.core.PenMarks
import com.joebywan.daybook.core.boardKeys
import com.joebywan.daybook.core.gridCursor
import com.joebywan.daybook.core.highlightGrid
import com.joebywan.daybook.core.keepClear
import com.joebywan.daybook.core.penKeyAction
import com.joebywan.daybook.core.sweptCells

// The input of a two-pen board (Nonogram, Tally): taps, sweeps, keys and the pen row. Taps follow
// [TwinTaps], the rule of every board with two kinds of mark. The pen, the
// sweep under the finger and the cursor are the caller's `remember` state, never the board's: the
// play screen pushes an undo entry for every state it is handed.

/** A sweep in progress: where it began, where the finger is now, what it lays down and what was under its start. */
class Sweep(val start: Int, val at: Int, val mark: Char, val startedOn: Char)

/** This board as it would be if [current] (if any) lifted now. */
fun <S : PenMarks<S>> S.swept(current: Sweep?): S =
    current?.let { w -> sweep(sweptCells(w.start, w.at, penWidth), w.mark, w.startedOn) } ?: this

/** Keys for the board: arrows move [cursor], [pens] maps letters to marks; one state per key. */
@Composable
fun <S : PenMarks<S>> Modifier.penKeys(
    taps: TwinTaps<S>,
    interactive: Boolean,
    pens: Map<Key, Char>,
    cursor: MutableState<Int?>,
): Modifier = boardKeys(interactive, shift = true) { key, shift, repeat ->
    val action = penKeyAction(key, shift, pens) ?: return@boardKeys false
    if (!repeat || action is PenKeyAction.Move) {
        val result = taps.settle().applyKey(cursor.value, action)
        cursor.value = result.cursor
        taps.apply { result.board }
    }
    true
}

/**
 * Taps and sweeps over the grid alone, in its own coordinates ([unitPx] a square). A tap uses [pen], a
 * double tap or long press the other one ([TwinTaps]). A sweep is one state, emitted on lift, and
 * locks to the row or column of the larger move ([sweptCells]).
 * Without [sweeps] (a zoomed Nonogram, where a drag pans) only taps are read.
 */
fun <S : PenMarks<S>> Modifier.penGrid(
    taps: TwinTaps<S>,
    interactive: Boolean,
    pen: Char,
    unitPx: Float,
    sweep: MutableState<Sweep?>,
    cursor: MutableState<Int?>,
    cursorColour: Color,
    sweeps: Boolean = true,
): Modifier {
    val s = taps.board
    val w = s.penWidth
    val h = s.penHeight
    val other = s.pens.first { it != pen }
    fun cellAt(offset: Offset): Int {
        val c = (offset.x / unitPx).toInt().coerceIn(0, w - 1)
        val r = (offset.y / unitPx).toInt().coerceIn(0, h - 1)
        return r * w + c
    }
    // Not keyed on the board: [taps] holds the latest, and emitting must not restart a gesture.
    return this
        .highlightGrid(w, h)
        .gridCursor(cursor.value.takeIf { interactive }, w, h, cursorColour)
        .pointerInput(interactive, pen, unitPx) {
            if (!interactive) return@pointerInput
            detectTapGestures(
                onLongPress = { offset ->
                    val i = cellAt(offset)
                    cursor.value = i
                    taps.press(i) { it.tap(i, other) }
                },
            ) { offset ->
                val i = cellAt(offset)
                cursor.value = i
                taps.tap(i, { it.tap(i, pen) }, { it.tap(i, other) })
            }
        }
        .pointerInput(interactive, pen, sweeps, unitPx) {
            if (!interactive || !sweeps) return@pointerInput
            // The overload that hands over the *down*: Compose's touch slop on the web is wider than
            // a 15x15 square, so the plain one would start every sweep a square or two along from
            // where the finger landed.
            detectDragGestures(
                orientationLock = null,
                onDragStart = { down, _, _ ->
                    val start = cellAt(down.position)
                    val board = taps.settle()
                    sweep.value = Sweep(start, start, board.sweepMark(start, pen), board.penMarks[start])
                },
                onDrag = { change, _ ->
                    change.consume()
                    sweep.value = sweep.value?.let { Sweep(it.start, cellAt(change.position), it.mark, it.startedOn) }
                },
                onDragEnd = { _ ->
                    val done = sweep.value
                    sweep.value = null
                    if (done != null) taps.apply { it.swept(done) }
                },
                onDragCancel = { sweep.value = null },
            )
        }
}

/**
 * [pens] under the board, or stacked ([vertical]) beside it. Kept clear of the hint popover: the player reaches for it to make a hinted move.
 * With [arcade] the chosen pen is its control fill and [icon] must read on that; without, its accent tint.
 */
@Composable
fun PenRow(
    pens: String,
    pen: Char,
    enabled: Boolean,
    accent: Color,
    label: (Char) -> String,
    onPen: (Char) -> Unit,
    vertical: Boolean = false,
    arcade: ArcadeBoard? = null,
    icon: DrawScope.(Char) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    @Composable
    fun Pens(content: @Composable () -> Unit) =
        if (vertical) Column(Modifier.keepClear(), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        else Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp).keepClear(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) { content() }
    Pens {
        for (option in pens) {
            val chosen = pen == option
            Row(
                Modifier
                    .width(124.dp)
                    .height(46.dp)
                    .then(if (chosen && arcade != null) Modifier.arcadeLift(arcade.control, 14.dp) else Modifier)
                    .clip(RoundedCornerShape(14.dp))
                    .then(
                        when {
                            arcade == null -> Modifier.background(if (chosen) accent.copy(alpha = 0.22f) else scheme.surface)
                            chosen -> Modifier.background(arcade.control.brush)
                            else -> Modifier.background(arcade.square)
                        },
                    )
                    .border(
                        if (chosen) 2.dp else 1.dp,
                        when {
                            arcade != null -> if (chosen) arcade.control.ink else arcade.frame
                            chosen -> BoardHues.onTint(accent, scheme.surface, 3f)
                            else -> scheme.outline
                        },
                        RoundedCornerShape(14.dp),
                    )
                    .clickable(enabled = enabled) { onPen(option) },
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(Modifier.size(20.dp)) { icon(option) }
                // A fixed 124 x 46 button: "Remove" clipped at 2x.
                CappedFontScale { Text(label(option), style = MaterialTheme.typography.labelLarge, color = arcade?.let { if (chosen) it.control.ink else it.squareInk } ?: scheme.onSurface, maxLines = 1) }
            }
        }
    }
}
