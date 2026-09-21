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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleState
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.Rng

data class LitsState(
    val width: Int,
    val height: Int,
    val region: List<Int>,
    val shaded: List<Boolean>,
    val solution: List<Boolean>,
    override val moves: Int = 0,
) : PuzzleState {

    override val solved: Boolean get() = shaded == solution

    fun toggle(index: Int): LitsState =
        copy(shaded = shaded.toMutableList().also { it[index] = !it[index] }, moves = moves + 1)
}

/**
 * LITS.
 *
 * Shade a four-square tetromino in each region so the shading forms one connected area, contains
 * no full two-by-two block, and never puts two tetrominoes of the same letter edge to edge.
 *
 * Generation partitions the grid, enumerates each region's legal tetrominoes, and keeps the
 * partition only when the solver finds exactly one global solution.
 */
object Lits : PuzzleType {

    override val id = "lits"
    override val displayName = "LITS"
    override val tagline = "One tetromino per region, all joined up"
    override val accent = 0xFF9A8264
    override val rules = listOf(
        "Shade exactly four squares in every region, forming an L, I, T or S tetromino.",
        "A 2x2 square of shading is never allowed.",
        "All shaded squares must form one connected area.",
        "Two tetrominoes of the same letter may not touch edge to edge, even across regions.",
        "Tap a square to shade or clear it.",
    )

    /** The four legal letters. A 2x2 block is deliberately not one of them. */
    private enum class Piece { I, L, T, S }

    private fun shape(difficulty: Difficulty) = when (difficulty) {
        Difficulty.STANDARD -> 6 to 6
        Difficulty.HARD -> 7 to 7
        Difficulty.EXPERT -> 8 to 8
    }

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState {
        val (w, h) = shape(difficulty)
        val n = w * h
        val targetPieces = maxOf(3, n / 7)

        var solverRuns = 0
        repeat(150) { attempt ->
            if (solverRuns > 120) return@repeat
            val rng = Rng(seed + attempt)
            val placed = placeTetrominoes(rng, w, h, targetPieces) ?: return@repeat

            // The shading is fixed at this point; only the region walls are still free, so several
            // wall layouts are tried per layout of pieces before giving up on it.
            repeat(4) {
                if (solverRuns > 120) return@repeat
                val region = regionsAround(rng, w, h, placed) ?: return@repeat
                val options = placed.indices.map { r ->
                    tetrominoes(region.indices.filter { region[it] == r }, w, h)
                }
                if (options.any { it.isEmpty() }) return@repeat
                solverRuns++
                val solutions = solve(w, h, options, limit = 2)
                if (solutions.size == 1) {
                    return LitsState(w, h, region, List(n) { false }, solutions.first())
                }
            }
        }

        // Fallback: one region per tetromino, which is forced and therefore always unique.
        val rng = Rng(seed)
        val placed = placeTetrominoes(rng, w, h, targetPieces)
            ?: placeTetrominoes(Rng(seed + 7717), w, h, 3)!!
        val region = MutableList(n) { -1 }
        placed.forEachIndexed { index, (quad, _) -> quad.forEach { region[it] = index } }
        // Anything left over joins whichever region it touches, keeping every cell owned.
        var guard = 0
        while (region.any { it == -1 } && guard++ < n * 50) {
            val cell = rng.nextInt(n)
            if (region[cell] != -1) continue
            val owners = neighbours(cell, w, h).map { region[it] }.filter { it != -1 }
            if (owners.isNotEmpty()) region[cell] = rng.pick(owners)
        }
        val solution = List(n) { i -> placed.any { i in it.first } }
        return LitsState(w, h, region.map { if (it == -1) 0 else it }, List(n) { false }, solution)
    }

    // ---- building a solution first --------------------------------------------------------

    /**
     * Lays down [target] tetrominoes that already satisfy every LITS rule.
     *
     * Building the answer before the regions is what makes this generator work at all: regions
     * drawn at random almost never admit a legal shading, let alone exactly one. Each new piece is
     * grown off the existing shading, so the union is connected by construction.
     */
    private fun placeTetrominoes(
        rng: Rng,
        w: Int,
        h: Int,
        target: Int,
    ): List<kotlin.Pair<List<Int>, Piece>>? {
        val n = w * h
        val shaded = BooleanArray(n)
        val pieceAt = arrayOfNulls<Piece>(n)
        val placed = mutableListOf<kotlin.Pair<List<Int>, Piece>>()

        fun makesSquare(quad: List<Int>): Boolean {
            for (cell in quad) {
                val r = cell / w
                val c = cell % w
                for (dr in -1..0) for (dc in -1..0) {
                    val rr = r + dr
                    val cc = c + dc
                    if (rr < 0 || cc < 0 || rr + 1 >= h || cc + 1 >= w) continue
                    if (listOf(
                            rr * w + cc, rr * w + cc + 1,
                            (rr + 1) * w + cc, (rr + 1) * w + cc + 1,
                        ).all { it in quad || shaded[it] }
                    ) return true
                }
            }
            return false
        }

        fun touchesSameLetter(quad: List<Int>, piece: Piece): Boolean =
            quad.any { cell ->
                neighbours(cell, w, h).any { next ->
                    next !in quad && shaded[next] && pieceAt[next] == piece
                }
            }

        var guard = 0
        while (placed.size < target && guard++ < target * 300) {
            // After the first piece, only grow from squares touching what is already shaded.
            val seedCells = if (placed.isEmpty()) {
                (0 until n).filter { !shaded[it] }
            } else {
                (0 until n).filter { cell ->
                    !shaded[cell] && neighbours(cell, w, h).any { shaded[it] }
                }
            }
            if (seedCells.isEmpty()) break

            val from = rng.pick(seedCells)
            val candidates = quadsContaining(from, w, h) { !shaded[it] }
                .filter { (quad, piece) -> !makesSquare(quad) && !touchesSameLetter(quad, piece) }
            if (candidates.isEmpty()) continue

            val (quad, piece) = rng.pick(candidates)
            quad.forEach { shaded[it] = true; pieceAt[it] = piece }
            placed += quad to piece
        }

        return if (placed.size >= maxOf(3, target - 1)) placed else null
    }

    /** Every legal tetromino that covers [cell] using only squares [free] allows. */
    private fun quadsContaining(
        cell: Int,
        w: Int,
        h: Int,
        free: (Int) -> Boolean,
    ): List<kotlin.Pair<List<Int>, Piece>> {
        if (!free(cell)) return emptyList()
        val seen = HashSet<List<Int>>()

        fun grow(current: List<Int>) {
            if (current.size == 4) {
                seen += current
                return
            }
            for (c in current) {
                for (next in neighbours(c, w, h)) {
                    if (next in current || !free(next)) continue
                    grow((current + next).sorted())
                }
            }
        }
        grow(listOf(cell))

        return seen.mapNotNull { quad -> classify(quad, w)?.let { quad to it } }
    }

    /** Grows each region outward from its tetromino until every square belongs to one. */
    private fun regionsAround(
        rng: Rng,
        w: Int,
        h: Int,
        placed: List<kotlin.Pair<List<Int>, Piece>>,
    ): List<Int>? {
        val n = w * h
        val region = MutableList(n) { -1 }
        placed.forEachIndexed { index, (quad, _) -> quad.forEach { region[it] = index } }

        val sizes = IntArray(placed.size) { 4 }
        var remaining = region.count { it == -1 }
        var guard = 0
        while (remaining > 0 && guard++ < n * 60) {
            val cell = rng.nextInt(n)
            if (region[cell] != -1) continue
            val owners = neighbours(cell, w, h)
                .map { region[it] }
                .filter { it != -1 && sizes[it] < MAX_REGION }
            if (owners.isEmpty()) continue
            val owner = rng.pick(owners)
            region[cell] = owner
            sizes[owner]++
            remaining--
        }
        return if (remaining == 0) region.toList() else null
    }

    private const val MAX_REGION = 7

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

    /** Every four-square legal tetromino that fits inside [cells]. */
    private fun tetrominoes(cells: List<Int>, w: Int, h: Int): List<kotlin.Pair<List<Int>, Piece>> {
        val out = mutableListOf<kotlin.Pair<List<Int>, Piece>>()
        val list = cells.sorted()
        for (a in list.indices) for (b in a + 1 until list.size)
            for (c in b + 1 until list.size) for (d in c + 1 until list.size) {
                val quad = listOf(list[a], list[b], list[c], list[d])
                if (!isConnected(quad, w, h)) continue
                val piece = classify(quad, w) ?: continue
                out += quad to piece
            }
        return out
    }

    private fun isConnected(cells: List<Int>, w: Int, h: Int): Boolean {
        val set = cells.toHashSet()
        val stack = ArrayDeque<Int>()
        val seen = HashSet<Int>()
        stack.addLast(cells.first())
        seen += cells.first()
        while (stack.isNotEmpty()) {
            val cell = stack.removeLast()
            for (next in neighbours(cell, w, h)) {
                if (next in set && seen.add(next)) stack.addLast(next)
            }
        }
        return seen.size == cells.size
    }

    /**
     * Names the tetromino, or returns null for the 2x2 block, which LITS does not allow.
     *
     * Shapes are identified from their bounding box: a 1x4 strip is an I, and within a 2x3 box the
     * row counts and the odd square's position separate L, T and S.
     */
    private fun classify(cells: List<Int>, w: Int): Piece? {
        val rows = cells.map { it / w }
        val cols = cells.map { it % w }
        val r0 = rows.min()
        val c0 = cols.min()
        val rowSpan = rows.max() - r0 + 1
        val colSpan = cols.max() - c0 + 1

        if (rowSpan == 1 || colSpan == 1) return Piece.I
        if (rowSpan == 2 && colSpan == 2) return null   // the O block

        // Normalise to a 2x3 box, transposing the 3x2 case.
        val normalised = if (rowSpan == 2 && colSpan == 3) {
            cells.map { (it / w - r0) to (it % w - c0) }
        } else if (rowSpan == 3 && colSpan == 2) {
            cells.map { (it % w - c0) to (it / w - r0) }
        } else {
            return null
        }

        val byRow = normalised.groupBy({ it.first }, { it.second })
        val long = byRow.values.firstOrNull { it.size == 3 }
        return if (long != null) {
            val single = byRow.values.first { it.size == 1 }.first()
            if (single == 0 || single == 2) Piece.L else Piece.T
        } else {
            Piece.S
        }
    }

    // ---- solver -------------------------------------------------------------------------------

    private fun solve(
        w: Int,
        h: Int,
        options: List<List<kotlin.Pair<List<Int>, Piece>>>,
        limit: Int,
    ): List<List<Boolean>> {
        val n = w * h
        val shaded = BooleanArray(n)
        val pieceAt = arrayOfNulls<Piece>(n)
        val found = mutableListOf<List<Boolean>>()
        var nodes = 0
        // Fewest choices first. Region order does not change the answer, only how fast it is found.
        val order = options.indices.sortedBy { options[it].size }

        fun makesSquare(quad: List<Int>): Boolean {
            for (cell in quad) {
                val r = cell / w
                val c = cell % w
                // Check every 2x2 block that includes this square.
                for (dr in -1..0) for (dc in -1..0) {
                    val rr = r + dr
                    val cc = c + dc
                    if (rr < 0 || cc < 0 || rr + 1 >= h || cc + 1 >= w) continue
                    val block = listOf(
                        rr * w + cc, rr * w + cc + 1,
                        (rr + 1) * w + cc, (rr + 1) * w + cc + 1,
                    )
                    if (block.all { shaded[it] }) return true
                }
            }
            return false
        }

        fun touchesSameLetter(quad: List<Int>, piece: Piece): Boolean =
            quad.any { cell ->
                neighbours(cell, w, h).any { next ->
                    next !in quad && shaded[next] && pieceAt[next] == piece
                }
            }

        fun connected(): Boolean {
            val start = (0 until n).firstOrNull { shaded[it] } ?: return false
            val seen = HashSet<Int>()
            val stack = ArrayDeque<Int>()
            stack.addLast(start)
            seen += start
            while (stack.isNotEmpty()) {
                val cell = stack.removeLast()
                for (next in neighbours(cell, w, h)) {
                    if (shaded[next] && seen.add(next)) stack.addLast(next)
                }
            }
            return seen.size == (0 until n).count { shaded[it] }
        }

        fun place(regionIndex: Int) {
            if (found.size >= limit || nodes++ > 80_000) return
            if (regionIndex == options.size) {
                if (connected()) found += shaded.toList()
                return
            }
            for ((quad, piece) in options[order[regionIndex]]) {
                if (quad.any { shaded[it] }) continue
                quad.forEach { shaded[it] = true; pieceAt[it] = piece }
                if (!makesSquare(quad) && !touchesSameLetter(quad, piece)) {
                    place(regionIndex + 1)
                }
                quad.forEach { shaded[it] = false; pieceAt[it] = null }
                if (found.size >= limit) return
            }
        }

        place(0)
        return found
    }

    // ---- play ---------------------------------------------------------------------------------

    override fun hint(state: PuzzleState): PuzzleState? {
        val s = state as LitsState
        val wrong = s.shaded.indices.firstOrNull { s.shaded[it] != s.solution[it] } ?: return null
        return s.toggle(wrong)
    }

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as LitsState
        val scheme = MaterialTheme.colorScheme

        BoxWithConstraints(Modifier.fillMaxWidth().padding(18.dp)) {
            val step = maxWidth / s.width
            val stepPx = with(LocalDensity.current) { step.toPx() }

            Canvas(
                Modifier
                    .width(step * s.width)
                    .height(step * s.height)
                    .pointerInput(s, interactive) {
                        if (!interactive) return@pointerInput
                        detectTapGestures { offset: Offset ->
                            val c = (offset.x / stepPx).toInt().coerceIn(0, s.width - 1)
                            val r = (offset.y / stepPx).toInt().coerceIn(0, s.height - 1)
                            onState(s.toggle(r * s.width + c))
                        }
                    }
            ) {
                for (i in 0 until s.width * s.height) {
                    val r = i / s.width
                    val c = i % s.width
                    drawRect(
                        color = if (s.shaded[i]) Color(accent) else scheme.surfaceVariant,
                        topLeft = Offset(c * stepPx, r * stepPx),
                        size = Size(stepPx, stepPx),
                    )
                    drawRect(
                        color = scheme.background.copy(alpha = 0.35f),
                        topLeft = Offset(c * stepPx, r * stepPx),
                        size = Size(stepPx, stepPx),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
                    )
                }

                // Thick strokes wherever two different regions meet.
                val edge = stepPx * 0.06f
                for (i in 0 until s.width * s.height) {
                    val r = i / s.width
                    val c = i % s.width
                    if (c + 1 < s.width && s.region[i] != s.region[i + 1]) {
                        drawLine(
                            scheme.onBackground,
                            Offset((c + 1) * stepPx, r * stepPx),
                            Offset((c + 1) * stepPx, (r + 1) * stepPx),
                            strokeWidth = edge,
                        )
                    }
                    if (r + 1 < s.height && s.region[i] != s.region[i + s.width]) {
                        drawLine(
                            scheme.onBackground,
                            Offset(c * stepPx, (r + 1) * stepPx),
                            Offset((c + 1) * stepPx, (r + 1) * stepPx),
                            strokeWidth = edge,
                        )
                    }
                }
                drawRect(
                    color = scheme.onBackground,
                    topLeft = Offset.Zero,
                    size = Size(stepPx * s.width, stepPx * s.height),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = edge),
                )
            }
        }
    }
}
