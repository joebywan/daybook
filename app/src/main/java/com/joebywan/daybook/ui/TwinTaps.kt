package com.joebywan.daybook.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/**
 * The one input rule of every board with two kinds of mark: a tap makes the first, and a double tap
 * on the same square or a long press makes the other (Kings' cross and crown, Nonogram's pen and the
 * other pen, Who's Who's tick and cross). One copy, so the games cannot drift apart again.
 *
 * A tap is drawn at once ([shown]) but handed to the screen only once the double-tap window has
 * passed, so a double tap is one move, not a first mark with the other under it in the undo history.
 * Giving `onDoubleTap` to `detectTapGestures` instead would delay the *drawing* of every tap.
 *
 * Driven by events and a timer of its own, never by recomposition (a frame can lag behind a quick
 * tap): [board] is the board as of the last thing this emitted or was handed. So a gesture detector
 * using it should not be keyed on the board, or emitting would restart it mid-gesture.
 */
class TwinTaps<S : Any> internal constructor(
    initial: S,
    private val now: () -> Long,
    /** Runs the action after the delay; returns its cancel. */
    private val after: (Long, () -> Unit) -> () -> Unit,
) {
    private class Held<S>(val key: Int, val base: S, val after: S)

    var board: S = initial
        internal set
    internal var emit: (S) -> Unit = {}
    private var held by mutableStateOf<Held<S>?>(null)
    private var cancel: (() -> Unit)? = null
    private var lastKey = -1
    private var lastAt = 0L

    /** The board as the finger has left it, which may be one tap ahead of the screen. */
    val shown: S get() = held?.takeIf { it.base === board }?.after ?: board

    /**
     * A tap on [key] (a square, a pad item): [once] now, or [twin] in its place if the next tap lands
     * on the same key in time. Without [holds] the tap is emitted at once and starts no double: for a
     * tap that would show something the twin must not (Mines' reveal) or that is already the twin's
     * undoing (taking a crown back).
     */
    fun tap(key: Int, once: (S) -> S, twin: (S) -> S, holds: Boolean = true) {
        val t = now()
        if (key == lastKey && t - lastAt < TWIN_MS) {
            press(key, twin)
            return
        }
        val from = settle()
        val next = once(from)
        if (!holds) {
            push(next)
            return
        }
        lastKey = key
        lastAt = t
        if (next === from) return
        val h = Held(key, from, next)
        held = h
        cancel = after(TWIN_MS) {
            if (held === h) {
                held = null
                if (h.base === board) push(h.after)
            }
        }
    }

    /** A long press on [key], or the second tap of a double: [twin], replacing a tap held on the same key. */
    fun press(key: Int, twin: (S) -> S) {
        lastKey = -1
        val h = drop()
        push(twin(if (h != null && h.key == key) h.base else settled(h)))
    }

    /** Hands over any held tap now and returns the board as it then stands: for keys, sweeps and buttons. */
    fun settle(): S {
        lastKey = -1
        return settled(drop())
    }

    /** [change] on the settled board, emitted unless it changed nothing. */
    fun apply(change: (S) -> S) = push(change(settle()))

    private fun drop(): Held<S>? {
        cancel?.invoke()
        cancel = null
        val h = held?.takeIf { it.base === board }
        held = null
        return h
    }

    private fun settled(h: Held<S>?): S {
        if (h != null) push(h.after)
        return board
    }

    private fun push(next: S) {
        if (next === board) return
        board = next
        emit(next)
    }

    companion object {
        const val TWIN_MS = 300L
    }
}

private val tapClock = TimeSource.Monotonic.markNow()

/** [TwinTaps] for [board], emitting through [onState]; hand it the latest of both on every composition. */
@Composable
fun <S : Any> rememberTwinTaps(board: S, onState: (S) -> Unit): TwinTaps<S> {
    val scope = rememberCoroutineScope()
    val taps = remember {
        TwinTaps(board, { tapClock.elapsedNow().inWholeMilliseconds }) { ms, action ->
            val job = scope.launch {
                delay(ms)
                action()
            }
            ({ job.cancel() })
        }
    }
    taps.board = board
    taps.emit = onState
    return taps
}
