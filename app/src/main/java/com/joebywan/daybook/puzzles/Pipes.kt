package com.joebywan.daybook.puzzles

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.Rng
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Pipe shapes are direction bitmasks: bit 0 up, 1 right, 2 down, 3 left. Rotating a piece is a
 * rotation of those four bits, which is why the whole puzzle needs no shape table at all.
 */
private const val UP = 1
private const val RIGHT = 2
private const val DOWN = 4
private const val LEFT = 8

/**
 * Long enough to read as a turn rather than a flicker, short enough that a player tapping at speed
 * is never waiting on it. Taps apply to the state immediately and only the drawn angle lags, so
 * this is a ceiling on visual lag, not on input.
 */
private const val SPIN_MILLIS = 140

/** Degrees in the quarter turn a tap performs; the tile is drawn wound back by this and unwinds. */
private const val QUARTER = 90f

@Serializable
data class PipesState(
    val width: Int,
    val height: Int,
    val cells: List<Int>,
    /**
     * Where the water comes in. Part of the generated state rather than a constant so that a
     * restored board fills from the same tile it filled from before it was put down.
     */
    val source: Int,
    override val moves: Int = 0,
) : PuzzleState {

    override val solved: Boolean get() = Pipes.fullyJoined(this) && Pipes.connected(this)

    fun rotate(index: Int): PipesState =
        copy(
            cells = cells.toMutableList().also { it[index] = Pipes.rotateCw(it[index]) },
            moves = moves + 1,
        )
}

/**
 * Pipes — the "Net" rotation puzzle.
 *
 * The solved board is a random spanning tree of the grid, so there is always a way to join every
 * pipe into one network with no loose ends and no loops.
 */
object Pipes : PuzzleType {

    override val id = "pipes"
    override val displayName = "Pipes"
    override val tagline = "Rotate until every pipe joins up"
    override val accent = 0xFF48B9C4
    override val rules = listOf(
        "Tap a tile to rotate it a quarter turn.",
        "Water enters at the ringed tile; pipes joined back to it run full.",
        "Finish with no loose ends: every pipe opening must meet another.",
        "All the pipework must form one single connected network.",
    )

    fun rotateCw(mask: Int): Int {
        var out = 0
        if (mask and UP != 0) out = out or RIGHT
        if (mask and RIGHT != 0) out = out or DOWN
        if (mask and DOWN != 0) out = out or LEFT
        if (mask and LEFT != 0) out = out or UP
        return out
    }

    private fun shape(difficulty: Difficulty) = when (difficulty) {
        Difficulty.STANDARD -> 5 to 7
        Difficulty.HARD -> 6 to 9
        Difficulty.EXPERT -> 8 to 11
    }

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState {
        val rng = Rng(seed)
        val (w, h) = shape(difficulty)
        // The tree is grown outwards from here, so this cell is the natural mains inlet: every
        // other tile is downstream of it in the solution.
        val source = rng.nextInt(w * h)
        val solved = spanningTree(rng, w, h, source)

        // Scramble. Re-scramble in the unlikely event the board lands already solved.
        var cells: List<Int>
        do {
            cells = solved.map { mask ->
                var m = mask
                repeat(rng.nextInt(4)) { m = rotateCw(m) }
                m
            }
        } while (cells == solved && solved.any { it != 0 })

        return PipesState(w, h, cells, source)
    }

    /** Randomised depth-first spanning tree; each cell records the edges it keeps. */
    private fun spanningTree(rng: Rng, w: Int, h: Int, start: Int): List<Int> {
        val masks = MutableList(w * h) { 0 }
        val seen = BooleanArray(w * h)
        val stack = ArrayDeque<Int>()
        seen[start] = true
        stack.addLast(start)

        while (stack.isNotEmpty()) {
            val current = stack.last()
            val r = current / w
            val c = current % w
            val options = buildList {
                if (r > 0 && !seen[current - w]) add(UP to (current - w))
                if (r < h - 1 && !seen[current + w]) add(DOWN to (current + w))
                if (c > 0 && !seen[current - 1]) add(LEFT to (current - 1))
                if (c < w - 1 && !seen[current + 1]) add(RIGHT to (current + 1))
            }
            if (options.isEmpty()) {
                stack.removeLast()
                continue
            }
            val (direction, next) = rng.pick(options)
            masks[current] = masks[current] or direction
            masks[next] = masks[next] or opposite(direction)
            seen[next] = true
            stack.addLast(next)
        }
        return masks
    }

    private fun opposite(direction: Int) = when (direction) {
        UP -> DOWN
        DOWN -> UP
        LEFT -> RIGHT
        else -> LEFT
    }

    /** True when no opening points at a wall or at a tile that does not open back. */
    fun fullyJoined(s: PipesState): Boolean {
        for (i in s.cells.indices) {
            val r = i / s.width
            val c = i % s.width
            val mask = s.cells[i]
            if (mask and UP != 0 && (r == 0 || s.cells[i - s.width] and DOWN == 0)) return false
            if (mask and DOWN != 0 && (r == s.height - 1 || s.cells[i + s.width] and UP == 0)) return false
            if (mask and LEFT != 0 && (c == 0 || s.cells[i - 1] and RIGHT == 0)) return false
            if (mask and RIGHT != 0 && (c == s.width - 1 || s.cells[i + 1] and LEFT == 0)) return false
        }
        return true
    }

    /**
     * The tiles the water has actually reached: those joined back to [PipesState.source] through
     * openings that meet on both sides.
     *
     * The board is coloured by this and the win condition is decided by it, so what the player
     * sees filling up and what counts as finished cannot drift apart.
     */
    fun filled(s: PipesState): Set<Int> {
        val start = s.source
        if (start !in s.cells.indices) return emptySet()

        val seen = BooleanArray(s.cells.size)
        val stack = ArrayDeque<Int>()
        seen[start] = true
        stack.addLast(start)
        val reached = mutableSetOf(start)

        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            val mask = s.cells[i]
            val r = i / s.width
            val c = i % s.width

            fun flowTo(opening: Int, inBounds: Boolean, j: Int) {
                if (!inBounds || mask and opening == 0) return
                if (s.cells[j] and opposite(opening) == 0 || seen[j]) return
                seen[j] = true
                reached += j
                stack.addLast(j)
            }

            flowTo(UP, r > 0, i - s.width)
            flowTo(DOWN, r < s.height - 1, i + s.width)
            flowTo(LEFT, c > 0, i - 1)
            flowTo(RIGHT, c < s.width - 1, i + 1)
        }
        return reached
    }

    /**
     * A fully joined board has exactly as many edges as the spanning tree it came from, so it is
     * either one tree or a cycle plus leftovers. This is the check that rules the latter out.
     */
    fun connected(s: PipesState): Boolean = filled(s).size == s.cells.size

    /**
     * Every tile starts at a random rotation, so no single one can be settled on its own — the
     * network only resolves once the board is read as a whole.
     */
    override val offersHints = false

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as PipesState
        val scheme = MaterialTheme.colorScheme
        val wet = Color(accent)
        val dry = scheme.onSurfaceVariant.copy(alpha = 0.38f)
        val wetCells = remember(s.cells, s.source) { filled(s) }

        // One angle per tile, held outside the state so a rotation can be shown turning while the
        // board itself has already moved on. Only the grid shape resets them.
        val spins = remember(s.width, s.height) { List(s.width * s.height) { Animatable(0f) } }
        val shown = remember(s.width, s.height) { s.cells.toMutableList() }

        LaunchedEffect(s.cells) {
            val before = shown.toList()
            shown.clear()
            shown.addAll(s.cells)
            if (before.size != s.cells.size) return@LaunchedEffect

            for (i in s.cells.indices) {
                if (before[i] == s.cells[i]) continue
                // An undo turns the other way. Anything else — a restored board, a new puzzle —
                // is not a turn at all and should simply appear.
                val wound = when {
                    rotateCw(before[i]) == s.cells[i] -> -QUARTER
                    rotateCw(s.cells[i]) == before[i] -> QUARTER
                    else -> 0f
                }
                val spin = spins[i]
                launch {
                    if (wound == 0f) {
                        spin.snapTo(0f)
                    } else {
                        // Winding back from wherever the tile currently is, rather than from
                        // square on, means a second tap during the first turn keeps going round
                        // instead of jumping back a quarter.
                        spin.snapTo(spin.value + wound)
                        spin.animateTo(0f, tween(SPIN_MILLIS, easing = FastOutSlowInEasing))
                    }
                }
            }
        }

        BoxWithConstraints(Modifier.fillMaxWidth().padding(16.dp)) {
            val cell = maxWidth / s.width
            val cellPx = with(LocalDensity.current) { cell.toPx() }
            Canvas(
                Modifier
                    .width(cell * s.width)
                    .height(cell * s.height)
                    .pointerInput(s, interactive) {
                        if (!interactive) return@pointerInput
                        detectTapGestures { offset: Offset ->
                            val c = (offset.x / cellPx).toInt().coerceIn(0, s.width - 1)
                            val r = (offset.y / cellPx).toInt().coerceIn(0, s.height - 1)
                            onState(s.rotate(r * s.width + c))
                        }
                    }
            ) {
                val stroke = cellPx * 0.18f
                for (i in s.cells.indices) {
                    val r = i / s.width
                    val c = i % s.width
                    val cx = (c + 0.5f) * cellPx
                    val cy = (r + 0.5f) * cellPx
                    val mask = s.cells[i]
                    val isSource = i == s.source
                    val pipeColour = if (i in wetCells) wet else dry

                    drawRect(
                        color = if (isSource) wet.copy(alpha = 0.16f) else scheme.surface,
                        topLeft = Offset(c * cellPx + 1f, r * cellPx + 1f),
                        size = Size(cellPx - 2f, cellPx - 2f),
                    )

                    if (mask != 0) {
                        // A single-ended pipe is an endpoint; draw it as a stub with a cap.
                        val endpoint = Integer.bitCount(mask) == 1
                        rotate(degrees = spins[i].value, pivot = Offset(cx, cy)) {
                            fun arm(dx: Float, dy: Float) {
                                drawLine(
                                    color = pipeColour,
                                    start = Offset(cx, cy),
                                    end = Offset(cx + dx * cellPx * 0.5f, cy + dy * cellPx * 0.5f),
                                    strokeWidth = stroke,
                                    cap = StrokeCap.Round,
                                )
                            }
                            if (mask and UP != 0) arm(0f, -1f)
                            if (mask and DOWN != 0) arm(0f, 1f)
                            if (mask and LEFT != 0) arm(-1f, 0f)
                            if (mask and RIGHT != 0) arm(1f, 0f)

                            drawCircle(
                                color = pipeColour,
                                radius = if (endpoint) cellPx * 0.20f else stroke * 0.62f,
                                center = Offset(cx, cy),
                            )
                        }
                    }

                    // The inlet does not turn with its tile: it marks a fixed point on the board,
                    // and a ring that spun would read as just another pipe.
                    if (isSource) {
                        drawCircle(color = wet, radius = cellPx * 0.17f, center = Offset(cx, cy))
                        drawCircle(
                            color = wet,
                            radius = cellPx * 0.31f,
                            center = Offset(cx, cy),
                            style = Stroke(width = stroke * 0.5f),
                        )
                    }
                }
            }
        }
    }
}
