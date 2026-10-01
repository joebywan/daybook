package com.joebywan.daybook.puzzles

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.LocalBoardHighlight
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.TutorialFrame
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
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
     * still carried because [Kings.teach] has to know which of the player's moves are mistakes, but
     * it no longer decides anything.
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

    /**
     * A single tap: pencil the square out, or rub the pencilling away.
     *
     * A square holding a king is returned untouched — the same instance, so the caller can tell
     * that nothing happened and emit nothing. Kings are the board's expensive decisions and are
     * taken back by [toggleKing]; letting the cheap, frequent gesture clear one would mean every
     * stray finger cost a king, which is the mistake the old three-step cycle made in reverse by
     * charging two taps for every mark.
     */
    fun toggleMark(index: Int): KingsState = when (marks[index]) {
        Mark.EMPTY -> withMark(index, Mark.BLOCKED)
        Mark.BLOCKED -> withMark(index, Mark.EMPTY)
        Mark.KING -> this
    }

    /**
     * A double tap: crown the square, or take the crown back.
     *
     * A pencilled-out square is crowned like any other. The player's own mark is a note to
     * themselves, not a lock, and a double tap is deliberate enough to outrank it.
     */
    fun toggleKing(index: Int): KingsState =
        withMark(index, if (marks[index] == Mark.KING) Mark.EMPTY else Mark.KING)

    private fun withMark(index: Int, mark: Mark): KingsState =
        copy(marks = marks.toMutableList().also { it[index] = mark }, moves = moves + 1)

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
        "Tap a square to pencil it out, and tap it again to rub the mark away.",
        "Double-tap a square to crown a king there, or to take the crown back.",
        "Drag across a run of squares to mark them all in one sweep.",
        "Squares a king already rules out are crossed off for you.",
    )

    /**
     * One colour per region of the largest board (9x9, nine regions), drawn opaque.
     *
     * Chosen by search to be as far apart from each other as possible: the closest pair, green
     * and mint, is CIEDE2000 22 apart, where the 0.55-alpha set this replaced had two greens 4.5
     * apart once blended — the complaint that prompted it. Two further limits shaped them: every
     * colour keeps at least 4.5:1 contrast with [MarkInk], so the marks read on all of them, and
     * lightness and chroma stay short of neon. Under simulated deuteranopia and protanopia
     * (Machado 2009, full severity) the closest pairs are about 11 apart — orange/green,
     * teal/violet, teal/raspberry — and [regionPalette] keeps those pairs off neighbouring regions
     * where it can.
     *
     * Opaque, and the same in both themes: the tiles carry their own ground, so the marks on them
     * are drawn in a fixed ink rather than the theme's.
     *
     * [colourNames] names these, index for index; keep the two in step.
     */
    private val regionColours = listOf(
        0xFFEF9E8D, // salmon
        0xFFC86D05, // burnt orange
        0xFFDDC152, // mustard
        0xFF509B58, // green
        0xFF83E0C1, // mint
        0xFF3E9CAD, // teal
        0xFFA2C6FF, // sky
        0xFF8975DB, // violet
        0xFFCF5E8A, // raspberry
    )

    /**
     * What a player calls each of [regionColours], index for index: "the teal one", never
     * "region 3". [KingsTeacher] and the walkthrough captions speak through [regionNames].
     */
    private val colourNames = listOf(
        "salmon", "orange", "yellow", "green", "mint", "teal", "blue", "purple", "pink",
    )

    /** The name of the colour each region is painted in, as [regionPalette] paints it. */
    internal fun regionNames(n: Int, region: List<Int>): List<String> =
        regionPalette(n, region).map { colourNames[it % colourNames.size] }

    /**
     * How different two [regionColours] look, as the worst of CIEDE2000 for normal vision and for
     * simulated deuteranopia and protanopia, rounded. Generated offline from the hex values above;
     * regenerate it if they change.
     */
    private val colourDistance = listOf(
        intArrayOf(0, 19, 12, 12, 12, 24, 33, 37, 19),
        intArrayOf(19, 0, 17, 11, 30, 39, 49, 50, 24),
        intArrayOf(12, 17, 0, 16, 20, 40, 47, 59, 29),
        intArrayOf(12, 11, 16, 0, 22, 29, 42, 44, 14),
        intArrayOf(12, 30, 20, 22, 0, 25, 24, 35, 18),
        intArrayOf(24, 39, 40, 29, 25, 0, 16, 11, 11),
        intArrayOf(33, 49, 47, 42, 24, 16, 0, 19, 27),
        intArrayOf(37, 50, 59, 44, 35, 11, 19, 0, 17),
        intArrayOf(19, 24, 29, 14, 18, 11, 27, 17, 0),
    )

    /**
     * Which of [regionColours] each region is painted in: region `r` gets `regionColours[result[r]]`.
     *
     * Display only — nothing here reaches the generator, the [Rng] or the saved game, and the same
     * regions always get the same colours. It starts from region `r` → colour `r` and swaps pairs
     * of colours (including the ones a smaller board leaves unused) while that makes the closest
     * pair of *touching* regions look further apart, or, at the same closest pair, the touching
     * pairs further apart in total. A local search, not an exhaustive one: on a year of Expert
     * boards it lifts the median colour-blind distance between neighbours from 11 to about 17.
     */
    internal fun regionPalette(n: Int, region: List<Int>): IntArray {
        val count = regionColours.size
        if (n > count) return IntArray(n) { it % count }
        // Touching pairs in a fixed order, from a matrix rather than a hash set, so the result
        // cannot depend on the platform's iteration order.
        val touching = Array(n) { BooleanArray(n) }
        for (i in 0 until n * n) {
            val r = i / n
            val c = i % n
            if (c + 1 < n && region[i] != region[i + 1]) {
                touching[region[i]][region[i + 1]] = true
                touching[region[i + 1]][region[i]] = true
            }
            if (r + 1 < n && region[i] != region[i + n]) {
                touching[region[i]][region[i + n]] = true
                touching[region[i + n]][region[i]] = true
            }
        }
        val edges = buildList {
            for (a in 0 until n) for (b in a + 1 until n) if (touching[a][b]) add(a to b)
        }
        val colour = IntArray(count) { it }
        fun score(): Long {
            var worst = Int.MAX_VALUE
            var total = 0
            for ((a, b) in edges) {
                val d = colourDistance[colour[a]][colour[b]]
                worst = minOf(worst, d)
                total += d
            }
            return worst.toLong() * 1_000_000 + total
        }
        var best = score()
        var improved = true
        while (improved) {
            improved = false
            for (i in 0 until n) {
                for (j in i + 1 until count) {
                    colour[i] = colour[j].also { colour[j] = colour[i] }
                    val s = score()
                    if (s > best) {
                        best = s
                        improved = true
                    } else {
                        colour[i] = colour[j].also { colour[j] = colour[i] }
                    }
                }
            }
        }
        return colour.copyOf(n)
    }

    /**
     * The ink every mark on a tile is drawn in, in both themes: the app's own dark ink, because
     * the tiles are opaque and light enough that the theme's light-on-dark text colour would
     * vanish on half of them. At least 4.5:1 against every region colour.
     */
    private val MarkInk = Color(0xFF11201C)

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
            val ordered = rng.shuffled(hosts.sorted()).sortedBy { sizes[it] }

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

    /**
     * The regions already touching [cell] edge-on, which are the only ones that may claim it.
     *
     * A hash set, so callers that hand these to the [Rng] must sort them first. Hash iteration
     * order is a platform detail: the JVM happens to walk small integers in ascending order and
     * Kotlin/Wasm walks them in insertion order, and before the callers sorted, the same seed
     * carved a different board in the browser than on the phone. Sorting matches what the JVM
     * already did, so no board on Android changed.
     */
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
            val owners = hostsOf(cell, n, region).sorted()
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

    // ---- the walkthrough ---------------------------------------------------------------------

    /**
     * The walkthrough's board, 5x5 so every square is big enough to aim at while learning the
     * gestures. Regions, and the colours [regionPalette] paints them:
     *
     * ```
     * 0 0 1 2 2      blue   blue   orange purple purple
     * 3 3 1 2 2      mint   mint   orange purple purple
     * 3 3 3 2 2      mint   mint   mint   purple purple
     * 3 3 4 2 2      mint   mint   green  purple purple
     * 3 4 4 4 4      mint   green  green  green  green
     * ```
     *
     * The captions name regions 0 and 1 through [regionNames], so they follow the palette if it
     * changes. Hand-built so that the moves the frames teach are the moves the board actually needs,
     * in that order: the square next to region 0 is out because it would empty region 1, which
     * leaves region 0 one square, whose king then shows off what it rules out, after which region 1
     * sitting in one column clears a run of three to sweep. KingsTutorialTest proves it has exactly one answer.
     */
    internal val TUTORIAL_REGIONS = listOf(
        0, 0, 1, 2, 2,
        3, 3, 1, 2, 2,
        3, 3, 3, 2, 2,
        3, 3, 4, 2, 2,
        3, 4, 4, 4, 4,
    )
    internal val TUTORIAL_SOLUTION = setOf(0, 7, 14, 16, 23)
    private const val TUTORIAL_N = 5

    private fun tutorialBoard(kings: Set<Int> = emptySet(), crosses: Set<Int> = emptySet()) = KingsState(
        TUTORIAL_N, TUTORIAL_REGIONS,
        List(TUTORIAL_N * TUTORIAL_N) {
            when (it) {
                in kings -> Mark.KING
                in crosses -> Mark.BLOCKED
                else -> Mark.EMPTY
            }
        },
        TUTORIAL_SOLUTION,
    )

    /**
     * Accepts exactly [base] with [changes] made and nothing else. Strict on purpose: each frame's
     * board is written for the one before it, so a frame that let a stray cross through would hand
     * the next frame a board its caption does not describe.
     */
    private fun only(base: KingsState, changes: Map<Int, Mark>): (PuzzleState) -> Boolean = { next ->
        next is KingsState && next.marks.indices.all { i -> next.marks[i] == (changes[i] ?: base.marks[i]) }
    }

    override val tutorial: List<TutorialFrame> by lazy {
        val solved = tutorialBoard(kings = TUTORIAL_SOLUTION)
        val empty = tutorialBoard()
        val cornerLeft = tutorialBoard(crosses = setOf(1))
        val crowned = tutorialBoard(kings = setOf(0), crosses = setOf(1))
        val swept = tutorialBoard(kings = setOf(0), crosses = setOf(1, 12, 17, 22))
        val row3 = (10..14).toSet()
        val column2 = setOf(1, 6, 11, 16, 21)
        val secondCells = setOf(2, 7)
        val names = regionNames(TUTORIAL_N, TUTORIAL_REGIONS)
        val first = names[0] // the corner region, one square once its neighbour is crossed out
        val second = names[1] // the two squares in column 3
        listOf(
            TutorialFrame(
                state = solved,
                caption = "Every row, every column and every colour holds exactly one king. " +
                    "Here are one row, one column and $second, each with its one king.",
                highlight = BoardHighlight(strong = row3 + column2 + secondCells),
            ),
            TutorialFrame(
                state = solved,
                caption = "Kings can't touch, not even corner to corner. None of the eight squares " +
                    "around a king can hold another.",
                highlight = BoardHighlight(strong = setOf(7), soft = setOf(1, 2, 3, 6, 8, 11, 12, 13)),
            ),
            TutorialFrame(
                state = empty,
                caption = "A king on the glowing square would share a row with one $second square and " +
                    "touch the other, leaving $second nowhere. Tap it to cross it out.",
                highlight = BoardHighlight(strong = setOf(1), soft = secondCells),
                accepts = only(empty, mapOf(1 to Mark.BLOCKED)),
                retry = "Tap the glowing square once.",
                done = "Crossed out. That square can never hold a king.",
            ),
            TutorialFrame(
                state = cornerLeft,
                caption = "${first.replaceFirstChar { it.uppercaseChar() }} has only one square left, so its king must go there. " +
                    "Double-tap it to crown it.",
                highlight = BoardHighlight(strong = setOf(0), soft = setOf(1)),
                accepts = only(cornerLeft, mapOf(0 to Mark.KING)),
                retry = "Double-tap the glowing square: two quick taps.",
                done = "Crowned.",
            ),
            TutorialFrame(
                state = crowned,
                caption = "A king rules out its row, its column, its colour and the squares around " +
                    "it. The board crosses those off for you.",
                highlight = BoardHighlight(strong = setOf(0), soft = crowned.eliminated()),
            ),
            TutorialFrame(
                state = crowned,
                caption = "${second.replaceFirstChar { it.uppercaseChar() }} sits entirely in column 3, so column 3's king is $second. Drag down the " +
                    "glowing squares to cross them all out in one sweep.",
                highlight = BoardHighlight(strong = setOf(12, 17, 22), soft = secondCells),
                accepts = only(crowned, mapOf(12 to Mark.BLOCKED, 17 to Mark.BLOCKED, 22 to Mark.BLOCKED)),
                retry = "Drag along all three glowing squares in one sweep.",
                done = "Swept.",
            ),
            TutorialFrame(
                state = swept,
                caption = "Your turn: finish the board. Stuck? Hint shows you why.",
                freePlay = true,
                done = "Solved. That's all there is to it.",
            ),
        )
    }

    // ---- teaching --------------------------------------------------------------------------

    /**
     * A mistake to take back or a step to reason out — see [KingsTeacher]. Replaces the old
     * `hint()`, which placed a king straight out of [KingsState.solution] and taught nothing.
     */
    override fun teach(state: PuzzleState): Deduction? {
        val s = state as KingsState
        val step = KingsTeacher.teach(s) ?: return null
        val base = s.marks
        return Deduction(
            technique = step.technique,
            nudge = step.nudge,
            explanation = step.explanation,
            focus = step.focus,
            cited = step.cited,
            targets = step.targets,
            mistake = step.technique == KingsTeacher.MISTAKE,
            fallback = step.technique == KingsTeacher.FALLBACK,
            applyTo = { now -> applyStep(now as KingsState, step) },
            reachedBy = { now ->
                val k = now as KingsState
                // Ruled out by a *correct* king counts as crossed: the player who crowns the king
                // that sweeps a row has done more than the hint asked. A wrong king's sweep does not
                // count, or the hint would clear itself on the back of a mistake.
                val ruled = k.marks.indices
                    .filter { k.marks[it] == Mark.KING && it in k.solution }
                    .flatMap { k.eliminatedBy(it) }
                    .toSet()
                step.kings.all { k.marks[it] == Mark.KING } &&
                    step.crosses.all { k.marks[it] == Mark.BLOCKED || it in ruled } &&
                    step.clears.all { k.marks[it] != base[it] }
            },
        )
    }

    /** "Show me": the step, made on the board as it is now. One state, so one undo entry. */
    private fun applyStep(s: KingsState, step: KingsTeacher.Step): KingsState {
        val ruled = s.eliminated()
        val next = s.marks.toMutableList()
        var changed = 0
        fun set(i: Int, mark: Mark) {
            if (next[i] != mark) {
                next[i] = mark
                changed++
            }
        }
        step.clears.forEach { set(it, Mark.EMPTY) }
        step.kings.forEach { set(it, Mark.KING) }
        step.crosses.filter { next[it] == Mark.EMPTY && it !in ruled }.forEach { set(it, Mark.BLOCKED) }
        return if (changed == 0) s else s.copy(marks = next, moves = s.moves + changed)
    }

    // ---- drawing -------------------------------------------------------------------------

    private const val MOTIF_SIZE = 3

    /**
     * Three regions that interlock rather than stripe — an L of one colour, an L of another and a
     * full column of a third — because a motif of three neat rows reads as a colour chart and a
     * Kings board never looks like that.
     *
     * Colours 7, 4 and 1: violet, mint and burnt orange, the three of [regionColours] furthest
     * apart from one another (CIEDE2000 30 at the closest, colour-blind vision included), because
     * all three regions touch and a thumbnail has no room for subtlety. Violet leads as the
     * nearest to Kings' own accent.
     */
    private val motifRegions = listOf(7, 7, 1, 7, 4, 1, 4, 4, 1)

    /**
     * Legal, not merely decorative: both crosses sit diagonally against the crown, which is
     * exactly where a king rules squares out, so the tile is a crop of a board that could happen.
     */
    private val motifMarks = listOf(
        Mark.EMPTY, Mark.EMPTY, Mark.BLOCKED,
        Mark.EMPTY, Mark.KING, Mark.EMPTY,
        Mark.BLOCKED, Mark.EMPTY, Mark.EMPTY,
    )

    /**
     * Three squares across rather than the seven the smallest real board has, because the tile is
     * only 72-96dp: at three, a cell lands at 24-32dp, which is roughly what a 9x9 board gives a
     * cell on a phone. That is the size [Crown] was drawn for, and drawing the real crown and the
     * real crosses — rather than a suggestion of them — is what makes the tile read as Kings
     * instead of as a swatch. More squares would take the crown below the size it survives.
     */
    @Composable
    override fun Preview(modifier: Modifier) {
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            // Square and centred in whatever shape the grid hands over. `maxHeight` is infinite
            // when the tile is free to grow, and taking the smaller leaves the width in charge.
            val cell = minOf(maxWidth, maxHeight) / MOTIF_SIZE
            Box(Modifier.size(cell * MOTIF_SIZE)) {
                for (r in 0 until MOTIF_SIZE) {
                    for (c in 0 until MOTIF_SIZE) {
                        val i = r * MOTIF_SIZE + c
                        Box(
                            Modifier
                                .padding(start = cell * c, top = cell * r)
                                .size(cell)
                                .padding(1.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(regionColours[motifRegions[i]])),
                            contentAlignment = Alignment.Center,
                        ) {
                            when (motifMarks[i]) {
                                Mark.KING -> Crown(cell, MarkInk)
                                Mark.BLOCKED -> BlockedCross(cell)
                                Mark.EMPTY -> Unit
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * How long a tap waits to find out whether it was half of a double tap.
     *
     * The platform's own double-tap timeout. Only the *emission* waits this long — the mark itself
     * is on screen before the finger is off the glass — so the window costs the player nothing
     * they can see. What it does cost is that a mark reaches the undo history, the move count and
     * the saved game up to this late, and that a mark made in the last third of a second before
     * the app is killed is lost.
     */
    private const val DOUBLE_TAP_MS = 300L

    /**
     * Where tap times are measured from. The stdlib's monotonic clock rather than Android's
     * `SystemClock.uptimeMillis()` so this file also compiles for the web build; only differences
     * between two taps are ever read, so the origin does not matter.
     */
    private val tapClock = TimeSource.Monotonic.markNow()

    /**
     * A tap whose mark is already on the board but not yet in the undo history.
     *
     * [base] earns its place twice. A second tap on the same square builds its king from there, so
     * a crown costs one entry in the history instead of a cross followed by a crown; and it
     * identifies the board this tap was made on, so a held tap that Undo, Restart or a hint has
     * overtaken is dropped rather than replayed onto a board it never saw.
     */
    private data class PendingTap(val cell: Int, val base: KingsState, val after: KingsState)

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as KingsState
        val scheme = MaterialTheme.colorScheme

        // A tap paints its mark here and hands it to the screen a moment later — the same "draw it
        // now, emit once" arrangement the sweep below already uses.
        //
        // Marking is the gesture a player makes dozens of times a board, so it must not wait:
        // giving `onDoubleTap` to detectTapGestures would delay every mark by [DOUBLE_TAP_MS],
        // which is a worse board than the one this replaced. Emitting the mark immediately instead
        // would leave a cross in the undo history underneath every crown, and would re-key the
        // pointer input between the two taps so the second one arrived at a detector that had
        // forgotten the first. Holding back only the emission avoids both.
        var pending by remember(s.solution) { mutableStateOf<PendingTap?>(null) }
        // The tap memory deliberately outlives [pending], so a second tap landing just after the
        // window closed still reads as a double rather than rubbing out the first tap's mark.
        var lastCell by remember(s.solution) { mutableIntStateOf(-1) }
        var lastTapAt by remember(s.solution) { mutableLongStateOf(0L) }
        // A held tap that a sweep has started on top of. It cannot simply be emitted when the
        // gesture begins: that would re-key the pointer input below and cancel the sweep before
        // it painted anything, so the sweep carries it and delivers it at the end instead.
        var carried by remember(s.solution) { mutableStateOf<PendingTap?>(null) }

        // The sweep in progress. Held here rather than in the board state so that the gesture can
        // be drawn as it happens while still emitting exactly one state — and one undo entry —
        // when the finger lifts.
        var sweeping by remember(s.solution) { mutableStateOf<Mark?>(null) }
        var swept by remember(s.solution) { mutableStateOf(emptySet<Int>()) }
        val sweepMark = sweeping

        val held = pending?.takeIf { it.base === s }
        // The board as the finger has left it, which may be one tap ahead of the screen.
        val shown = held?.after ?: s
        val conflicts = shown.conflicts()
        val eliminated = shown.eliminated()
        val highlight = LocalBoardHighlight.current
        val glow = if (highlight.warning) scheme.error else MarkInk // on opaque tiles, in both themes
        // A glow that breathes is findable at a glance on a 9x9 board; a static outline is not much
        // louder than the gaps between squares. Only runs while something glows, and is read in
        // the draw phase, so it repaints the outlines without recomposing the board every frame.
        val pulse: State<Float> = if (highlight.strong.isEmpty()) {
            remember { mutableFloatStateOf(1f) }
        } else {
            rememberInfiniteTransition(label = "hint").animateFloat(
                initialValue = 0.45f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "hint-pulse",
            )
        }
        val palette = remember(s.region) { regionPalette(s.size, s.region) }

        // Keyed on the board as well as the tap: a board that moved underneath a held tap restarts
        // this, and [held] is null the second time round, so the stale mark is quietly dropped.
        LaunchedEffect(held, s) {
            if (held == null) return@LaunchedEffect
            delay(DOUBLE_TAP_MS)
            pending = null
            onState(held.after)
        }

        // Sized from both axes (CLAUDE.md), so a tall board or a short screen never runs it under
        // the hint slot.
        BoxWithConstraints(Modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) {
            val cell = if (constraints.hasBoundedHeight) minOf(maxWidth / s.size, maxHeight / s.size) else maxWidth / s.size
            val cellPx = with(LocalDensity.current) { cell.toPx() }

            fun cellAt(offset: Offset): Int {
                val r = (offset.y / cellPx).toInt().coerceIn(0, s.size - 1)
                val c = (offset.x / cellPx).toInt().coerceIn(0, s.size - 1)
                return r * s.size + c
            }

            // One tap: a mark now, or a king if its partner arrives in time.
            //
            // Double taps are counted here rather than by `detectTapGestures` so that the second
            // tap has to land on the *same* square. Compose's own detector is a pure timing
            // window — it has no distance check at all — so on a grid this dense it would crown
            // squares the player only meant to cross off on the way past.
            fun tap(offset: Offset) {
                val i = cellAt(offset)
                val now = tapClock.elapsedNow().inWholeMilliseconds
                val live = pending?.takeIf { it.base === s }
                val board = live?.after ?: s

                if (i == lastCell && now - lastTapAt < DOUBLE_TAP_MS) {
                    lastCell = -1
                    pending = null
                    // The pair's own first tap never reached the screen, so the crown replaces it
                    // instead of following it: one entry in the history, one move. A mark held on
                    // some *other* square is still owed to the player, so it rides along in
                    // [board].
                    val from = if (live != null && live.cell == i) live.base else board
                    onState(from.toggleKing(i))
                    return
                }

                lastCell = i
                lastTapAt = now
                // A tap on a different square settles the one before it. Two taps are two entries
                // in the history — collapsing a burst of marks into one would mean a single Undo
                // swallowing the four crosses that came before the one the player regretted.
                if (live != null && live.cell != i) onState(live.after)
                val next = board.toggleMark(i)
                pending = if (next === board) null else PendingTap(i, board, next)
            }

            // Tap and drag are read on the grid as a whole: per-cell `clickable` boxes only ever
            // see the cell the finger went down on, which is the one thing a sweep is not about.
            Box(
                Modifier
                    .size(cell * s.size)
                    .pointerInput(s, interactive) {
                        if (!interactive) return@pointerInput
                        detectTapGestures { offset -> tap(offset) }
                    }
                    .pointerInput(s, interactive) {
                        if (!interactive) return@pointerInput
                        detectDragGestures(
                            onDragStart = { offset ->
                                // A mark still inside its double-tap window is folded into the
                                // sweep's base rather than emitted: emitting mid-gesture would
                                // re-key this pointer input and kill the sweep before it painted
                                // anything.
                                val tapped = pending?.takeIf { it.base === s }
                                carried = tapped
                                pending = null
                                lastCell = -1
                                val board = tapped?.after ?: s
                                val start = cellAt(offset)
                                sweeping = board.sweepMark(start)
                                swept = if (sweeping == null) emptySet() else setOf(start)
                            },
                            onDrag = { change, _ ->
                                if (sweeping != null) swept = swept + cellAt(change.position)
                            },
                            onDragEnd = {
                                val mark = sweeping
                                val board = carried?.after ?: s
                                val next = if (mark != null && swept.isNotEmpty()) {
                                    board.paint(swept, mark)
                                } else {
                                    board
                                }
                                // A sweep that painted nothing and picked nothing up is not a move,
                                // and the screen would push an undo entry for it all the same.
                                if (next !== s) onState(next)
                                carried = null
                                sweeping = null
                                swept = emptySet()
                            },
                            onDragCancel = {
                                // Nothing was emitted, so the tap the sweep picked up goes back on
                                // its own clock.
                                pending = carried
                                carried = null
                                sweeping = null
                                swept = emptySet()
                            },
                        )
                    }
            ) {
                for (r in 0 until s.size) {
                    for (c in 0 until s.size) {
                        val i = r * s.size + c
                        val mark = when {
                            sweepMark != null && i in swept && shown.marks[i] != Mark.KING -> sweepMark
                            else -> shown.marks[i]
                        }
                        val strong = i in highlight.strong
                        val soft = !strong && i in highlight.soft
                        // Everything the highlight does not name steps back, so the named squares
                        // read without having to hunt for their outlines. The tiles are opaque, so
                        // stepping back means fading toward the page rather than going see-through.
                        val dim = !(highlight.isEmpty || strong || soft)
                        Box(
                            Modifier
                                .padding(start = cell * c, top = cell * r)
                                .size(cell)
                                .padding(1.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Color(regionColours[palette[s.region[i]]]).let {
                                        if (dim) it.copy(alpha = 0.4f).compositeOver(scheme.background) else it
                                    }
                                )
                                .then(
                                    when {
                                        strong -> Modifier.drawWithContent {
                                            drawContent()
                                            val w = 4.dp.toPx()
                                            drawRoundRect(
                                                glow.copy(alpha = pulse.value),
                                                topLeft = Offset(w / 2, w / 2),
                                                size = Size(size.width - w, size.height - w),
                                                cornerRadius = CornerRadius(4.dp.toPx()),
                                                style = Stroke(w),
                                            )
                                        }
                                        soft -> Modifier.border(
                                            1.5.dp, glow.copy(alpha = 0.5f), RoundedCornerShape(4.dp),
                                        )
                                        else -> Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            when (mark) {
                                // A clashing crown is filled in the error colour, which on a red
                                // or orange tile would be all but invisible, so it keeps an ink rim.
                                Mark.KING -> if (i in conflicts) {
                                    Crown(cell, scheme.error, rim = MarkInk)
                                } else {
                                    Crown(cell, MarkInk)
                                }
                                Mark.BLOCKED -> BlockedCross(cell)
                                // A square the board has ruled out is a fact, not a suggestion, so
                                // it is written in the same hand as the player's own crosses.
                                Mark.EMPTY -> if (i in eliminated) BlockedCross(cell)
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
     * Stroked rather than filled, and a shade lighter than [Crown]'s solid ink, so a glance
     * separates the crosses from the kings by weight without having to resolve either shape. It
     * used to be drawn in the page colour for opposite polarity as well, but on opaque tiles as
     * light as these a pale cross drops to 1.4:1; at 75% ink it keeps at least 3:1 on every tile.
     */
    @Composable
    private fun BlockedCross(cell: Dp) {
        Canvas(Modifier.fillMaxSize().padding(cell * 0.28f)) {
            val ink = MarkInk.copy(alpha = 0.75f)
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
     *
     * [rim], when given, is a thin ring of that colour round the silhouette: a clashing crown is
     * filled in the error colour, which has too little contrast with the warmer tiles to carry the
     * shape on its own.
     */
    @Composable
    private fun Crown(cell: Dp, colour: Color, rim: Color? = null) {
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
            if (rim != null) {
                // Drawn first and twice the width wanted, so the fill covers its inner half.
                drawPath(crown, rim, style = Stroke(width = w * 0.12f, join = StrokeJoin.Round))
            }
            drawPath(crown, colour)
        }
    }
}
