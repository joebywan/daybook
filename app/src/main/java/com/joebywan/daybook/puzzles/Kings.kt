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
import androidx.compose.ui.graphics.Path
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

    /**
     * Judged by the rules, never by comparison with [solution].
     *
     * This used to read `kings == solution`, which refused every legal placement but the one the
     * generator happened to store — and a Kings board that admits a second answer is not rare
     * enough for that to be theoretical: the board that exposed it had twenty-six. [solution] is
     * still carried because [Kings.hint] has to nudge towards *some* answer, but it no longer
     * decides anything.
     */
    override val solved: Boolean get() = Kings.isSolved(size, region, marks)

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
 * Generation picks a legal king layout first, then hands the remaining squares to the colour
 * regions one at a time, keeping the board provably single-answered at every step. Winning is
 * judged by the rules, so any legal placement finishes the board — not just the one stored.
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
        "Squares a king already rules out are crossed off for you.",
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

    // ---- the win condition ---------------------------------------------------------------

    /**
     * Exactly one king per row, per column and per region, and no two kings touching.
     *
     * Lives on the object rather than on [KingsState] so the generator and the board agree on one
     * statement of the rules: the bug that shipped was a board whose *stored* answer had two kings
     * in a column, which a check written twice could have caught but a check written nowhere could
     * not.
     */
    fun isSolved(n: Int, region: List<Int>, marks: List<Mark>): Boolean {
        val kings = marks.indices.filter { marks[it] == Mark.KING }
        if (kings.size != n) return false
        val rows = BooleanArray(n)
        val cols = BooleanArray(n)
        val regions = HashSet<Int>()
        for (k in kings) {
            val r = k / n
            val c = k % n
            if (rows[r] || cols[c] || !regions.add(region[k])) return false
            rows[r] = true
            cols[c] = true
        }
        for (a in kings) for (b in kings) {
            if (a >= b) continue
            val touching = kotlin.math.abs(a / n - b / n) <= 1 && kotlin.math.abs(a % n - b % n) <= 1
            if (touching) return false
        }
        return true
    }

    // ---- generation ----------------------------------------------------------------------

    /**
     * King layouts tried per board.
     *
     * A single carve completes between a third and two thirds of the time depending on the grid,
     * and each failure is independent, so forty is a wide margin against ever reaching the
     * unproved [lastResort] — not a budget that is expected to be spent. Boards cost a couple of
     * carves on average, which at 9x9 is single-digit milliseconds.
     */
    private const val ATTEMPTS = 40

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState =
        generateVerified(seed, difficulty) ?: lastResort(seed, sizeFor(difficulty))

    /**
     * The real generator: a board whose uniqueness has been *proved*, or null when no layout in
     * the budget carved into one.
     *
     * Split out from [generate] so a test can assert this never abdicates. The version this
     * replaced had the same two paths but no way to tell them apart, so nobody noticed that at 8x8
     * and 9x9 the checked path failed on every single attempt and the unchecked fallback was what
     * players actually got.
     */
    fun generateVerified(seed: Long, difficulty: Difficulty): KingsState? {
        val n = sizeFor(difficulty)
        for (attempt in 0 until ATTEMPTS) {
            val rng = Rng(seed + attempt)
            val layout = randomLayout(rng, n) ?: continue
            val regions = carve(rng, n, layout) ?: continue
            return KingsState(
                n, regions, List(n * n) { Mark.EMPTY },
                layout.mapIndexed { r, c -> r * n + c }.toSet(),
            )
        }
        return null
    }

    /**
     * Only reachable if every layout in the budget failed to carve, which has not been observed.
     * It exists so [generate] is total — a daily puzzle must never throw — and it is deliberately
     * the only path that ships a board without a proof of uniqueness. KingsRulesTest pins that it
     * never fires. Winning is judged by the rules now, so even here a second answer would be a
     * blemish rather than an unwinnable board.
     */
    private fun lastResort(seed: Long, n: Int): KingsState {
        val layout = randomLayout(Rng(seed), n) ?: staircaseLayout(n)
        return KingsState(
            n, growRegions(Rng(seed), n, layout), List(n * n) { Mark.EMPTY },
            layout.mapIndexed { r, c -> r * n + c }.toSet(),
        )
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

    /**
     * Even columns top to bottom, then odd ones: distinct for every n, which `(r * 2) % n` was
     * not. That expression repeated columns whenever n was even — at 8x8 it read 0,2,4,6,0,2,4,6 —
     * so the fallback it fed stored an answer with two kings in a column, which no player could
     * ever reach. Consecutive rows differ by two within each half and by n-2 or more across the
     * join, so no two kings touch.
     */
    private fun staircaseLayout(n: Int): List<Int> {
        val evens = (n + 1) / 2
        return (0 until n).map { r -> if (r < evens) r * 2 else (r - evens) * 2 + 1 }
    }

    /**
     * Hands the squares the kings do not stand on to the regions one at a time, never letting the
     * board stop being uniquely solvable.
     *
     * Growing regions at random and checking afterwards was the losing strategy: at 8x8 and 9x9 a
     * randomly grown partition essentially never admits exactly one layout, so four hundred
     * attempts failed four hundred times and the unchecked fallback shipped. Handing out squares
     * one by one inverts the problem. With every region exactly its own king's square each region
     * has a single candidate and the board is trivially unique; giving a square to a region only
     * ever *adds* placements that region might hold and never removes one, so the solution count
     * is monotone and uniqueness can be re-checked after each square, offering the square to a
     * different neighbour when it would introduce a second answer. Monotonicity also means this is
     * not over-strict: a partition that is unique when finished was unique at every step along the
     * way, so nothing is ruled out that the old approach could have found.
     *
     * A square no neighbour can take is set aside rather than treated as failure — claiming its
     * other neighbours first can hand it a region that will take it — which is what carries the
     * success rate from a few percent to essentially always. Returns null rather than a partial or
     * unproved partition: the caller reseeds.
     */
    private fun carve(rng: Rng, n: Int, layout: List<Int>): List<Int>? {
        val cells = n * n
        val region = MutableList(cells) { -1 }
        layout.forEachIndexed { r, c -> region[r * n + c] = r }
        val sizes = IntArray(n) { 1 }
        var remaining = cells - n

        // Squares no region can take without adding a second answer, against the neighbours they
        // were refused by: a square is worth revisiting once a new region reaches it.
        val refused = HashMap<Int, Set<Int>>()

        var guard = 0
        while (remaining > 0 && guard++ < cells * 8) {
            val open = (0 until cells).mapNotNull { cell ->
                if (region[cell] != -1) return@mapNotNull null
                val hosts = hostsOf(cell, n, region)
                if (hosts.isEmpty() || refused[cell]?.containsAll(hosts) == true) null
                else cell to hosts
            }
            if (open.isEmpty()) return null

            // Fewest neighbours to choose from goes first. A sparse board has almost no alternative
            // layouts to open up by accident, so a square is at its most acceptable early, and the
            // squares with one candidate region are the ones a later step would find impossible.
            // Taking them in that order roughly doubled how often a carve runs to completion.
            val (cell, hosts) = rng.shuffled(open).minBy { it.second.size }
            // Smallest region first, so no one region swallows the leftovers and the colours stay
            // roughly the size a player expects to reason about.
            val ordered = rng.shuffled(hosts.toList()).sortedBy { sizes[it] }

            var chosen = -1
            for (host in ordered) {
                region[cell] = host
                if (countSolutions(n, region) == 1) {
                    chosen = host
                    break
                }
                region[cell] = -1
            }
            if (chosen == -1) {
                refused[cell] = hosts
                continue
            }
            sizes[chosen]++
            remaining--
        }
        return if (remaining == 0) region else null
    }

    /** The regions already touching [cell] edge-on, which are the only ones that may claim it. */
    private fun hostsOf(cell: Int, n: Int, region: List<Int>): Set<Int> =
        neighbours(cell, n).mapNotNullTo(HashSet()) { region[it].takeIf { id -> id != -1 } }

    private fun neighbours(cell: Int, n: Int): List<Int> {
        val r = cell / n
        val c = cell % n
        return listOfNotNull(
            if (r > 0) cell - n else null,
            if (r < n - 1) cell + n else null,
            if (c > 0) cell - 1 else null,
            if (c < n - 1) cell + 1 else null,
        )
    }

    /**
     * Flood-grows one region from each king until the board is covered. Every region is connected
     * and holds exactly one king by construction — but nothing here looks at how many layouts the
     * result admits, which is why only [lastResort] still uses it.
     */
    private fun growRegions(rng: Rng, n: Int, layout: List<Int>): List<Int> {
        val region = MutableList(n * n) { -1 }
        layout.forEachIndexed { r, c -> region[r * n + c] = r }

        val frontier = mutableListOf<Int>()
        fun pushNeighbours(index: Int) {
            neighbours(index, n).forEach { if (region[it] == -1) frontier += it }
        }
        (0 until n * n).filter { region[it] != -1 }.forEach(::pushNeighbours)

        while (frontier.isNotEmpty()) {
            val pick = rng.nextInt(frontier.size)
            val cell = frontier.removeAt(pick)
            if (region[cell] != -1) continue
            val owners = hostsOf(cell, n, region).toList()
            if (owners.isEmpty()) {
                frontier += cell
                continue
            }
            region[cell] = rng.pick(owners)
            pushNeighbours(cell)
        }
        return region
    }

    /**
     * Counts legal king layouts for these regions, stopping at two.
     *
     * A square still unclaimed — region `-1` — holds no king, so a partly carved board counts only
     * the layouts its finished regions already allow. That is what lets [carve] re-check after
     * every single square instead of only at the end.
     *
     * One king per row is implicit in the walk, and n kings on n distinct regions means one each;
     * consecutive rows are the only ones that can touch, so the previous row's column is the whole
     * of the adjacency test.
     */
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
                if (reg < 0 || usedRegions[reg]) continue
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
                                Mark.KING -> Crown(
                                    cell,
                                    if (i in conflicts) scheme.error else scheme.onBackground,
                                )
                                Mark.BLOCKED -> BlockedCross(cell, scheme.background)
                                // A square the board has ruled out is a fact, not a suggestion, so
                                // it is written in the same hand as the player's own crosses.
                                Mark.EMPTY -> if (i in eliminated) BlockedCross(cell, scheme.background)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * "Not a king", whether the player pencilled it in or a king on the board ruled it out.
     *
     * Stroked rather than filled, and drawn in the page colour where [Crown] is drawn in the ink:
     * opposite weight and opposite polarity, so a glance separates the crosses from the kings
     * without having to resolve either shape.
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
     * The king, as a crown.
     *
     * One filled silhouette — band and points in a single path — because a 9x9 board leaves the
     * mark about twenty dp across, and at that size an outline, a rim or the circles a crown
     * usually carries on its tips close up into a smudge long before the silhouette itself stops
     * reading. Three points rather than five for the same reason: rendered at a 38px cell, five
     * points came out as a comb.
     */
    @Composable
    private fun Crown(cell: Dp, colour: Color) {
        Canvas(Modifier.fillMaxSize().padding(cell * 0.14f)) {
            // Crowns are wider than they are tall, so the shape is sized off the width and then
            // centred in the square the cell gives it.
            val w = size.width
            val h = w * 0.72f
            val top = (size.height - h) / 2f
            fun x(f: Float) = f * w
            fun y(f: Float) = top + f * h
            val crown = Path().apply {
                moveTo(x(0.06f), y(1.00f))   // base, tucked in a little under the band
                lineTo(x(0.00f), y(0.66f))
                lineTo(x(0.00f), y(0.18f))   // left point
                lineTo(x(0.265f), y(0.60f))
                lineTo(x(0.50f), y(0.00f))   // centre point, the tallest
                lineTo(x(0.735f), y(0.60f))
                lineTo(x(1.00f), y(0.18f))   // right point
                lineTo(x(1.00f), y(0.66f))
                lineTo(x(0.94f), y(1.00f))
                close()
            }
            drawPath(crown, colour)
        }
    }
}
