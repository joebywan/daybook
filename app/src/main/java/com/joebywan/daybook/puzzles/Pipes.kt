package com.joebywan.daybook.puzzles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleState
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.Rng

/**
 * Pipe shapes are direction bitmasks: bit 0 up, 1 right, 2 down, 3 left. Rotating a piece is a
 * rotation of those four bits, which is why the whole puzzle needs no shape table at all.
 */
private const val UP = 1
private const val RIGHT = 2
private const val DOWN = 4
private const val LEFT = 8

data class PipesState(
    val width: Int,
    val height: Int,
    val cells: List<Int>,
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
        val solved = spanningTree(rng, w, h)

        // Scramble. Re-scramble in the unlikely event the board lands already solved.
        var cells: List<Int>
        do {
            cells = solved.map { mask ->
                var m = mask
                repeat(rng.nextInt(4)) { m = rotateCw(m) }
                m
            }
        } while (cells == solved && solved.any { it != 0 })

        return PipesState(w, h, cells)
    }

    /** Randomised depth-first spanning tree; each cell records the edges it keeps. */
    private fun spanningTree(rng: Rng, w: Int, h: Int): List<Int> {
        val masks = MutableList(w * h) { 0 }
        val seen = BooleanArray(w * h)
        val stack = ArrayDeque<Int>()
        val start = rng.nextInt(w * h)
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
     * A fully joined board has exactly as many edges as the spanning tree it came from, so it is
     * either one tree or a cycle plus leftovers. This is the check that rules the latter out.
     */
    fun connected(s: PipesState): Boolean {
        val seen = BooleanArray(s.cells.size)
        val stack = ArrayDeque<Int>()
        stack.addLast(0)
        seen[0] = true
        var count = 1
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            val mask = s.cells[i]
            val r = i / s.width
            val c = i % s.width
            val neighbours = buildList {
                if (mask and UP != 0 && r > 0) add(i - s.width)
                if (mask and DOWN != 0 && r < s.height - 1) add(i + s.width)
                if (mask and LEFT != 0 && c > 0) add(i - 1)
                if (mask and RIGHT != 0 && c < s.width - 1) add(i + 1)
            }
            neighbours.forEach {
                if (!seen[it]) {
                    seen[it] = true
                    count++
                    stack.addLast(it)
                }
            }
        }
        return count == s.cells.size
    }

    /**
     * Every tile starts at a random rotation, so no single one can be settled on its own — the
     * network only resolves once the board is read as a whole.
     */
    override val offersHints = false

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as PipesState
        val scheme = MaterialTheme.colorScheme
        val pipeColour = Color(accent)

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

                    drawRect(
                        color = scheme.surface,
                        topLeft = Offset(c * cellPx + 1f, r * cellPx + 1f),
                        size = androidx.compose.ui.geometry.Size(cellPx - 2f, cellPx - 2f),
                    )

                    if (mask == 0) continue

                    // A single-ended pipe is an endpoint; draw it as a stub with a cap.
                    val endpoint = Integer.bitCount(mask) == 1
                    fun arm(dx: Float, dy: Float) {
                        drawLine(
                            color = pipeColour,
                            start = Offset(cx, cy),
                            end = Offset(cx + dx * cellPx * 0.5f, cy + dy * cellPx * 0.5f),
                            strokeWidth = stroke,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
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
        }
    }
}
