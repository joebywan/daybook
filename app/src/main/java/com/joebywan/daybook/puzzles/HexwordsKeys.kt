package com.joebywan.daybook.puzzles

import androidx.compose.ui.input.key.Key
import com.joebywan.daybook.core.isEnterKey
import com.joebywan.daybook.core.isEraseKey
import com.joebywan.daybook.core.letterKey
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/** What a physical key asks of a Hexwords board. */
sealed interface HexKeyAction {
    data class Letter(val letter: Char) : HexKeyAction
    data object Enter : HexKeyAction
    data object Backspace : HexKeyAction
    data object Clear : HexKeyAction

    /** Tab: the next way to trace what is typed, since which hexes it takes is the whole game. */
    data object NextTrace : HexKeyAction
}

fun hexKeyAction(key: Key): HexKeyAction? = letterKey(key)?.let { HexKeyAction.Letter(it) } ?: when {
    isEnterKey(key) -> HexKeyAction.Enter
    isEraseKey(key) -> HexKeyAction.Backspace
    key == Key.Escape -> HexKeyAction.Clear
    key == Key.Tab -> HexKeyAction.NextTrace
    else -> null
}

/** The path after the finger reaches [hex]: on if it touches the last hex and is new, back one if it is the hex before. */
fun swipeHex(path: List<Int>, hex: Int, nb: Array<IntArray>): List<Int> = when {
    path.isEmpty() -> listOf(hex)
    path.last() == hex -> path
    path.size >= 2 && path[path.size - 2] == hex -> path.dropLast(1)
    hex in path || hex !in nb[path.last()] -> path
    else -> path + hex
}

/** The word after a tap on [hex]: its last hex taps off, a touching new hex joins, any other hex starts again from itself. */
fun tapHex(path: List<Int>, hex: Int, nb: Array<IntArray>): List<Int> = when {
    path.lastOrNull() == hex -> path.dropLast(1)
    path.isNotEmpty() && hex !in path && hex in nb[path.last()] -> path + hex
    else -> listOf(hex)
}

/** The hexes of a block of [rows] drawn pointy-top in a [width] x [height] box, sized from both axes and centred. */
class HexGeometry(val rows: List<Int>, val width: Float, val height: Float) {
    private val widest = rows.max()

    /** Centre to corner. */
    val radius: Float = minOf(width / (SQRT3 * widest), height / (1.5f * rows.size + 0.5f))

    /** Centre to edge. */
    val apothem: Float = radius * SQRT3 / 2f

    val xs = FloatArray(rows.sum())
    val ys = FloatArray(rows.sum())

    init {
        val top = (height - radius * (1.5f * rows.size + 0.5f)) / 2f + radius
        var i = 0
        rows.forEachIndexed { y, w ->
            repeat(w) { c ->
                xs[i] = width / 2f + (2 * c - w + 1) * apothem
                ys[i] = top + y * 1.5f * radius
                i++
            }
        }
    }

    /** Whether ([x], [y]) is inside hex [i]'s six sides. */
    fun inside(i: Int, x: Float, y: Float): Boolean {
        val dx = abs(x - xs[i])
        val dy = abs(y - ys[i])
        return dx <= apothem && dy <= radius - dx / SQRT3
    }

    /** The hex a tap lands on, or null off the block. */
    fun hit(x: Float, y: Float): Int? = xs.indices.firstOrNull { inside(it, x, y) }

    /**
     * The hex a moving finger has entered: within most of the way to its edge, so sliding along a corner between two hexes
     * on the way to a third takes neither.
     */
    fun hitCore(x: Float, y: Float): Int? = xs.indices.firstOrNull { hypot(x - xs[it], y - ys[it]) <= apothem * CORE }

    private companion object {
        val SQRT3 = sqrt(3f)
        const val CORE = 0.78f
    }
}
