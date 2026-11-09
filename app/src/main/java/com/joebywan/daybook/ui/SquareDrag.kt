package com.joebywan.daybook.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.joebywan.daybook.core.dragTrail
import com.joebywan.daybook.puzzles.Snap

/**
 * A drag that draws through the squares of a [cols] x [rows] board, [unitPx] pixels a square (Pearls, Links).
 *
 * The trail starts on the square the finger went down on (the overload of `detectDragGestures` that hands over the
 * *down*: the web's touch slop is wide), takes every square a fast swipe crosses in order ([Snap.cellsCrossed]), and
 * backs up when the finger goes back over the square before ([dragTrail]). [onTrail] hears every change, so the board
 * can draw the drag in progress; [onEnd] gets the finished trail once, on lift, for one state; a cancelled drag clears
 * the trail and emits nothing.
 *
 * The detector is keyed on the board's size alone and reads everything else through `rememberUpdatedState`, so it
 * outlives the board's states (Snap's lesson, CLAUDE.md).
 */
@Composable
fun Modifier.squareDrag(
    cols: Int,
    rows: Int,
    unitPx: Float,
    enabled: Boolean,
    onTrail: (List<Int>) -> Unit,
    onEnd: (List<Int>) -> Unit,
): Modifier {
    val unit by rememberUpdatedState(unitPx)
    val trail by rememberUpdatedState(onTrail)
    val end by rememberUpdatedState(onEnd)
    return pointerInput(cols, rows, enabled) {
        if (!enabled) return@pointerInput
        var path = emptyList<Int>()
        var lastX = 0f
        var lastY = 0f
        detectDragGestures(
            orientationLock = null,
            onDragStart = { down, _, _ ->
                lastX = down.position.x / unit
                lastY = down.position.y / unit
                path = listOf(gridCellAt(Offset(lastX, lastY), 1f, cols, rows))
                trail(path)
            },
            onDrag = { change, _ ->
                change.consume()
                val x = change.position.x / unit
                val y = change.position.y / unit
                var p = path
                // Every square a fast swipe crosses, in order, as a slow one would.
                for (cell in Snap.cellsCrossed(cols, rows, lastX, lastY, x, y)) p = dragTrail(p, cell, cols, rows)
                lastX = x
                lastY = y
                if (p != path) {
                    path = p
                    trail(p)
                }
            },
            onDragEnd = {
                val p = path
                path = emptyList()
                trail(path)
                end(p)
            },
            onDragCancel = {
                path = emptyList()
                trail(path)
            },
        )
    }
}
