package com.joebywan.daybook.ui

import com.joebywan.daybook.ui.theme.BoardHues
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

// The input of a two-pen board (Nonogram, Tally): taps, sweeps, keys and the pen row. The pen, the
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
    s: S,
    interactive: Boolean,
    pens: Map<Key, Char>,
    cursor: MutableState<Int?>,
    onState: (S) -> Unit,
): Modifier = boardKeys(interactive, shift = true) { key, shift, repeat ->
    val action = penKeyAction(key, shift, pens) ?: return@boardKeys false
    if (!repeat || action is PenKeyAction.Move) {
        val result = s.applyKey(cursor.value, action)
        cursor.value = result.cursor
        if (result.board !== s) onState(result.board)
    }
    true
}

/**
 * Taps and sweeps over the grid alone, in its own coordinates ([unitPx] a square). A sweep is one
 * state, emitted on lift, and locks to the row or column of the larger move ([sweptCells]).
 * Without [sweeps] (a zoomed Nonogram, where a drag pans) only taps are read.
 */
fun <S : PenMarks<S>> Modifier.penGrid(
    s: S,
    interactive: Boolean,
    pen: Char,
    unitPx: Float,
    sweep: MutableState<Sweep?>,
    cursor: MutableState<Int?>,
    cursorColour: Color,
    onState: (S) -> Unit,
    sweeps: Boolean = true,
): Modifier {
    val w = s.penWidth
    val h = s.penHeight
    fun cellAt(offset: Offset): Int {
        val c = (offset.x / unitPx).toInt().coerceIn(0, w - 1)
        val r = (offset.y / unitPx).toInt().coerceIn(0, h - 1)
        return r * w + c
    }
    return this
        .highlightGrid(w, h)
        .gridCursor(cursor.value.takeIf { interactive }, w, h, cursorColour)
        .pointerInput(s, interactive, pen) {
            if (!interactive) return@pointerInput
            detectTapGestures { offset ->
                cursor.value = cellAt(offset)
                onState(s.tap(cellAt(offset), pen))
            }
        }
        .pointerInput(s, interactive, pen, sweeps) {
            if (!interactive || !sweeps) return@pointerInput
            // The overload that hands over the *down*: Compose's touch slop on the web is wider than
            // a 15x15 square, so the plain one would start every sweep a square or two along from
            // where the finger landed.
            detectDragGestures(
                orientationLock = null,
                onDragStart = { down, _, _ ->
                    val start = cellAt(down.position)
                    sweep.value = Sweep(start, start, s.sweepMark(start, pen), s.penMarks[start])
                },
                onDrag = { change, _ ->
                    change.consume()
                    sweep.value = sweep.value?.let { Sweep(it.start, cellAt(change.position), it.mark, it.startedOn) }
                },
                onDragEnd = { _ ->
                    val done = sweep.value
                    sweep.value = null
                    if (done != null) {
                        val next = s.swept(done)
                        if (next !== s) onState(next)
                    }
                },
                onDragCancel = { sweep.value = null },
            )
        }
}

/** [pens] under the board, or stacked ([vertical]) beside it. Kept clear of the hint popover: the player reaches for it to make a hinted move. */
@Composable
fun PenRow(
    pens: String,
    pen: Char,
    enabled: Boolean,
    accent: Color,
    label: (Char) -> String,
    onPen: (Char) -> Unit,
    vertical: Boolean = false,
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
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (chosen) accent.copy(alpha = 0.22f) else scheme.surface)
                    .border(
                        if (chosen) 2.dp else 1.dp,
                        if (chosen) BoardHues.onTint(accent, scheme.surface, 3f) else scheme.outline,
                        RoundedCornerShape(14.dp),
                    )
                    .clickable(enabled = enabled) { onPen(option) },
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(Modifier.size(20.dp)) { icon(option) }
                // A fixed 124 x 46 button: "Remove" clipped at 2x.
                CappedFontScale { Text(label(option), style = MaterialTheme.typography.labelLarge, color = scheme.onSurface, maxLines = 1) }
            }
        }
    }
}
