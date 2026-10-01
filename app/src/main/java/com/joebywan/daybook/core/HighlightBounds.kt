package com.joebywan.daybook.core

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize

/**
 * Where on screen the current [BoardHighlight] is, so the hint popover can stay off it.
 *
 * The play screen provides one through [LocalHighlightBounds]; boards report into it with
 * [reportHighlight] (a node that can say what part of itself is highlighted), [highlightGrid] (an
 * even grid, the common case) or [highlightAnchor] (one element that is itself a highlight index).
 * Every report is converted to window coordinates and the popover takes the union, so a board
 * made of several parts (Sudoku's grid and its digit keys) just reports each part. A board that
 * reports nothing leaves [rect] null, which the screen treats as "unknown" and falls back to
 * placing the popover below.
 */
@Stable
class HighlightBounds {
    private val parts = mutableStateMapOf<Any, Rect>()

    /** The union of everything reported, in window coordinates, or null when nothing is. */
    val rect: Rect?
        get() = parts.values.reduceOrNull { a, b ->
            Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
        }

    internal fun set(key: Any, rect: Rect?) {
        if (rect == null) parts.remove(key) else if (parts[key] != rect) parts[key] = rect
    }
}

/** Null where nothing is listening (the walkthrough), in which case boards report nothing. */
val LocalHighlightBounds = staticCompositionLocalOf<HighlightBounds?> { null }

/**
 * Reports the part of this node the highlight covers. [bounds] gets the node's size and the
 * highlight, and answers in the node's own coordinates, or null when none of it is covered.
 */
fun Modifier.reportHighlight(bounds: (IntSize, BoardHighlight) -> Rect?): Modifier = composed {
    val sink = LocalHighlightBounds.current ?: return@composed this
    val highlight by rememberUpdatedState(LocalBoardHighlight.current)
    val measure by rememberUpdatedState(bounds)
    val key = remember { Any() }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    fun publish() {
        val c = coords
        val local = if (c != null && c.isAttached && !highlight.isEmpty) measure(c.size, highlight) else null
        sink.set(key, local?.let { Rect(c!!.localToWindow(it.topLeft), c.localToWindow(it.bottomRight)) })
    }

    val current = LocalBoardHighlight.current
    LaunchedEffect(current, coords) { publish() }
    DisposableEffect(sink) { onDispose { sink.set(key, null) } }
    this.onGloballyPositioned {
        coords = it
        publish()
    }
}

/** Reports the highlighted cells of an evenly divided [cols] x [rows] grid that fills this node. */
fun Modifier.highlightGrid(cols: Int, rows: Int): Modifier = reportHighlight { size, highlight ->
    val w = size.width / cols.toFloat()
    val h = size.height / rows.toFloat()
    var out: Rect? = null
    for (i in highlight.strong + highlight.soft) {
        if (i < 0 || i >= cols * rows) continue
        val cell = Rect(Offset((i % cols) * w, (i / cols) * h), Offset((i % cols + 1) * w, (i / cols + 1) * h))
        out = out?.let { Rect(minOf(it.left, cell.left), minOf(it.top, cell.top), maxOf(it.right, cell.right), maxOf(it.bottom, cell.bottom)) } ?: cell
    }
    out
}

/** Reports this whole node when [index] is one of the highlight's. */
fun Modifier.highlightAnchor(index: Int): Modifier = reportHighlight { size, highlight ->
    if (index in highlight.strong || index in highlight.soft) Rect(0f, 0f, size.width.toFloat(), size.height.toFloat()) else null
}
