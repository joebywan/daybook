package com.joebywan.daybook.puzzles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleState
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.Rng

data class SnapState(
    val width: Int,
    val height: Int,
    /** Cell index -> waypoint number, or 0 where there is no number. */
    val waypoints: List<Int>,
    val path: List<Int>,
    override val moves: Int = 0,
) : PuzzleState {

    val cellCount: Int get() = width * height

    override val solved: Boolean
        get() = path.size == cellCount && Snap.respectsOrder(this, path)

    fun reset(): SnapState = copy(path = emptyList(), moves = moves + 1)
}

/**
 * Snap — one unbroken line through every square, touching the numbers in order.
 *
 * The solution is a Hamiltonian path laid down first; numbers are then dropped onto it, one at a
 * time, until no other Hamiltonian path obeys them.
 */
object Snap : PuzzleType {

    override val id = "snap"
    override val displayName = "Snap"
    override val tagline = "One line, every square, numbers in order"
    override val accent = 0xFFD9584C
    override val rules = listOf(
        "Draw one continuous line from 1 to the highest number.",
        "The line must pass through every square exactly once.",
        "It must reach the numbered squares in ascending order.",
        "Drag to draw. Drag back along the line to rub it out.",
    )

    private fun shape(difficulty: Difficulty) = when (difficulty) {
        Difficulty.STANDARD -> 5 to 5
        Difficulty.HARD -> 6 to 6
        Difficulty.EXPERT -> 6 to 7
    }

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState {
        val (w, h) = shape(difficulty)

        repeat(80) { attempt ->
            val rng = Rng(seed + attempt)
            val path = hamiltonian(rng, w, h) ?: return@repeat

            // Start and end are always numbered; add interior numbers until the answer is forced.
            val marks = MutableList(w * h) { 0 }
            val chosen = mutableListOf(0, path.lastIndex)

            fun relabel() {
                for (i in marks.indices) marks[i] = 0
                chosen.sorted().forEachIndexed { rank, at -> marks[path[at]] = rank + 1 }
            }
            relabel()

            var guard = 0
            while (countPaths(w, h, marks) != 1 && guard++ < w * h) {
                val candidates = (1 until path.lastIndex).filterNot { it in chosen }
                if (candidates.isEmpty()) break
                chosen += rng.pick(candidates)
                relabel()
            }
            if (countPaths(w, h, marks) == 1) {
                return SnapState(w, h, marks.toList(), emptyList())
            }
        }

        // Fallback: number every square along a simple boustrophedon path.
        val marks = MutableList(w * h) { 0 }
        val path = boustrophedon(w, h)
        path.forEachIndexed { rank, cell -> marks[cell] = rank + 1 }
        return SnapState(w, h, marks.toList(), emptyList())
    }

    private fun boustrophedon(w: Int, h: Int): List<Int> = buildList {
        for (r in 0 until h) {
            val cols = if (r % 2 == 0) 0 until w else (w - 1) downTo 0
            for (c in cols) add(r * w + c)
        }
    }

    private fun neighbours(cell: Int, w: Int, h: Int): List<Int> {
        val r = cell / w
        val c = cell % w
        return buildList {
            if (r > 0) add(cell - w)
            if (r < h - 1) add(cell + w)
            if (c > 0) add(cell - 1)
            if (c < w - 1) add(cell + 1)
        }
    }

    /** Randomised search for a Hamiltonian path, starting from a random square. */
    private fun hamiltonian(rng: Rng, w: Int, h: Int): List<Int>? {
        val n = w * h
        val seen = BooleanArray(n)
        val path = ArrayList<Int>(n)
        var nodes = 0

        fun extend(cell: Int): Boolean {
            if (nodes++ > 200_000) return false
            seen[cell] = true
            path += cell
            if (path.size == n) return true
            // Warnsdorff-ish: try the most hemmed-in neighbour first.
            val options = rng.shuffled(neighbours(cell, w, h).filter { !seen[it] })
                .sortedBy { neighbours(it, w, h).count { next -> !seen[next] } }
            for (next in options) {
                if (extend(next)) return true
            }
            seen[cell] = false
            path.removeAt(path.lastIndex)
            return false
        }

        return if (extend(rng.nextInt(n))) path.toList() else null
    }

    fun respectsOrder(s: SnapState, path: List<Int>): Boolean {
        val seen = path.mapNotNull { cell ->
            s.waypoints[cell].takeIf { it > 0 }
        }
        return seen == seen.sorted() && seen == (1..seen.size).toList() &&
            seen.size == s.waypoints.count { it > 0 }
    }

    /** Counts Hamiltonian paths obeying the numbers, stopping at two. */
    private fun countPaths(w: Int, h: Int, marks: List<Int>): Int {
        val n = w * h
        val total = marks.count { it > 0 }
        val start = marks.indexOf(1)
        if (start < 0) return 0
        val seen = BooleanArray(n)
        var found = 0
        var nodes = 0

        fun walk(cell: Int, depth: Int, nextLabel: Int) {
            if (found >= 2 || nodes++ > 400_000) return
            val label = marks[cell]
            var expecting = nextLabel
            if (label > 0) {
                if (label != nextLabel) return
                expecting = nextLabel + 1
            }
            seen[cell] = true
            if (depth == n) {
                if (expecting == total + 1) found++
            } else {
                for (next in neighbours(cell, w, h)) {
                    if (!seen[next]) walk(next, depth + 1, expecting)
                    if (found >= 2) break
                }
            }
            seen[cell] = false
        }

        walk(start, 1, 1)
        return found
    }

    // ---- play ---------------------------------------------------------------------------------

    /**
     * The path is a single chain, so any tile a hint filled in would pin down the stretch either
     * side of it — there is no small enough piece of the answer to give away.
     */
    override val offersHints = false

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as SnapState
        val scheme = MaterialTheme.colorScheme
        val measurer = rememberTextMeasurer()

        BoxWithConstraints(Modifier.fillMaxWidth().padding(20.dp)) {
            val step = maxWidth / s.width
            val stepPx = with(LocalDensity.current) { step.toPx() }

            fun cellAt(offset: Offset): Int {
                val c = (offset.x / stepPx).toInt().coerceIn(0, s.width - 1)
                val r = (offset.y / stepPx).toInt().coerceIn(0, s.height - 1)
                return r * s.width + c
            }

            /** Appends or rubs out one square, following the drag. */
            fun extend(current: SnapState, cell: Int): SnapState {
                val path = current.path
                if (path.isEmpty()) {
                    // A line may only begin at 1.
                    return if (current.waypoints[cell] == 1) current.copy(path = listOf(cell), moves = current.moves + 1)
                    else current
                }
                if (cell == path.last()) return current
                if (path.size >= 2 && cell == path[path.size - 2]) {
                    return current.copy(path = path.dropLast(1), moves = current.moves + 1)
                }
                if (cell in path) return current
                if (cell !in neighbours(path.last(), current.width, current.height)) return current
                return current.copy(path = path + cell, moves = current.moves + 1)
            }

            Canvas(
                Modifier
                    .width(step * s.width)
                    .height(step * s.height)
                    .pointerInput(s.waypoints, interactive) {
                        if (!interactive) return@pointerInput
                        var working = s
                        detectDragGestures(
                            onDragStart = { offset ->
                                working = s
                                val cell = cellAt(offset)
                                // Starting on the line trims it back to that point.
                                working = if (cell in working.path) {
                                    working.copy(path = working.path.take(working.path.indexOf(cell) + 1))
                                } else {
                                    extend(working, cell)
                                }
                                onState(working)
                            },
                            onDrag = { change, _ ->
                                val next = extend(working, cellAt(change.position))
                                if (next !== working) {
                                    working = next
                                    onState(working)
                                }
                            },
                        )
                    }
            ) {
                for (r in 0 until s.height) {
                    for (c in 0 until s.width) {
                        drawRoundRect(
                            color = scheme.surfaceVariant,
                            topLeft = Offset(c * stepPx + 2f, r * stepPx + 2f),
                            size = Size(stepPx - 4f, stepPx - 4f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(stepPx * 0.16f),
                        )
                    }
                }

                if (s.path.size >= 2) {
                    val line = androidx.compose.ui.graphics.Path()
                    s.path.forEachIndexed { i, cell ->
                        val x = (cell % s.width + 0.5f) * stepPx
                        val y = (cell / s.width + 0.5f) * stepPx
                        if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
                    }
                    drawPath(
                        line,
                        Color(accent),
                        style = Stroke(width = stepPx * 0.26f, cap = StrokeCap.Round),
                    )
                }

                for (cell in 0 until s.cellCount) {
                    val number = s.waypoints[cell]
                    if (number == 0) continue
                    val cx = (cell % s.width + 0.5f) * stepPx
                    val cy = (cell / s.width + 0.5f) * stepPx
                    drawCircle(scheme.onBackground, radius = stepPx * 0.30f, center = Offset(cx, cy))
                    val layout = measurer.measure(
                        number.toString(),
                        TextStyle(
                            color = scheme.background,
                            fontSize = (step.value * 0.30f).sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    drawText(
                        layout,
                        topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f),
                    )
                }
            }
        }
    }
}
