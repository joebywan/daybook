package com.joebywan.daybook.puzzles

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import com.joebywan.daybook.core.BoardHighlight
import kotlin.math.pow

// Zoom for a big Nonogram board (docs/TODO.md history: Expert's squares were 16-19dp on a phone, and a
// reshaped board was measured and dropped). Pinch zooms; zoomed in, one finger pans and a tap still
// marks; on the whole board everything is as it was. The view is the screen's, never the board's:
// it is not saved, has no undo, and every screen gets the same puzzle.

/** The most a board zooms: about 40dp squares from Expert's 16 on a short phone. */
internal const val MAX_ZOOM = 2.5f

/** Boards this wide or more zoom; the 5x5 (and its walkthrough, which teaches the sweep) never does. */
internal const val ZOOM_FROM_WIDTH = 10

/**
 * How the grid is shown in its box: [scale] 1 is the whole grid; [offset] is where its top-left
 * sits, in pixels, so it is never above zero and never far enough left or up to show past an edge.
 */
@Immutable
internal data class GridView(val scale: Float = 1f, val offset: Offset = Offset.Zero) {
    val zoomed: Boolean get() = scale > 1.001f

    /** Zoomed by [factor] about [focus] (a point in the box), within 1 and [MAX_ZOOM]. */
    fun zoomedBy(factor: Float, focus: Offset, box: Size): GridView {
        val next = (scale * factor).coerceIn(1f, MAX_ZOOM)
        return GridView(next, focus - (focus - offset) * (next / scale)).within(box)
    }

    fun pannedBy(delta: Offset, box: Size): GridView = copy(offset = offset + delta).within(box)

    fun within(box: Size): GridView = copy(
        offset = Offset(
            offset.x.coerceIn(minOf(0f, box.width * (1 - scale)), 0f),
            offset.y.coerceIn(minOf(0f, box.height * (1 - scale)), 0f),
        ),
    )

    /**
     * The least change that shows [xs] and [ys] (spans of the unzoomed grid, in pixels; null for
     * "anywhere"): zoomed out only as far as they need to fit, and moved only if they are not
     * already in view, then centred on them.
     */
    fun showing(xs: ClosedFloatingPointRange<Float>?, ys: ClosedFloatingPointRange<Float>?, box: Size): GridView {
        if (!zoomed) return this
        fun fit(span: ClosedFloatingPointRange<Float>?, room: Float) =
            span?.let { room / (it.endInclusive - it.start).coerceAtLeast(1f) } ?: MAX_ZOOM
        val next = minOf(scale, fit(xs, box.width), fit(ys, box.height)).coerceAtLeast(1f)
        fun axis(span: ClosedFloatingPointRange<Float>?, at: Float, room: Float): Float {
            // Keep the middle of the view where it was, then centre on the span if that leaves it out.
            val kept = if (next == scale) at else room / 2 - (room / 2 - at) / scale * next
            if (span == null) return kept
            val lo = span.start * next + kept
            val hi = span.endInclusive * next + kept
            return if (lo >= 0f && hi <= room) kept else room / 2 - (span.start + span.endInclusive) / 2 * next
        }
        return GridView(next, Offset(axis(xs, offset.x, box.width), axis(ys, offset.y, box.height))).within(box)
    }

    companion object {
        fun lerp(a: GridView, b: GridView, t: Float) =
            GridView(a.scale + (b.scale - a.scale) * t, lerp(a.offset, b.offset, t))
    }
}

/**
 * The spans of a [width] x [height] grid of [unit]-pixel squares that [highlight] needs in view:
 * its strong squares (else its soft ones), and the line of any clue it names. A whole row needs
 * only its height in view (its clue is pinned beside it), and a whole column only its width.
 */
internal fun highlightSpans(
    highlight: BoardHighlight,
    width: Int,
    height: Int,
    unit: Float,
): Pair<ClosedFloatingPointRange<Float>?, ClosedFloatingPointRange<Float>?>? {
    val n = width * height
    val named = highlight.strong.ifEmpty { highlight.soft }
    var rows = named.filter { it in 0 until n }.map { it / width }
    var cols = named.filter { it in 0 until n }.map { it % width }
    val cells = named.count { it in 0 until n }
    // A full row or column is a line, wherever its clue is.
    if (rows.toSet().size == 1 && cells == width) cols = emptyList()
    if (cols.toSet().size == 1 && cells == height) rows = emptyList()
    for (i in named) {
        if (i in n until n + height) rows = rows + (i - n)
        if (i in n + height until n + height + width) cols = cols + (i - n - height)
    }
    if (rows.isEmpty() && cols.isEmpty()) return null
    fun span(at: List<Int>) = if (at.isEmpty()) null else at.min() * unit..(at.max() + 1) * unit
    return span(cols) to span(rows)
}

/** Moves [view] to [to]: animated, unless reduced motion asks for stillness. */
internal suspend fun MutableState<GridView>.glideTo(to: GridView, still: Boolean) {
    val from = value
    if (still || from == to) {
        value = to
        return
    }
    animate(0f, 1f, animationSpec = tween(250)) { t, _ -> value = GridView.lerp(from, to, t) }
}

/**
 * Pinch to zoom anywhere on the board, and, while zoomed, a one-finger drag to pan (once past the
 * touch slop, so a tap is still a tap). [gridAt] is where the grid sits in this node and [grid] its
 * size. Read on the Initial pass, before the grid's own taps and sweeps: once a second finger is
 * down, or a zoomed drag is a pan, every change is consumed, so a sweep already begun is
 * cancelled (it is never a state until lift) and the lift is not a tap.
 *
 * A mouse: Ctrl+wheel (or a trackpad pinch, which browsers send the same way) zooms, and the
 * wheel pans while zoomed.
 */
internal fun Modifier.gridZoom(view: MutableState<GridView>, gridAt: Offset, grid: Size, enabled: Boolean): Modifier =
    if (!enabled) this else this
        .pointerInput(gridAt, grid) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var pinching = false
                var panning = false
                var travel = Offset.Zero
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } >= 2) pinching = true
                    if (pinching) {
                        // The last lift has no fingers down, so no centre: nothing to move.
                        val centre = event.calculateCentroid()
                        if (centre.isSpecified) {
                            view.value = view.value
                                .zoomedBy(event.calculateZoom(), centre - gridAt, grid)
                                .pannedBy(event.calculatePan(), grid)
                        }
                        event.changes.forEach { it.consume() }
                    } else if (view.value.zoomed) {
                        val change = event.changes.first()
                        travel += change.positionChange()
                        if (travel.getDistance() > viewConfiguration.touchSlop) panning = true
                        if (panning) {
                            view.value = view.value.pannedBy(change.positionChange(), grid)
                            change.consume()
                        }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
        .pointerInput(gridAt, grid) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type != PointerEventType.Scroll) continue
                    val change = event.changes.first()
                    val delta = change.scrollDelta
                    if (event.keyboardModifiers.isCtrlPressed) {
                        view.value = view.value.zoomedBy(1.15f.pow(-delta.y.coerceIn(-3f, 3f)), change.position - gridAt, grid)
                    } else if (view.value.zoomed) {
                        view.value = view.value.pannedBy(-delta * (grid.height / 15f), grid)
                    } else continue
                    change.consume()
                }
            }
        }
