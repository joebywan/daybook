package com.joebywan.daybook.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.isSpecified
import com.joebywan.daybook.core.Tones
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * How many moves the play screen has been handed this visit. Only `PlayScreen.push` raises it: undo, restart, a
 * restored save and the walkthrough (which provides nothing, so it stays 0) never do, which is how [rememberMoveFeel]
 * tells a move the player made from a board arriving any other way.
 */
val LocalMoveCount = compositionLocalOf { 0 }

/**
 * The sounds of a board being played. The play screen provides [LocalTones] around its board alone, so a walkthrough, a
 * practice or a motif drawn with the same code stays silent.
 */
val LocalBoardTones = staticCompositionLocalOf<(Int) -> Unit> { {} }

/** How many times Undo has taken something back this visit (`PlayScreen.undo`): the squares it changed dip and return. */
val LocalUndoCount = compositionLocalOf { 0 }

/** How many moves this visit have solved the board (`PlayScreen.push`): the solve wave plays when it goes up. */
val LocalSolveCount = compositionLocalOf { 0 }

/**
 * Whether a board composed now is arriving: true on the play screen until the board has been shown once (saved, so
 * a rotation is not an arrival), false in the walkthrough and anywhere else. Its squares then fade in.
 */
val LocalBoardArrives = compositionLocalOf { false }

private const val SETTLE_MS = 120
private const val SWEEP_MS = 400
private const val ARRIVE_MS = 250
private const val SETTLE_SCALE = 0.08f
/** A finished unit's pop, travelling with its glow. Drawn only: nothing changes size or position. */
private const val POP_SCALE = 0.07f
/** The sweep's half width, as a share of the unit it runs along. */
private const val SWEEP_WIDTH = 0.35f
/** The share of the arrival one square's own fade takes; the rest staggers the squares in reading order. */
private const val ARRIVE_EACH = 0.4f

/** The solve wave's length; the play screen holds the finished frame back by as much, after [SOLVE_WAVE_DELAY_MS]. */
const val SOLVE_WAVE_MS = 350

/** The hit stop before a win's thump: the move's own settle and glow hold still for it. State is not held, only drawing. */
const val WIN_FREEZE_MS = 50
const val WIN_THUMP_MS = 220
/** The wave starts on the thump's rebound. */
const val SOLVE_WAVE_DELAY_MS = WIN_FREEZE_MS + 80

/**
 * How far down the whole board is at [t] (0..1) through a win's thump, as a share of its depth: a fast drop, a rise a quarter
 * of the way past rest, and back. Vertical by the caller's use: small sideways motion means a mistake.
 */
fun winThump(t: Float): Float {
    if (t <= 0f || t >= 1f) return 0f
    if (t < THUMP_DROP) return sin(t / THUMP_DROP * PI.toFloat() / 2)
    val u = (t - THUMP_DROP) / (1f - THUMP_DROP)
    return cos(2 * PI.toFloat() * u) * (1f - u) * (1f - u)
}

private const val THUMP_DROP = 0.18f

/**
 * The feel of a move: the squares it changed settle (drawn 8% large, easing back over 120 ms; an undo dips them 8%
 * small instead), a unit it finished (a row, a box, a cage, a line, a tube) has a soft glow run along it once
 * (400 ms) with a small pop travelling along with it and one note of the board's phrase, the move that solves the board runs one wave of the same glow across every square in reading order
 * (350 ms), and a board arriving fades its squares in, in reading order (250 ms). Read [scale], [glow] and [alpha]
 * in the draw phase only, so a running animation repaints the board without recomposing it. Nothing here is state
 * of the game; nothing changes size or position.
 */
@Stable
class MoveFeel internal constructor() {
    internal var settle by mutableFloatStateOf(1f)
    internal var sweep by mutableFloatStateOf(1f)
    internal var wave by mutableFloatStateOf(1f)
    internal var arrive by mutableFloatStateOf(1f)
    internal var settleRun = 0
    internal var sweepRun = 0
    internal var waveRun = 0
    internal var arriveRun = 0
    /** Moves that finished a unit but not the board, reduced motion or not: each plays [unitStep]'s note. */
    internal var unitRun = 0
    /** How far up its phrase the board stood after the last of them (`Tones.phraseStep`). */
    internal var unitStep = 0
    private var tick = -1
    private var undos = 0
    private var solves = 0
    private var shrink = false
    private var cells: List<Any?> = emptyList()
    private var done = BooleanArray(0)
    private var changed: Set<Int> = emptySet()
    // Square -> its place along the swept unit, 0 at the start and 1 at the end.
    private var swept: Map<Int, Float> = emptyMap()

    /** The scale to draw square [i] at: 1 unless the last move or undo changed it. */
    fun scale(i: Int): Float {
        var s = if (settle < 1f && i in changed) 1f + (if (shrink) -SETTLE_SCALE else SETTLE_SCALE) * (1f - settle) else 1f
        if (sweep < 1f) swept[i]?.let { s += POP_SCALE * sweepGlow(sweep, it) }
        return s
    }

    /** How strongly square [i] glows, 0 to 1: 0 unless a unit holding it has just been finished, or the board solved. */
    fun glow(i: Int): Float {
        var g = 0f
        if (sweep < 1f) swept[i]?.let { g = sweepGlow(sweep, it) }
        if (wave < 1f) g = maxOf(g, sweepGlow(wave, place(i)))
        return g
    }

    /** How opaque square [i] is drawn: 1 unless the board is arriving and its turn has not fully come. */
    fun alpha(i: Int): Float =
        if (arrive >= 1f) 1f else ((arrive - place(i) * (1f - ARRIVE_EACH)) / ARRIVE_EACH).coerceIn(0f, 1f)

    // Square i's place in reading order, 0 to 1.
    private fun place(i: Int): Float = if (cells.size <= 1) 0.5f else (i.toFloat() / (cells.size - 1)).coerceIn(0f, 1f)

    internal fun observe(
        tick: Int, cells: List<Any?>, units: List<IntArray>, done: BooleanArray, still: Boolean,
        undos: Int = 0, solves: Int = 0, arrives: Boolean = false,
    ) {
        val first = this.tick < 0
        if (first && arrives && !still) {
            arrive = 0f
            arriveRun++
        }
        val played = !first && tick != this.tick
        val moved = played && !still
        val undone = !first && undos != this.undos && !still
        if ((moved || undone) && this.cells.size == cells.size) {
            val diff = cells.indices.filterTo(HashSet()) { cells[it] != this.cells[it] }
            if (diff.isNotEmpty()) {
                changed = diff
                shrink = !moved
                settle = 0f
                settleRun++
            }
        }
        if (played && this.done.size == done.size) {
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
            // The chime has it when this move solved the board.
            if (fresh.isNotEmpty() && solves == this.solves) {
                unitStep = Tones.phraseStep(done.count { it }, done.size)
                unitRun++
            }
            if (fresh.isNotEmpty() && !still) {
                swept = fresh
                sweep = 0f
                sweepRun++
            }
        }
        if (!first && solves != this.solves && !still) {
            wave = 0f
            waveRun++
        }
        this.tick = tick
        this.undos = undos
        this.solves = solves
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
 * The move feel for a board whose squares are [cells] (one value per square, in reading order, whatever the board
 * stores: compared with `==` to find what a move changed) and whose units are [units] (each the squares of one, in the
 * order the glow should run), [complete] saying whether a unit is finished **by the rules**, never by the stored answer.
 * Only a move fires the settle and the glow: a board loaded, restarted or undone into a finished unit stays quiet. Under
 * reduced motion nothing moves; the unit's tone still plays (sound has its own switch).
 */
@Composable
fun rememberMoveFeel(
    cells: List<Any?>,
    units: List<IntArray> = emptyList(),
    complete: (Int) -> Boolean = { false },
): MoveFeel {
    val feel = remember { MoveFeel() }
    feel.observe(
        LocalMoveCount.current, cells, units, BooleanArray(units.size) { complete(it) }, LocalReducedMotion.current,
        LocalUndoCount.current, LocalSolveCount.current, LocalBoardArrives.current,
    )
    val tones = LocalBoardTones.current
    LaunchedEffect(feel.unitRun) { if (feel.unitRun > 0) tones(Tones.step(feel.unitStep)) }
    // The move that solved stays on its first frame through the win's freeze. No move follows it.
    val hold = if (LocalSolveCount.current > 0) WIN_FREEZE_MS else 0
    Play(feel.settleRun, tween(SETTLE_MS), hold) { feel.settle = it }
    Play(feel.sweepRun, tween(SWEEP_MS), hold) { feel.sweep = it }
    Play(feel.waveRun, tween(SOLVE_WAVE_MS, easing = LinearEasing), SOLVE_WAVE_DELAY_MS) { feel.wave = it }
    Play(feel.arriveRun, tween(ARRIVE_MS, easing = LinearEasing)) { feel.arrive = it }
    return feel
}

/** Runs [set] from 0 to 1 over [spec], after [holdMs], each time [run] goes up (never for 0, the run before any). */
@Composable
private fun Play(run: Int, spec: AnimationSpec<Float>, holdMs: Int = 0, set: (Float) -> Unit) {
    LaunchedEffect(run) {
        if (run > 0) {
            delay(holdMs.toLong())
            animate(0f, 1f, animationSpec = spec) { v, _ -> set(v) }
        }
    }
}

/** For a board built of one composable per square: square [i] arrives, settles, and glows in [glow] (at most 35%). */
fun Modifier.moveFeel(feel: MoveFeel, i: Int, glow: Color): Modifier =
    drawWithContent { feelSquare(feel, i, Offset.Zero, size, glow) { this@drawWithContent.drawContent() } }

/** The strongest a finished unit's glow is drawn: a wash, never a fill. */
const val GLOW_ALPHA = 0.35f

/** A [w] x [h] grid's rows (top to bottom, each left to right), then its columns (each top to bottom). */
fun lineUnits(w: Int, h: Int): List<IntArray> =
    List(h) { r -> IntArray(w) { r * w + it } } + List(w) { c -> IntArray(h) { it * w + c } }

/**
 * For a board drawn on one canvas: [draw] square [i] at its arrival's opacity and its settle scale about the middle of
 * the square at [topLeft] and [size], then its glow in [glow] over it.
 */
inline fun DrawScope.feelSquare(feel: MoveFeel, i: Int, topLeft: Offset, size: Size, glow: Color, draw: DrawScope.() -> Unit) {
    val a = feel.alpha(i)
    if (a <= 0f) return
    // A little past the square, for a mark drawn over its edge.
    if (a < 1f) drawContext.canvas.saveLayer(Rect(topLeft, size).inflate(size.minDimension / 4), Paint().apply { alpha = a })
    val s = feel.scale(i)
    if (s == 1f) draw() else scale(s, Offset(topLeft.x + size.width / 2, topLeft.y + size.height / 2)) { draw() }
    val g = feel.glow(i)
    if (g > 0f && glow.isSpecified) drawRect(glow.copy(alpha = GLOW_ALPHA * g), topLeft, size)
    if (a < 1f) drawContext.canvas.restore()
}
