package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key

/**
 * What a physical key asks of a Target board. Keys read like a calculator: type a tile's number, an operator,
 * another tile's number, and the operation is made when the second is confirmed (Enter, or a digit-free key).
 */
sealed interface TargetKeyAction {
    /** 0-9: one more digit of the number being typed. */
    data class Digit(val d: Int) : TargetKeyAction

    /** + - x /: choose the operator (and confirm a number typed before it). */
    data class Operator(val op: TargetOp) : TargetKeyAction

    /** Enter: confirm the number typed as a tile. */
    data object Enter : TargetKeyAction

    /** Backspace or Delete: take back the last digit, else the operator, else the first tile. */
    data object Back : TargetKeyAction
}

/** The action for [key] (with [shift] held, since + and * are shifted on most keyboards), or null. */
fun targetKeyAction(key: Key, shift: Boolean = false): TargetKeyAction? {
    val digit = when (key) {
        Key.Zero, Key.NumPad0 -> 0
        Key.One, Key.NumPad1 -> 1
        Key.Two, Key.NumPad2 -> 2
        Key.Three, Key.NumPad3 -> 3
        Key.Four, Key.NumPad4 -> 4
        Key.Five, Key.NumPad5 -> 5
        Key.Six, Key.NumPad6 -> 6
        Key.Seven, Key.NumPad7 -> 7
        Key.Eight, Key.NumPad8 -> 8
        Key.Nine, Key.NumPad9 -> 9
        else -> -1
    }
    if (digit >= 0) {
        // Shift+8 is *, Shift+= is +: operators, not digits.
        if (shift && digit == 8) return TargetKeyAction.Operator(TargetOp.Mul)
        return if (shift) null else TargetKeyAction.Digit(digit)
    }
    return when (key) {
        Key.Plus, Key.NumPadAdd -> TargetKeyAction.Operator(TargetOp.Add)
        Key.Equals -> if (shift) TargetKeyAction.Operator(TargetOp.Add) else null
        Key.Minus, Key.NumPadSubtract -> TargetKeyAction.Operator(TargetOp.Sub)
        Key.NumPadMultiply, Key.X -> TargetKeyAction.Operator(TargetOp.Mul)
        Key.Slash, Key.NumPadDivide -> TargetKeyAction.Operator(TargetOp.Div)
        Key.Enter, Key.NumPadEnter -> TargetKeyAction.Enter
        Key.Backspace, Key.Delete -> TargetKeyAction.Back
        else -> null
    }
}

/**
 * The move being made, before it is an operation: the first tile, the operator, and (for the keyboard) the digits of a
 * number being typed. Transient (`remember` in the board), never [PuzzleState]: only a finished operation is a state.
 */
data class TargetEntry(val first: Int? = null, val op: TargetOp? = null, val typed: String = "")

/** What one tap or key did: the entry that follows, the finished operation if there was one, and a note to show. */
class TargetStep(val entry: TargetEntry, val state: TargetState? = null, val note: String? = null)

/** The longest number typed: tiles hold at most [TargetLogic.CAP]. */
private const val TYPED_MAX = 4

/** A tap on tile [slot]. Selects it, switches the first tile, or (with an operator chosen) finishes the operation. */
fun TargetState.tapTile(e: TargetEntry, slot: Int): TargetStep {
    if (slots[slot] == 0) return TargetStep(e)
    val first = e.first
    if (first == null) return TargetStep(TargetEntry(first = slot))
    if (slot == first) return TargetStep(TargetEntry())
    val op = e.op ?: return TargetStep(TargetEntry(first = slot))
    val next = combine(first, op, slot)
        ?: return TargetStep(TargetEntry(first, op), note = TargetLogic.refusal(slots[first], slots[slot], op))
    return TargetStep(TargetEntry(), next)
}

/** A tap on operator [op]: chooses it, or clears it if it was chosen. Needs a first tile. */
fun TargetState.tapOp(e: TargetEntry, op: TargetOp): TargetStep {
    if (e.first == null) return TargetStep(e.copy(typed = ""), note = "Pick a number first")
    return TargetStep(TargetEntry(e.first, if (e.op == op) null else op))
}

/** The slot holding [value], other than [except] (the first tile, which cannot be its own partner), or null. */
private fun TargetState.slotOf(value: Int, except: Int?): Int? =
    slots.indices.firstOrNull { it != except && slots[it] == value }

private fun TargetState.confirm(e: TargetEntry): TargetStep {
    val value = e.typed.toIntOrNull() ?: return TargetStep(e.copy(typed = ""))
    val cleared = e.copy(typed = "")
    val slot = slotOf(value, if (e.op != null) e.first else null)
        ?: return TargetStep(cleared, note = "No tile shows $value")
    // Typing a number before an operator picks (or changes) the first tile; after one, it is the second.
    return if (e.op == null) TargetStep(TargetEntry(first = slot)) else tapTile(cleared, slot)
}

/** One key. The same moves as the taps, through [tapOp] and [tapTile], so undo and the log cannot differ. */
fun TargetState.press(e: TargetEntry, action: TargetKeyAction): TargetStep = when (action) {
    is TargetKeyAction.Digit ->
        if (e.typed.length >= TYPED_MAX || (e.typed.isEmpty() && action.d == 0)) TargetStep(e)
        else TargetStep(e.copy(typed = e.typed + action.d))
    is TargetKeyAction.Operator -> {
        val confirmed = if (e.typed.isNotEmpty()) confirm(e) else TargetStep(e)
        // A number typed before the operator may itself finish nothing (it is the first tile), so go on to the operator.
        if (confirmed.state != null || confirmed.note != null) confirmed else tapOp(confirmed.entry, action.op)
    }
    TargetKeyAction.Enter -> if (e.typed.isNotEmpty()) confirm(e) else TargetStep(e)
    TargetKeyAction.Back -> when {
        e.typed.isNotEmpty() -> TargetStep(e.copy(typed = e.typed.dropLast(1)))
        e.op != null -> TargetStep(e.copy(op = null))
        else -> TargetStep(TargetEntry())
    }
}
