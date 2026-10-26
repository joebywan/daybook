package com.joebywan.daybook.core

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
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
import kotlinx.coroutines.launch

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
 * only for arrows (see [arrowStep]). Shift+key reaches [onKey] only when [shift] is set.
 *
 * Keys are paced one per recomposition. Every board works its move out from the state it was last
 * composed with, so a second key before the next frame acted on the stale board and the first was lost:
 * "crane" typed at speed into Lexicon came out "CE", and four quick arrows moved Sudoku's cursor once.
 * A key that arrives while the board is still catching up waits in a queue and is consumed then.
 */
@Composable
fun Modifier.boardKeys(
    enabled: Boolean,
    shift: Boolean = false,
    onKey: (key: Key, shift: Boolean, repeat: Boolean) -> Boolean,
): Modifier {
    val focus = remember { FocusRequester() }
    var held by remember { mutableStateOf<Key?>(null) }
    val latest by rememberUpdatedState(onKey)
    val live by rememberUpdatedState(enabled)
    val pacer = remember { KeyPacer() }
    val scope = rememberCoroutineScope()
    // Two frames: the key's state lands in the first frame's recomposition, which runs after its frame callbacks.
    fun press(p: KeyPress): Boolean {
        val used = latest(p.key, p.shift, p.repeat)
        if (used) {
            pacer.settling = true
            scope.launch {
                while (true) {
                    withFrameNanos { }
                    withFrameNanos { }
                    val next = pacer.queue.removeFirstOrNull() ?: break
                    if (live) latest(next.key, next.shift, next.repeat) else pacer.queue.clear()
                }
                pacer.settling = false
            }
        }
        return used
    }
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
                    val p = KeyPress(event.key, event.isShiftPressed, repeat)
                    when {
                        !pacer.settling -> press(p)
                        // A held key outrunning the frames is dropped rather than piled up to run on after release.
                        repeat && pacer.queue.isNotEmpty() -> true
                        // Consumed now and judged later, so a key the board turns down mid-burst (H, Esc) is lost;
                        // splitting each board's onKey into "uses" and "apply" would fix it if that ever matters.
                        else -> { pacer.queue.addLast(p); true }
                    }
                }
                else -> false
            }
        }
        .focusable(enabled)
}

private class KeyPress(val key: Key, val shift: Boolean, val repeat: Boolean)

/** Keys waiting for the board to catch up with the last one. Plain fields: read only from event handlers and the pacing coroutine. */
private class KeyPacer {
    var settling = false
    val queue = ArrayDeque<KeyPress>()
}

private val DIGIT_KEYS = listOf(
    Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine,
)
private val NUMPAD_KEYS = listOf(
    Key.NumPad0, Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4,
    Key.NumPad5, Key.NumPad6, Key.NumPad7, Key.NumPad8, Key.NumPad9,
)
private val LETTER_KEYS = listOf(
    Key.A, Key.B, Key.C, Key.D, Key.E, Key.F, Key.G, Key.H, Key.I, Key.J, Key.K, Key.L, Key.M,
    Key.N, Key.O, Key.P, Key.Q, Key.R, Key.S, Key.T, Key.U, Key.V, Key.W, Key.X, Key.Y, Key.Z,
)

/** 0-9 for a digit key, top row or number pad, else null. */
fun digitKey(key: Key): Int? =
    DIGIT_KEYS.indexOf(key).takeIf { it >= 0 } ?: NUMPAD_KEYS.indexOf(key).takeIf { it >= 0 }

/** 'a'..'z' for a letter key, else null. */
fun letterKey(key: Key): Char? = LETTER_KEYS.indexOf(key).takeIf { it >= 0 }?.let { 'a' + it }

/** Enter, on the main block or the number pad. */
fun isEnterKey(key: Key): Boolean = key == Key.Enter || key == Key.NumPadEnter

/** Backspace or Delete. */
fun isEraseKey(key: Key): Boolean = key == Key.Backspace || key == Key.Delete

/** Space or Enter: "tap what the cursor is on". */
fun isConfirmKey(key: Key): Boolean = key == Key.Spacebar || isEnterKey(key)

/** What a physical key asks of a board with a digit pad and a selected square (Sudoku, Inequality, Regions). */
sealed interface DigitKeyAction {
    /** 1..max: the digit pad's key of that number. */
    data class Digit(val digit: Int) : DigitKeyAction

    /** Backspace, Delete or 0: empty the selected square. */
    data object Clear : DigitKeyAction

    /** An arrow: move the selection one square, never wrapping. */
    data class Move(val dRow: Int, val dCol: Int) : DigitKeyAction
}

/** The action for [key] on a board whose pad goes up to [max], or null for a key it does not use. The caller has ruled out chords. */
fun digitKeyAction(key: Key, max: Int): DigitKeyAction? {
    arrowStep(key)?.let { (r, c) -> return DigitKeyAction.Move(r, c) }
    if (isEraseKey(key)) return DigitKeyAction.Clear
    val d = digitKey(key) ?: return null
    return when {
        d == 0 -> DigitKeyAction.Clear
        d <= max -> DigitKeyAction.Digit(d)
        else -> null
    }
}

/**
 * The board after [action] on a [side] x [side] grid whose selection is [at], or null when it changes nothing.
 * One state per key, the same ones a tap emits: [select], [enter] (a digit on a square) and [clear] are the
 * board's own. With nothing selected an arrow selects the first square.
 */
inline fun <S> S.applyDigitKey(
    action: DigitKeyAction,
    at: Int?,
    side: Int,
    select: S.(Int) -> S,
    enter: S.(Int, Int) -> S,
    clear: S.(Int) -> S,
): S? {
    val next = when (action) {
        is DigitKeyAction.Move -> if (at == null) return select(0) else select(stepCursor(at, action.dRow, action.dCol, side, side))
        is DigitKeyAction.Digit -> if (at == null) this else enter(at, action.digit)
        DigitKeyAction.Clear -> if (at == null) this else clear(at)
    }
    return if (next == this) null else next
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
 * [gap] (px) is the space between squares, if any. The keyboard cursor: transient (`remember`, never `PuzzleState`), drawn over the board, no layout.
 */
fun Modifier.gridCursor(cursor: Int?, cols: Int, rows: Int, color: Color, gap: Float = 0f): Modifier =
    if (cursor == null) this else drawWithContent {
        drawContent()
        val w = (size.width + gap) / cols
        val h = (size.height + gap) / rows
        val stroke = maxOf(2f, minOf(w, h) * 0.1f)
        drawRect(
            color,
            Offset((cursor % cols) * w + stroke / 2, (cursor / cols) * h + stroke / 2),
            Size(w - gap - stroke, h - gap - stroke),
            style = Stroke(stroke),
        )
    }

/**
 * The cursor after [key] if it is an arrow (the first arrow lands on square 0 when there is none yet),
 * else null: the key is not a move.
 */
fun movedCursor(cursor: Int?, key: Key, rows: Int, cols: Int): Int? {
    val (dr, dc) = arrowStep(key) ?: return null
    return if (cursor == null) 0 else stepCursor(cursor, dr, dc, rows, cols)
}

/** [at] moved by one arrow step on a [rows] x [cols] grid, clamped at the edges and never wrapping. */
fun stepCursor(at: Int, dRow: Int, dCol: Int, rows: Int, cols: Int): Int =
    (at / cols + dRow).coerceIn(0, rows - 1) * cols + (at % cols + dCol).coerceIn(0, cols - 1)
