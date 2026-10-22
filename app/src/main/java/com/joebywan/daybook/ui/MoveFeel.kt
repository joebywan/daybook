package com.joebywan.daybook.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.abs
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * How many moves the play screen has been handed this visit. Only `PlayScreen.push` raises it: undo, restart, a
 * restored save and the walkthrough (which provides nothing, so it stays 0) never do, which is how [rememberMoveFeel]
 * tells a move the player made from a board arriving any other way.
 */
val LocalMoveCount = compositionLocalOf { 0 }

private const val SETTLE_MS = 120
private const val SWEEP_MS = 400
private const val SETTLE_SCALE = 0.08f
/** The sweep's half width, as a share of the unit it runs along. */
private const val SWEEP_WIDTH = 0.35f

/**
 * The feel of a move: the squares it changed settle (drawn 8% large, easing back over 120 ms), and a unit it
 * finished (a row, a box, a cage, a line, a tube) has a soft glow run along it once (400 ms). Read [scale] and
 * [glow] in the draw phase only, so a running animation repaints the board without recomposing it. Nothing here
 * is state of the game; nothing changes size or position.
 */
@Stable
class MoveFeel internal constructor() {
    internal var settle by mutableFloatStateOf(1f)
    internal var sweep by mutableFloatStateOf(1f)
    internal var settleRun = 0
    internal var sweepRun = 0
    private var tick = -1
    private var cells: List<Any?> = emptyList()
    private var done = BooleanArray(0)
    private var changed: Set<Int> = emptySet()
    // Square -> its place along the swept unit, 0 at the start and 1 at the end.
    private var swept: Map<Int, Float> = emptyMap()

    /** The scale to draw square [i] at: 1 unless the last move changed it. */
    fun scale(i: Int): Float = if (settle < 1f && i in changed) 1f + SETTLE_SCALE * (1f - settle) else 1f

    /** How strongly square [i] glows, 0 to 1: 0 unless a unit holding it has just been finished. */
    fun glow(i: Int): Float {
        if (sweep >= 1f) return 0f
        val at = swept[i] ?: return 0f
        return sweepGlow(sweep, at)
    }

    internal fun observe(tick: Int, cells: List<Any?>, units: List<IntArray>, done: BooleanArray, still: Boolean) {
        val moved = this.tick >= 0 && tick != this.tick && !still
        if (moved && this.cells.size == cells.size) {
            val diff = cells.indices.filterTo(HashSet()) { cells[it] != this.cells[it] }
            if (diff.isNotEmpty()) {
                changed = diff
                settle = 0f
                settleRun++
            }
        }
        if (moved && this.done.size == done.size) {
            val fresh = HashMap<Int, Float>()
            for (u in done.indices) {
                if (!done[u] || this.done[u]) continue
                val squares = units[u]
                squares.forEachIndexed { k, sq ->
                    val at = if (squares.size == 1) 0.5f else k.toFloat() / (squares.size - 1)
                    // A square in two finished units at once glows with the earlier of the two.
                    fresh[sq] = minOf(fresh[sq] ?: at, at)
                }
            }
            if (fresh.isNotEmpty()) {
                swept = fresh
                sweep = 0f
                sweepRun++
            }
        }
        this.tick = tick
        this.cells = cells
        this.done = done
    }

    companion object {
        /** One that never plays, for a motif or a preview drawn with a board's own code. */
        val Still = MoveFeel()
    }
}

/**
 * The glow at [at] (0..1 along the unit) when the sweep is [t] of the way through: a soft band that enters
 * before the start and leaves after the end, so every square is lit once and none is lit at either end.
 */
internal fun sweepGlow(t: Float, at: Float): Float {
    val front = -SWEEP_WIDTH + t * (1f + 2 * SWEEP_WIDTH)
    return (1f - abs(at - front) / SWEEP_WIDTH).coerceIn(0f, 1f)
}

/**
 * The move feel for a board whose squares are [cells] (one value per square, whatever the board stores: compared
 * with `==` to find what a move changed) and whose units are [units] (each the squares of one, in the order the
 * glow should run), [complete] saying whether a unit is finished **by the rules**, never by the stored answer.
 * Only a move fires anything: a board loaded, restarted or undone into a finished unit stays quiet. Under reduced
 * motion nothing plays.
 */
@Composable
fun rememberMoveFeel(
    cells: List<Any?>,
    units: List<IntArray> = emptyList(),
    complete: (Int) -> Boolean = { false },
): MoveFeel {
    val feel = remember { MoveFeel() }
    feel.observe(LocalMoveCount.current, cells, units, BooleanArray(units.size) { complete(it) }, LocalReducedMotion.current)
    LaunchedEffect(feel.settleRun) {
        if (feel.settleRun > 0) animate(0f, 1f, animationSpec = tween(SETTLE_MS)) { v, _ -> feel.settle = v }
    }
    LaunchedEffect(feel.sweepRun) {
        if (feel.sweepRun > 0) animate(0f, 1f, animationSpec = tween(SWEEP_MS)) { v, _ -> feel.sweep = v }
    }
    return feel
}

/** For a board built of one composable per square: square [i] settles, and glows in [glow] (at most 35%). */
fun Modifier.moveFeel(feel: MoveFeel, i: Int, glow: Color): Modifier = drawWithContent {
    val s = feel.scale(i)
    if (s == 1f) drawContent() else scale(s) { this@drawWithContent.drawContent() }
    val g = feel.glow(i)
    if (g > 0f) drawRect(glow.copy(alpha = GLOW_ALPHA * g))
}

/** The strongest a finished unit's glow is drawn: a wash, never a fill. */
const val GLOW_ALPHA = 0.35f

/** A [w] x [h] grid's rows (top to bottom, each left to right), then its columns (each top to bottom). */
fun lineUnits(w: Int, h: Int): List<IntArray> =
    List(h) { r -> IntArray(w) { r * w + it } } + List(w) { c -> IntArray(h) { it * w + c } }

/**
 * For a board drawn on one canvas: [draw] square [i] at its settle scale about the middle of the square at
 * [topLeft] and [size], then its glow in [glow] over it.
 */
inline fun DrawScope.feelSquare(feel: MoveFeel, i: Int, topLeft: Offset, size: Size, glow: Color, draw: DrawScope.() -> Unit) {
    val s = feel.scale(i)
    if (s == 1f) draw() else scale(s, Offset(topLeft.x + size.width / 2, topLeft.y + size.height / 2)) { draw() }
    val g = feel.glow(i)
    if (g > 0f) drawRect(glow.copy(alpha = GLOW_ALPHA * g), topLeft, size)
}
