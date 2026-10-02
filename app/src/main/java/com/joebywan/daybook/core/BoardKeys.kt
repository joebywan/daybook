package com.joebywan.daybook.core

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

/**
 * The keyboard plumbing every board shares (web, or a hardware keyboard on Android): the board's own
 * box takes focus, chords are ignored, and a held key is reported as a repeat. What a key *means* stays
 * in the board's pure key file and goes through the board's own state functions, so undo and notes
 * cannot differ from a tap.
 *
 * Focus is claimed on arrival and again whenever a hint lights something up (the Hint button took it
 * when clicked, and the move it asks for should be makeable from the keyboard). It draws nothing.
 *
 * [onKey] gets a key that is not part of a chord and says whether the board uses it (true consumes it,
 * even when the key changes nothing). `repeat` is true for the auto-repeat of a held key: act on it
 * only for arrows (see [isArrow]). Shift+key reaches [onKey] only when [shift] is set.
 */
@Composable
fun Modifier.boardKeys(
    enabled: Boolean,
    shift: Boolean = false,
    onKey: (key: Key, shift: Boolean, repeat: Boolean) -> Boolean,
): Modifier {
    val focus = remember { FocusRequester() }
    var held by remember { mutableStateOf<Key?>(null) }
    val strong = LocalBoardHighlight.current.strong
    LaunchedEffect(enabled, strong) { if (enabled) focus.requestFocus() }
    return this
        .focusRequester(focus)
        .onKeyEvent { event ->
            if (!enabled) return@onKeyEvent false
            // Chords belong to the browser (Ctrl+R, Cmd+L...) and to the system.
            if (event.isCtrlPressed || event.isMetaPressed || event.isAltPressed) return@onKeyEvent false
            if (event.isShiftPressed && !shift) return@onKeyEvent false
            when (event.type) {
                KeyEventType.KeyUp -> {
                    if (held == event.key) held = null
                    false
                }
                KeyEventType.KeyDown -> {
                    val repeat = held == event.key
                    held = event.key
                    onKey(event.key, event.isShiftPressed, repeat)
                }
                else -> false
            }
        }
        .focusable(enabled)
}

/** (dRow, dCol) for an arrow key, or null. */
fun arrowStep(key: Key): Pair<Int, Int>? = when (key) {
    Key.DirectionUp -> -1 to 0
    Key.DirectionDown -> 1 to 0
    Key.DirectionLeft -> 0 to -1
    Key.DirectionRight -> 0 to 1
    else -> null
}

/**
 * Outlines square [cursor] of an even [cols] x [rows] grid filling this node, or nothing for null.
 * The keyboard cursor: transient (`remember`, never `PuzzleState`), drawn over the board, no layout.
 */
fun Modifier.gridCursor(cursor: Int?, cols: Int, rows: Int, color: Color): Modifier =
    if (cursor == null) this else drawWithContent {
        drawContent()
        val w = size.width / cols
        val h = size.height / rows
        val stroke = maxOf(2f, minOf(w, h) * 0.1f)
        drawRect(
            color,
            Offset((cursor % cols) * w + stroke / 2, (cursor / cols) * h + stroke / 2),
            Size(w - stroke, h - stroke),
            style = Stroke(stroke),
        )
    }

/** True for the four arrows: the keys worth acting on while held. */
fun isArrow(key: Key): Boolean = arrowStep(key) != null

/** [at] moved by one arrow step on a [rows] x [cols] grid, clamped at the edges and never wrapping. */
fun stepCursor(at: Int, dRow: Int, dCol: Int, rows: Int, cols: Int): Int =
    (at / cols + dRow).coerceIn(0, rows - 1) * cols + (at % cols + dCol).coerceIn(0, cols - 1)
