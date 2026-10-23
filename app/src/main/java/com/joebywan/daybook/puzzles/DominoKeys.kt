package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.arrowStep

/**
 * The board's transient choice (`docs/DOMINO_SPEC.md` "Input"): the tray domino in hand, whether it
 * is turned round, and the first square tapped. Never `PuzzleState`: picking up and turning a domino
 * are not moves. Unturned, a domino leads with its larger half (the 6-3 puts its 6 on the first
 * square), as the tray draws it and the hints name it.
 */
data class DominoPick(val selected: Int? = null, val flipped: Boolean = false, val first: Int? = null) {

    /** This pick, cleared of anything the board no longer allows (undo, a hint, a lift). */
    fun on(s: DominoState): DominoPick {
        val sel = selected?.takeIf { it in s.tray.indices && s.placed[it] == null }
        val f = first?.takeIf { it in s.open.indices && s.open[it] && DominoLogic.dominoAt(s, it) < 0 }
        return if (sel == selected && f == first) this else DominoPick(sel, flipped && sel != null, f)
    }

    /** The pip value the selected domino will put on the first square, or null. */
    fun lead(s: DominoState): Int? = selected?.let { s.tray[it] }?.let { if (flipped) it.a else it.b }
}

/** A pick after a gesture, and the board it leaves: null when the board did not change (one state per gesture). */
data class DominoMove(val pick: DominoPick, val board: DominoState? = null)

/** Where the selected domino goes with its leading half on [lead] and its other half on [other]. */
internal fun placementFor(flipped: Boolean, lead: Int, other: Int): Placement =
    if (flipped) Placement(lead, other) else Placement(other, lead)

private fun firstInTray(s: DominoState): Int? = s.placed.indexOfFirst { it == null }.takeIf { it >= 0 }

private fun DominoState.changed(next: DominoState): DominoState? = next.takeIf { it !== this }

/** A tap on tray slot [k]: select it, or turn it round if it is already in hand. An empty slot does nothing. */
fun DominoState.tapTray(pick: DominoPick, k: Int): DominoMove {
    val p = pick.on(this)
    if (k !in tray.indices || placed[k] != null) return DominoMove(p)
    if (p.selected == k) return DominoMove(p.copy(flipped = !p.flipped && !tray[k].isDouble))
    return DominoMove(DominoPick(k, false, p.first))
}

/** A drag starting on tray slot [k]: that domino in hand, keeping its turn if it was already there; null for an empty slot. */
fun DominoState.grabTray(pick: DominoPick, k: Int): DominoPick? {
    if (k !in tray.indices || placed[k] != null) return null
    val p = pick.on(this)
    return DominoPick(k, p.selected == k && p.flipped)
}

/**
 * A tap on square [q]. On a laid domino: lift it back to its slot and into the hand. On an empty
 * square: the first square, or, beside the first, lay the domino in hand (the first in the tray when
 * none is). Tapping the first square again lets it go; a square that is not beside it becomes the first.
 */
fun DominoState.tapSquare(pick: DominoPick, q: Int): DominoMove {
    val p = pick.on(this)
    if (q !in open.indices || !open[q]) return DominoMove(p)
    val d = DominoLogic.dominoAt(this, q)
    if (d >= 0) return DominoMove(DominoPick(d), changed(DominoLogic.lift(this, d)))
    val sel = p.selected ?: firstInTray(this) ?: return DominoMove(p.copy(first = null))
    val flipped = p.flipped && p.selected == sel
    val f = p.first
    return when {
        f == null -> DominoMove(DominoPick(sel, flipped, q))
        f == q -> DominoMove(DominoPick(sel, flipped, null))
        DominoLogic.adjacent(width, f, q) -> DominoMove(DominoPick(), changed(DominoLogic.place(this, sel, placementFor(flipped, f, q))))
        else -> DominoMove(DominoPick(sel, flipped, q))
    }
}

/** A drag from square [from] to its neighbour [to]: the same as tapping the two. Anything else does nothing. */
fun DominoState.dragSquares(pick: DominoPick, from: Int, to: Int): DominoMove {
    val p = pick.on(this)
    val n = width * height
    if (from !in 0 until n || to !in 0 until n || !DominoLogic.adjacent(width, from, to)) return DominoMove(p)
    if (!open[from] || !open[to] || DominoLogic.dominoAt(this, from) >= 0 || DominoLogic.dominoAt(this, to) >= 0) return DominoMove(p)
    val sel = p.selected ?: firstInTray(this) ?: return DominoMove(p)
    val flipped = p.flipped && p.selected == sel
    return DominoMove(DominoPick(), changed(DominoLogic.place(this, sel, placementFor(flipped, from, to))))
}

/** A double tap or long press on square [q]: turn the domino laid there round (one state). */
fun DominoState.flipAt(pick: DominoPick, q: Int): DominoMove {
    val p = pick.on(this)
    val d = if (q in open.indices) DominoLogic.dominoAt(this, q) else -1
    if (d < 0) return DominoMove(p)
    return DominoMove(p, changed(DominoLogic.flip(this, d)))
}

// ---- keys ----------------------------------------------------------------------------------------

/** What a physical key asks of a Dominoes board. */
sealed interface DominoKeyAction {
    /** An arrow: the cursor to the next square of the board that way. */
    data class Move(val dRow: Int, val dCol: Int) : DominoKeyAction

    /** `]` or Tab (+1), `[` (-1): the next domino still in the tray. */
    data class Cycle(val step: Int) : DominoKeyAction

    /** F or Space: turn the domino in hand round, or, with none in hand, the one under the cursor. */
    data object Flip : DominoKeyAction

    /** Enter: a tap on the cursor's square. */
    data object Enter : DominoKeyAction

    /** Backspace or Delete: lift the domino under the cursor. */
    data object Lift : DominoKeyAction
}

/** The action for [key], or null for a key the board does not use. The caller has already ruled out chords. */
fun dominoKeyAction(key: Key): DominoKeyAction? {
    arrowStep(key)?.let { (r, c) -> return DominoKeyAction.Move(r, c) }
    return when (key) {
        Key.RightBracket, Key.Tab -> DominoKeyAction.Cycle(1)
        Key.LeftBracket -> DominoKeyAction.Cycle(-1)
        Key.F, Key.Spacebar -> DominoKeyAction.Flip
        Key.Enter, Key.NumPadEnter -> DominoKeyAction.Enter
        Key.Backspace, Key.Delete -> DominoKeyAction.Lift
        else -> null
    }
}

/** The cursor and pick after a key, and the board it leaves (null when unchanged). */
data class DominoKeyResult(val cursor: Int?, val pick: DominoPick, val board: DominoState? = null)

/**
 * [action] at [cursor] (null: none yet, so an arrow lands on the first square of the board). Every
 * change goes through the same functions a touch uses, so undo cannot differ.
 */
fun DominoState.applyKey(cursor: Int?, pick: DominoPick, action: DominoKeyAction): DominoKeyResult {
    val p = pick.on(this)
    return when (action) {
        is DominoKeyAction.Move -> {
            if (cursor == null) return DominoKeyResult(open.indexOfFirst { it }.takeIf { it >= 0 }, p)
            var r = cursor / width + action.dRow
            var c = cursor % width + action.dCol
            while (r in 0 until height && c in 0 until width && !open[r * width + c]) {
                r += action.dRow
                c += action.dCol
            }
            DominoKeyResult(if (r in 0 until height && c in 0 until width) r * width + c else cursor, p)
        }
        is DominoKeyAction.Cycle -> {
            val free = tray.indices.filter { placed[it] == null }
            if (free.isEmpty()) return DominoKeyResult(cursor, p)
            val at = free.indexOf(p.selected)
            val next = when {
                at < 0 -> if (action.step > 0) free.first() else free.last()
                else -> free[(at + action.step).mod(free.size)]
            }
            DominoKeyResult(cursor, DominoPick(next, false, p.first))
        }
        DominoKeyAction.Flip -> {
            val sel = p.selected
            val move = if (sel != null) tapTray(p, sel) else if (cursor != null) flipAt(p, cursor) else DominoMove(p)
            DominoKeyResult(cursor, move.pick, move.board)
        }
        DominoKeyAction.Enter -> {
            if (cursor == null) return DominoKeyResult(null, p)
            val move = tapSquare(p, cursor)
            DominoKeyResult(cursor, move.pick, move.board)
        }
        DominoKeyAction.Lift -> {
            if (cursor == null || DominoLogic.dominoAt(this, cursor) < 0) return DominoKeyResult(cursor, p)
            val move = tapSquare(p, cursor)
            DominoKeyResult(cursor, move.pick, move.board)
        }
    }
}
