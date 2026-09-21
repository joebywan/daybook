package com.joebywan.daybook.puzzles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.Rng
import kotlinx.serialization.Serializable

@Serializable
enum class Mark { EMPTY, BLOCKED, KING }

@Serializable
data class KingsState(
    val size: Int,
    val region: List<Int>,
    val marks: List<Mark>,
    val solution: Set<Int>,
    override val moves: Int = 0,
) : PuzzleState {

    override val solved: Boolean
        get() = marks.indices.filter { marks[it] == Mark.KING }.toSet() == solution

    /** Kings that break a rule, for live feedback. */
    fun conflicts(): Set<Int> {
        val kings = marks.indices.filter { marks[it] == Mark.KING }
        val bad = mutableSetOf<Int>()
        for (a in kings) for (b in kings) {
            if (a >= b) continue
            val ra = a / size; val ca = a % size
            val rb = b / size; val cb = b % size
            val touching = kotlin.math.abs(ra - rb) <= 1 && kotlin.math.abs(ca - cb) <= 1
            if (ra == rb || ca == cb || region[a] == region[b] || touching) {
                bad += a
                bad += b
            }
        }
        return bad
    }

    fun cycle(index: Int): KingsState {
        val next = when (marks[index]) {
            Mark.EMPTY -> Mark.BLOCKED
            Mark.BLOCKED -> Mark.KING
            Mark.KING -> Mark.EMPTY
        }
        return copy(marks = marks.toMutableList().also { it[index] = next }, moves = moves + 1)
    }

    /** Every cell a king on [index] rules out, the square it stands on included. */
    fun eliminatedBy(index: Int): Set<Int> {
        val r = index / size
        val c = index % size
        val out = mutableSetOf<Int>()
        for (k in 0 until size) {
            out += r * size + k
            out += k * size + c
        }
        marks.indices.filterTo(out) { region[it] == region[index] }
        for (dr in -1..1) for (dc in -1..1) {
            val nr = r + dr
            val nc = c + dc
            if (nr in 0 until size && nc in 0 until size) out += nr * size + nc
        }
        return out
    }

    /**
     * Derived from the kings on the board rather than written into [marks] on placement: stored
     * auto-marks would have to be unpicked when a king moves, and telling them apart from the
     * player's own pencilling — two kings can rule out the same square — is bookkeeping that goes
     * wrong the first time someone undoes. Recomputing costs a few hundred set inserts per frame
     * and keeps undo, restart and hints correct for free.
     *
     * Cells holding kings are excluded: a king is not a ruled-out square, it is the answer.
     */
    fun eliminated(): Set<Int> {
        val kings = marks.indices.filter { marks[it] == Mark.KING }
        if (kings.isEmpty()) return emptySet()
        val out = mutableSetOf<Int>()
        kings.forEach { out += eliminatedBy(it) }
        return out - kings.toSet()
    }

    /**
     * The mark a sweep starting on [index] lays down, or null when the gesture should paint
     * nothing. A drag repeats one decision taken at its start; cycling each cell as the finger
     * crossed it would leave a trail nobody could predict.
     */
    fun sweepMark(index: Int): Mark? = when (marks[index]) {
        Mark.EMPTY -> Mark.BLOCKED
        Mark.BLOCKED -> Mark.EMPTY
        // Kings are placed deliberately; a sweep that began on one is almost certainly a stray
        // finger rather than a request to clear the board's most expensive decision.
        Mark.KING -> null
    }

    /**
     * Applies one mark to a whole swept run in a single state. The screen pushes an undo entry per
     * emission, so streaming a state per cell would cost a dozen taps on Undo to walk back one
     * gesture. Kings in the path are stepped over rather than overwritten.
     */
    fun paint(cells: Collection<Int>, mark: Mark): KingsState {
        val next = marks.toMutableList()
        var changed = 0
        for (i in cells) {
            if (next[i] == Mark.KING || next[i] == mark) continue
            next[i] = mark
            changed++
        }
        // A sweep stands in for the taps it replaced, so it scores as that many moves.
        return if (changed == 0) this else copy(marks = next, moves = moves + changed)
    }
}

/**
 * Kings — the one-per-row/column/region placement puzzle.
 *
 * Generation picks a legal king layout first, then grows the colour regions around it, and only
 * keeps the result if a solver confirms the regions admit exactly one layout.
 */
object Kings : PuzzleType {

    override val id = "kings"
    override val displayName = "Kings"
    override val tagline = "One crown per row, column and colour"
    override val accent = 0xFF9B6FD0
    override val rules = listOf(
        "Place exactly one king in every row, every column and every coloured region.",
        "No two kings may touch, not even diagonally.",
        "Tap once to pencil in a blocked square, twice for a king, three times to clear.",
        "Drag across a run of squares to mark them all in one sweep.",
        "Faint dots appear on squares a king already rules out.",
    )

    private val regionColours = listOf(
        0xFF7C6BB5, 0xFF4C86D9, 0xFF54B07A, 0xFFE0B23C, 0xFFD9584C,
        0xFF48B9C4, 0xFFD97FB0, 0xFF9A8264, 0xFF6FA86F, 0xFFB5705A,
    )

    private fun sizeFor(difficulty: Difficulty) = when (difficulty) {
        Difficulty.STANDARD -> 7
        Difficulty.HARD -> 8
        Difficulty.EXPERT -> 9
    }

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState {
        val n = sizeFor(difficulty)
        var rng = Rng(seed)

        repeat(400) { attempt ->
            val layout = randomLayout(rng, n)
            if (layout != null) {
                val regions = growRegions(rng, n, layout)
                if (countSolutions(n, regions) == 1) {
                    val kings = layout.mapIndexed { r, c -> r * n + c }.toSet()
                    return KingsState(n, regions, List(n * n) { Mark.EMPTY }, kings)
                }
            }
            rng = Rng(seed + attempt + 1)
        }

        // Fall back to a trivially legal board rather than failing to produce a puzzle.
        val layout = fallbackLayout(n)
        val regions = growRegions(Rng(seed), n, layout)
        return KingsState(n, regions, List(n * n) { Mark.EMPTY },
            layout.mapIndexed { r, c -> r * n + c }.toSet())
    }

    /** A column per row: all distinct, and never within one column of the row above. */
    private fun randomLayout(rng: Rng, n: Int): List<Int>? {
        val cols = MutableList(n) { -1 }
        val used = BooleanArray(n)
        fun place(row: Int): Boolean {
            if (row == n) return true
            for (c in rng.shuffled((0 until n).toList())) {
                if (used[c]) continue
                if (row > 0 && kotlin.math.abs(cols[row - 1] - c) <= 1) continue
                cols[row] = c
                used[c] = true
                if (place(row + 1)) return true
                used[c] = false
                cols[row] = -1
            }
            return false
        }
        return if (place(0)) cols.toList() else null
    }

    private fun fallbackLayout(n: Int): List<Int> =
        (0 until n).map { r -> (r * 2) % n }

    /**
     * Flood-grows one region from each king until the board is covered. Every region is connected
     * and holds exactly one king by construction.
     */
    private fun growRegions(rng: Rng, n: Int, layout: List<Int>): List<Int> {
        val region = MutableList(n * n) { -1 }
        layout.forEachIndexed { r, c -> region[r * n + c] = r }

        val frontier = mutableListOf<Int>()
        fun pushNeighbours(index: Int) {
            val r = index / n
            val c = index % n
            listOfNotNull(
                if (r > 0) index - n else null,
                if (r < n - 1) index + n else null,
                if (c > 0) index - 1 else null,
                if (c < n - 1) index + 1 else null,
            ).forEach { if (region[it] == -1) frontier += it }
        }
        (0 until n * n).filter { region[it] != -1 }.forEach(::pushNeighbours)

        while (frontier.isNotEmpty()) {
            val pick = rng.nextInt(frontier.size)
            val cell = frontier.removeAt(pick)
            if (region[cell] != -1) continue
            val r = cell / n
            val c = cell % n
            val owners = listOfNotNull(
                if (r > 0) region[cell - n] else null,
                if (r < n - 1) region[cell + n] else null,
                if (c > 0) region[cell - 1] else null,
                if (c < n - 1) region[cell + 1] else null,
            ).filter { it != -1 }
            if (owners.isEmpty()) {
                frontier += cell
                continue
            }
            region[cell] = rng.pick(owners)
            pushNeighbours(cell)
        }
        return region
    }

    /** Counts legal king layouts for these regions, stopping at two. */
    private fun countSolutions(n: Int, region: List<Int>): Int {
        val usedCols = BooleanArray(n)
        val usedRegions = BooleanArray(n)
        var found = 0

        fun place(row: Int, prevCol: Int) {
            if (found >= 2) return
            if (row == n) {
                found++
                return
            }
            for (c in 0 until n) {
                if (usedCols[c]) continue
                if (row > 0 && kotlin.math.abs(prevCol - c) <= 1) continue
                val reg = region[row * n + c]
                if (usedRegions[reg]) continue
                usedCols[c] = true
                usedRegions[reg] = true
                place(row + 1, c)
                usedCols[c] = false
                usedRegions[reg] = false
                if (found >= 2) return
            }
        }
        place(0, -99)
        return found
    }

    override fun hint(state: PuzzleState): PuzzleState? {
        val s = state as KingsState
        val wrong = s.marks.indices.firstOrNull {
            s.marks[it] == Mark.KING && it !in s.solution
        }
        if (wrong != null) {
            return s.copy(
                marks = s.marks.toMutableList().also { it[wrong] = Mark.EMPTY },
                moves = s.moves + 1,
            )
        }
        val missing = s.solution.firstOrNull { s.marks[it] != Mark.KING } ?: return null
        return s.copy(
            marks = s.marks.toMutableList().also { it[missing] = Mark.KING },
            moves = s.moves + 1,
        )
    }

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as KingsState
        val scheme = MaterialTheme.colorScheme
        val conflicts = s.conflicts()
        val eliminated = s.eliminated()

        // The sweep in progress. Held here rather than in the board state so that the gesture can
        // be drawn as it happens while still emitting exactly one state — and one undo entry —
        // when the finger lifts.
        var sweeping by remember(s.solution) { mutableStateOf<Mark?>(null) }
        var swept by remember(s.solution) { mutableStateOf(emptySet<Int>()) }
        val sweepMark = sweeping

        BoxWithConstraints(Modifier.fillMaxWidth().padding(14.dp)) {
            val cell = maxWidth / s.size
            val cellPx = with(LocalDensity.current) { cell.toPx() }

            fun cellAt(offset: Offset): Int {
                val r = (offset.y / cellPx).toInt().coerceIn(0, s.size - 1)
                val c = (offset.x / cellPx).toInt().coerceIn(0, s.size - 1)
                return r * s.size + c
            }

            // Tap and drag are read on the grid as a whole: per-cell `clickable` boxes only ever
            // see the cell the finger went down on, which is the one thing a sweep is not about.
            Box(
                Modifier
                    .size(maxWidth)
                    .pointerInput(s, interactive) {
                        if (!interactive) return@pointerInput
                        detectTapGestures { offset -> onState(s.cycle(cellAt(offset))) }
                    }
                    .pointerInput(s, interactive) {
                        if (!interactive) return@pointerInput
                        detectDragGestures(
                            onDragStart = { offset ->
                                val start = cellAt(offset)
                                sweeping = s.sweepMark(start)
                                swept = if (sweeping == null) emptySet() else setOf(start)
                            },
                            onDrag = { change, _ ->
                                if (sweeping != null) swept = swept + cellAt(change.position)
                            },
                            onDragEnd = {
                                val mark = sweeping
                                if (mark != null && swept.isNotEmpty()) onState(s.paint(swept, mark))
                                sweeping = null
                                swept = emptySet()
                            },
                            onDragCancel = {
                                sweeping = null
                                swept = emptySet()
                            },
                        )
                    }
            ) {
                for (r in 0 until s.size) {
                    for (c in 0 until s.size) {
                        val i = r * s.size + c
                        val shown = when {
                            sweepMark != null && i in swept && s.marks[i] != Mark.KING -> sweepMark
                            else -> s.marks[i]
                        }
                        Box(
                            Modifier
                                .padding(start = cell * c, top = cell * r)
                                .size(cell)
                                .padding(1.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Color(regionColours[s.region[i] % regionColours.size])
                                        .copy(alpha = 0.55f)
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            when (shown) {
                                Mark.KING -> Box(
                                    Modifier
                                        .fillMaxSize()
                                        .padding(cell * 0.22f)
                                        .clip(CircleShape)
                                        .background(
                                            if (i in conflicts) scheme.error else scheme.onBackground
                                        )
                                )
                                Mark.BLOCKED -> BlockedCross(cell, scheme.background)
                                Mark.EMPTY -> if (i in eliminated) RuledOutDot(cell, scheme.background)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * The player's own "not a king". A stroked X rather than a filled shape: big enough to read
     * on a phone at arm's length, yet plainly a pencil mark beside the solid disc of a king.
     */
    @Composable
    private fun BlockedCross(cell: Dp, colour: Color) {
        Canvas(Modifier.fillMaxSize().padding(cell * 0.28f)) {
            val ink = colour.copy(alpha = 0.85f)
            val width = size.minDimension * 0.2f
            drawLine(ink, Offset(0f, 0f), Offset(size.width, size.height), width, StrokeCap.Round)
            drawLine(ink, Offset(0f, size.height), Offset(size.width, 0f), width, StrokeCap.Round)
        }
    }

    /**
     * A square ruled out by a king already on the board. Deliberately quieter and a different
     * shape from [BlockedCross]: the player needs to see at a glance which marks are the board's
     * own deductions and which are theirs to change.
     */
    @Composable
    private fun RuledOutDot(cell: Dp, colour: Color) {
        Box(
            Modifier
                .size(cell * 0.16f)
                .clip(CircleShape)
                .background(colour.copy(alpha = 0.45f))
        )
    }
}
