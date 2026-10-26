package com.joebywan.daybook.core

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.puzzles.PuzzleState

/**
 * A board marked with two pens on a grid of squares (Nonogram's Fill/Cross, Tally's Remove/Keep): one
 * character per square, [UNMARKED] or a pen's mark. The tap, sweep and key rules live here once, so the
 * boards cannot drift apart. Implementers only say where their marks are; the marks stay in their own
 * serialised field.
 */
interface PenMarks<S : PenMarks<S>> {
    val penMarks: String
    val penWidth: Int
    val penHeight: Int

    /** The two pens in the order the pen row shows them; Space cycles untouched, the first, the second. */
    val pens: String

    /** This board with [marks], [changed] moves on. */
    fun withPenMarks(marks: String, changed: Int): S

    /** [mark] on every square of [indices] that is not already it; one state, one move per square. */
    fun paint(indices: Collection<Int>, mark: Char): S = paintWhere(indices, mark) { true }

    /**
     * A tap with [pen]: that mark, or an untouched square if it already has it. Tapping the other
     * pen's mark replaces it, so a player never has to rub out to change their mind.
     */
    fun tap(index: Int, pen: Char): S = paint(listOf(index), sweepMark(index, pen))

    /** What a sweep that starts on [index] with [pen] lays down: the pen's mark, or erasing if it is already there. */
    fun sweepMark(index: Int, pen: Char): Char = if (penMarks[index] == pen) UNMARKED else pen

    /**
     * A sweep: [mark] along [indices], but a pen only writes over untouched squares and an eraser
     * only takes out the mark it started on, so dragging one pen across the other's mark leaves it
     * alone, and rubbing out a run of one mark does not take the other with it.
     */
    fun sweep(indices: Collection<Int>, mark: Char, startedOn: Char): S =
        paintWhere(indices, mark) { if (mark == UNMARKED) it == startedOn else it == UNMARKED }

    /** The same instance when nothing changed, so callers can skip pushing it. */
    @Suppress("UNCHECKED_CAST")
    private fun paintWhere(indices: Collection<Int>, mark: Char, writes: (Char) -> Boolean): S {
        val next = penMarks.toCharArray()
        var changed = 0
        for (i in indices) {
            if (writes(next[i]) && next[i] != mark) {
                next[i] = mark
                changed++
            }
        }
        return if (changed == 0) this as S else withPenMarks(next.concatToString(), changed)
    }

    /**
     * [action] applied at [cursor] (null: no cursor yet, so an arrow lands on the first square and
     * other keys do nothing). Marks go through the same [tap] / [paint] / [sweep] a touch uses, one
     * state per key, so undo and the sweep rules cannot differ.
     */
    @Suppress("UNCHECKED_CAST")
    fun applyKey(cursor: Int?, action: PenKeyAction): PenKeyResult<S> {
        if (cursor == null) return PenKeyResult(if (action is PenKeyAction.Move) 0 else null, this as S)
        return when (action) {
            is PenKeyAction.Move -> {
                val to = stepCursor(cursor, action.dRow, action.dCol, penHeight, penWidth)
                val mark = penMarks[cursor]
                val board = if (action.lay && to != cursor && mark != UNMARKED) sweep(listOf(to), mark, mark) else this as S
                PenKeyResult(to, board)
            }
            is PenKeyAction.Pen -> PenKeyResult(cursor, tap(cursor, action.mark))
            PenKeyAction.Cycle -> {
                val cycle = UNMARKED + pens
                PenKeyResult(cursor, paint(listOf(cursor), cycle[(cycle.indexOf(penMarks[cursor]) + 1) % cycle.length]))
            }
            PenKeyAction.Clear -> PenKeyResult(cursor, paint(listOf(cursor), UNMARKED))
        }
    }

    companion object {
        const val UNMARKED = '.'
    }
}

/** This board with [changes] (square to mark) written over it and no move counted: a walkthrough frame. */
fun <S : PenMarks<S>> S.marked(changes: Map<Int, Char>): S {
    val next = penMarks.toCharArray()
    changes.forEach { (i, c) -> next[i] = c }
    return withPenMarks(next.concatToString(), 0)
}

/**
 * Accepts exactly this board with [changes] made and nothing else. Strict on purpose, as Kings' is: each
 * walkthrough frame's board is written for the one before it.
 */
inline fun <reified S : PenMarks<S>> S.only(changes: Map<Int, Char>): (PuzzleState) -> Boolean = { next ->
    next is S && next.penMarks == marked(changes).penMarks
}

/** What a physical key asks of a pen board. */
sealed interface PenKeyAction {
    /** An arrow: move the cursor one square. With [lay], Shift was held: carry the square's mark along. */
    data class Move(val dRow: Int, val dCol: Int, val lay: Boolean) : PenKeyAction

    /** A pen's key: that pen's mark on the cursor square, or an untouched square if it already has it (a tap). */
    data class Pen(val mark: Char) : PenKeyAction

    /** Space: untouched, then the first pen, then the second, then untouched again. */
    data object Cycle : PenKeyAction

    /** Backspace or Delete. */
    data object Clear : PenKeyAction
}

/** The action for [key], or null for a key the board does not use. [pens] maps a board's letters to its marks. The caller has already ruled out chords. */
fun penKeyAction(key: Key, shift: Boolean, pens: Map<Key, Char>): PenKeyAction? {
    arrowStep(key)?.let { (r, c) -> return PenKeyAction.Move(r, c, shift) }
    if (shift) return null
    return when (key) {
        Key.Spacebar -> PenKeyAction.Cycle
        Key.Backspace, Key.Delete -> PenKeyAction.Clear
        else -> pens[key]?.let { PenKeyAction.Pen(it) }
    }
}

/** The cursor after a key and the board it leaves; [board] is the same instance when nothing changed. */
data class PenKeyResult<S>(val cursor: Int?, val board: S)

/** The squares a sweep from [start] toward [at] covers on a [width]-wide grid: the straight line along the longer way. */
fun sweptCells(start: Int, at: Int, width: Int): List<Int> {
    val r0 = start / width
    val c0 = start % width
    val r1 = at / width
    val c1 = at % width
    return if (kotlin.math.abs(c1 - c0) >= kotlin.math.abs(r1 - r0)) {
        (minOf(c0, c1)..maxOf(c0, c1)).map { r0 * width + it }
    } else {
        (minOf(r0, r1)..maxOf(r0, r1)).map { it * width + c0 }
    }
}
