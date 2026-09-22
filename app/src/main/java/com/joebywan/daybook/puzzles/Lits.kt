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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.Rng
import kotlinx.serialization.Serializable

@Serializable
data class LitsState(
    val width: Int,
    val height: Int,
    val region: List<Int>,
    val shaded: List<Boolean>,
    val solution: List<Boolean>,
    override val moves: Int = 0,
) : PuzzleState {

    /**
     * Judged by the rules, never by comparison with [solution].
     *
     * A LITS board can admit more than one legal shading, and this used to read
     * `shaded == solution`: a player who found a different but entirely valid answer was told they
     * had not finished. [solution] is still carried because [Lits.hint] has to nudge towards
     * *some* answer, but it no longer decides anything.
     */
    override val solved: Boolean get() = Lits.isSolved(width, height, region, shaded)

    fun toggle(index: Int): LitsState =
        copy(shaded = shaded.toMutableList().also { it[index] = !it[index] }, moves = moves + 1)

    /** The letter of the tetromino each shaded square belongs to, where its region has settled. */
    fun letters(): List<Lits.Piece?> = Lits.letters(width, height, region, shaded)

    /** Squares no legal answer can shade while the shading already on the board stays put. */
    fun impossible(): Set<Int> = Lits.impossible(width, height, region, shaded)
}

/**
 * LITS.
 *
 * Shade a four-square tetromino in each region so the shading forms one connected area, contains
 * no full two-by-two block, and never puts two tetrominoes of the same letter edge to edge.
 *
 * Generation partitions the grid, enumerates each region's legal tetrominoes, and keeps the
 * partition only when the solver *proves* the board has exactly one global solution.
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
        "A finished tetromino takes its letter's colour and carries the letter, so two blocks " +
            "touching in one colour are the rule being broken.",
        "Squares that can no longer be shaded are crossed off for you.",
    )

    /**
     * The four legal letters. A 2x2 block is deliberately not one of them.
     *
     * Public because the letter is no longer only the solver's business: it is what the board
     * colours a finished tetromino by, and what a test can pin that colouring against.
     */
    enum class Piece { I, L, T, S }

    private fun shape(difficulty: Difficulty) = when (difficulty) {
        Difficulty.STANDARD -> 6 to 6
        Difficulty.HARD -> 7 to 7
        Difficulty.EXPERT -> 8 to 8
    }

    /**
     * The home tile: a 4x4 board carved into four four-square regions, with the top-left one
     * shaded into its L.
     *
     * Four across rather than a Standard board's six, because what identifies LITS is the heavy
     * region wall wandering across the grid, and at 80dp a six-wide board turns those walls into
     * a texture. The four regions are deliberately interlocking rather than tidy blocks — two of
     * them reach around each other — since a grid quartered into 2x2 squares would illustrate the
     * one shape the rules forbid.
     */
    private const val PREVIEW_SIDE = 4
    private val previewRegions = listOf(
        0, 0, 1, 1,
        0, 1, 1, 2,
        0, 3, 2, 2,
        3, 3, 3, 2,
    )
    private val previewShaded = listOf(
        true, true, false, false,
        true, false, false, false,
        true, false, false, false,
        false, false, false, false,
    )

    // ---- the win condition --------------------------------------------------------------------

    /**
     * True when [shaded] satisfies every LITS rule on this board.
     *
     * Written against the rules rather than against a stored answer on purpose. Uniqueness is a
     * property the *generator* tries to guarantee; the player must be believed either way, because
     * a board that slipped through ambiguous is the generator's failure, not theirs.
     *
     * Public because the rules test re-derives boards independently, and because it is the single
     * definition of "finished" the UI and the tests should share.
     */
    fun isSolved(
        width: Int,
        height: Int,
        region: List<Int>,
        shaded: List<Boolean>,
    ): Boolean {
        val n = width * height
        if (region.size != n || shaded.size != n || n == 0) return false

        // Every region needs its own four squares: a region left blank is not "not yet wrong".
        val quads = HashMap<Int, MutableList<Int>>()
        for (cell in 0 until n) if (shaded[cell]) quads.getOrPut(region[cell]) { mutableListOf() } += cell
        if (quads.size != region.toHashSet().size) return false

        val letters = arrayOfNulls<Piece>(n)
        for (quad in quads.values) {
            if (quad.size != 4) return false
            if (!isConnected(quad, width, height)) return false
            val piece = classify(quad, width) ?: return false   // null is the banned 2x2 block
            quad.forEach { letters[it] = piece }
        }

        for (r in 0 until height - 1) for (c in 0 until width - 1) {
            val i = r * width + c
            if (shaded[i] && shaded[i + 1] && shaded[i + width] && shaded[i + width + 1]) return false
        }

        // Same-letter contact only needs checking across a region wall: inside a region every
        // shaded square belongs to the one tetromino.
        for (cell in 0 until n) {
            if (!shaded[cell]) continue
            for (next in neighbours(cell, width, height)) {
                if (!shaded[next] || region[next] == region[cell]) continue
                if (letters[cell] == letters[next]) return false
            }
        }

        return isConnected((0 until n).filter { shaded[it] }, width, height)
    }

    // ---- what the board can show the player ---------------------------------------------------

    /**
     * The letter of the tetromino each shaded square belongs to, or null where there is not one.
     *
     * A region earns a letter only once its four squares are down and legal. Fewer than four, a
     * fifth, a shape in two pieces or the forbidden 2x2 all leave every square of that region
     * unnamed, because naming a half-shaded region would mean picking one of the shapes it could
     * still become — and the colour would then flicker between letters as the player worked,
     * asserting a deduction nobody has made.
     *
     * Public for the same reason [isSolved] is: the board and the tests should read the letters
     * from one place.
     */
    fun letters(
        width: Int,
        height: Int,
        region: List<Int>,
        shaded: List<Boolean>,
    ): List<Piece?> {
        val n = width * height
        if (region.size != n || shaded.size != n) return List(n) { null }

        val out = arrayOfNulls<Piece>(n)
        val quads = HashMap<Int, MutableList<Int>>()
        for (cell in 0 until n) if (shaded[cell]) quads.getOrPut(region[cell]) { mutableListOf() } += cell
        for (quad in quads.values) {
            if (quad.size != 4 || !isConnected(quad, width, height)) continue
            val piece = classify(quad, width) ?: continue
            quad.forEach { out[it] = piece }
        }
        return out.asList()
    }

    /**
     * Squares that cannot be shaded while everything already shaded stays where it is.
     *
     * Derived from the shading on every read rather than written into the state, exactly as
     * `Kings.eliminated` is: a stored cross has to be retracted when the shading that justified it
     * is rubbed out, and telling those apart from the player's own marks across undo, restart and
     * hints is the bookkeeping that rots. Recomputing is a few hundred set operations.
     *
     * One rule decides it, rather than a list of special cases. Shading a square commits its region
     * to *some* legal tetromino covering both that square and everything the region already holds,
     * so the square is impossible exactly when no such tetromino survives contact with the rest of
     * the board. That subsumes the obvious cases — a region already holding its four squares admits
     * no five-square cover, and a square whose neighbours complete a 2x2 poisons every cover that
     * includes it — and adds the ones a player would otherwise have to find by hand: a square its
     * region simply cannot fit a tetromino around, and a square whose every remaining shape would
     * land the region's letter against a finished tetromino of the same letter.
     *
     * Every clause reads only squares that are shaded *now*, so each cross means "not while the
     * board says this", which is the same promise Kings' crosses make about the kings standing on
     * it. Nothing here guesses at shading still to come: a cover is only condemned by squares the
     * player has actually put down.
     */
    fun impossible(
        width: Int,
        height: Int,
        region: List<Int>,
        shaded: List<Boolean>,
    ): Set<Int> {
        val n = width * height
        if (region.size != n || shaded.size != n) return emptySet()

        val letters = letters(width, height, region, shaded)
        val members = HashMap<Int, MutableList<Int>>()
        for (cell in 0 until n) members.getOrPut(region[cell]) { mutableListOf() } += cell

        /** Would this cover, standing on the shading already down, fill a 2x2 anywhere? */
        fun fillsBlock(quad: List<Int>): Boolean = quad.any { cell ->
            val r = cell / width
            val c = cell % width
            (-1..0).any { dr ->
                (-1..0).any { dc ->
                    val rr = r + dr
                    val cc = c + dc
                    rr >= 0 && cc >= 0 && rr + 1 < height && cc + 1 < width &&
                        listOf(
                            rr * width + cc, rr * width + cc + 1,
                            (rr + 1) * width + cc, (rr + 1) * width + cc + 1,
                        ).all { it in quad || shaded[it] }
                }
            }
        }

        /** Would this cover sit its letter against a region that has already settled on it? */
        fun clashes(quad: List<Int>, piece: Piece): Boolean = quad.any { cell ->
            neighbours(cell, width, height).any { next ->
                next !in quad && shaded[next] && letters[next] == piece
            }
        }

        val covers = HashMap<Int, List<kotlin.Pair<List<Int>, Piece>>>()
        val out = mutableSetOf<Int>()
        for (cell in 0 until n) {
            if (shaded[cell]) continue
            val id = region[cell]
            val cells = members.getValue(id)
            val forced = cells.filter { shaded[it] } + cell
            if (forced.size > 4) {
                out += cell
                continue
            }
            val fits = covers.getOrPut(id) { tetrominoes(cells, width, height) }
                .filter { it.first.containsAll(forced) }
            if (fits.isEmpty() || fits.all { (quad, piece) -> fillsBlock(quad) || clashes(quad, piece) }) {
                out += cell
            }
        }
        return out
    }

    // ---- generation ---------------------------------------------------------------------------

    /**
     * How many nodes one uniqueness check may visit.
     *
     * Only a guard against a pathological partition: a check that hits it proves nothing and is
     * thrown away, so the number trades candidates-discarded against wall clock. Regions this size
     * exhaust three orders of magnitude below the cap, and raising it from the old 80k costs
     * nothing measurable while making truncation vanishingly rare.
     */
    private const val NODE_BUDGET = 400_000

    /** Ceiling on uniqueness checks per board, which is what actually bounds generation time. */
    private const val SEARCH_BUDGET = 4000

    /** Piece layouts tried per board, and partitions carved per layout. */
    private const val ATTEMPTS = 150
    private const val WALL_TRIES = 2

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState {
        val (w, h) = shape(difficulty)
        val n = w * h
        // Denser shading means a more constrained — and far more often unique — board, but the
        // no-2x2 rule caps it near two thirds of the grid, so the generator aims at a fifth of the
        // squares' worth of pieces and settles for a sixth.
        val targetPieces = maxOf(4, n / 5)
        val minPieces = maxOf(4, n / 6)

        // Every candidate is a legal, finishable board by construction — the shading is laid down
        // first and the regions drawn around it — so the first one built is kept as a floor. Only
        // its *uniqueness* is ever in doubt.
        var floor: LitsState? = null
        var searches = 0

        for (attempt in 0 until ATTEMPTS) {
            if (searches >= SEARCH_BUDGET) break
            val rng = Rng(seed + attempt)
            val placed = placeTetrominoes(rng, w, h, targetPieces, minPieces) ?: continue
            val shading = List(n) { cell -> placed.any { cell in it.first } }

            // The shading is fixed at this point; only the walls are still free, so a couple of
            // partitions are carved per layout of pieces before giving up on it.
            for (wallTry in 0 until WALL_TRIES) {
                if (searches >= SEARCH_BUDGET) break
                val (region, intact) = carve(rng, w, h, placed) { searches++ }
                if (floor == null) floor = LitsState(w, h, region, List(n) { false }, shading)
                if (!intact) continue

                val options = placed.indices.map { r ->
                    tetrominoes(region.indices.filter { region[it] == r }, w, h)
                }
                if (options.any { it.isEmpty() }) continue
                searches++
                // Only an exactly-one verdict may ship. Truncated is explicitly not one.
                val verdict = search(w, h, options)
                if (verdict is Verdict.ExactlyOne) {
                    return LitsState(w, h, region, List(n) { false }, verdict.shading)
                }
            }
        }

        // Nothing was *proved* unique inside the budget. The floor still obeys every rule and can
        // be finished; now that the win condition is the rules rather than a stored answer, a
        // second legal answer is a blemish rather than a wrong-answer bug, so it beats shipping
        // nothing. FallbackTest pins how rarely this is reached.
        return floor ?: lastResort(seed, w, h)
    }

    /**
     * Only reachable if every placement attempt dead-ended, which has never been observed. It
     * exists so [generate] is total: a daily puzzle must never throw.
     */
    private fun lastResort(seed: Long, w: Int, h: Int): LitsState {
        val n = w * h
        val placed = (0 until 64).firstNotNullOfOrNull {
            placeTetrominoes(Rng(seed + 7717L + it), w, h, 4, 4)
        } ?: listOf(listOf(0, w, 2 * w, 3 * w) to Piece.I)
        return LitsState(
            w, h,
            carve(Rng(seed), w, h, placed) {}.first,
            List(n) { false },
            List(n) { cell -> placed.any { cell in it.first } },
        )
    }

    /**
     * Hands out the squares the tetrominoes do not cover, one at a time, never letting the board
     * stop being uniquely solvable.
     *
     * Drawing the walls first and checking afterwards was the losing strategy: a random partition
     * of a board this size typically admits *hundreds* of legal shadings, and steering one of those
     * down to a single answer costs more search than starting over. Handing out squares one by one
     * inverts the problem. With every region exactly its own tetromino the board is trivially
     * unique, and giving a square to a region only ever adds shadings that region might hold —
     * never removes one — so the solution count is monotone and uniqueness can be re-checked after
     * each square, with the square offered to a different neighbour when it would introduce a
     * second answer. Monotonicity also means this is not over-strict: a partition that is unique
     * when finished is unique at every step along the way, so nothing is being ruled out that the
     * old approach could have found.
     *
     * Returns the partition together with whether the invariant held the whole way. A run that
     * cannot place a square without breaking it finishes the partition anyway, so the caller still
     * has a legal board to fall back on, and is told not to trust it as unique.
     */
    private fun carve(
        rng: Rng,
        w: Int,
        h: Int,
        placed: List<kotlin.Pair<List<Int>, Piece>>,
        onSearch: () -> Unit,
    ): kotlin.Pair<List<Int>, Boolean> {
        val n = w * h
        val region = MutableList(n) { -1 }
        placed.forEachIndexed { index, (quad, _) -> quad.forEach { region[it] = index } }
        val sizes = IntArray(placed.size) { 4 }
        val cells = Array(placed.size) { placed[it].first.toMutableList() }
        val opts = Array(placed.size) { tetrominoes(cells[it], w, h) }

        var intact = true
        var remaining = region.count { it == -1 }
        // Squares no region can take without adding a second answer. They are set aside rather
        // than fatal: claiming their other neighbours can give them a region that will.
        val refused = HashMap<Int, Set<Int>>()

        var guard = 0
        while (remaining > 0 && guard++ < n * 8) {
            val frontier = (0 until n).filter { cell ->
                region[cell] == -1 && neighbours(cell, w, h).any { region[it] != -1 }
            }
            if (frontier.isEmpty()) break
            val open = if (!intact) frontier else frontier.filter { cell ->
                refused[cell]?.containsAll(hostsOf(cell, w, h, region)) != true
            }
            if (open.isEmpty()) { intact = false; continue }

            val cell = rng.pick(open)
            // Smallest region first, so no one region swallows the leftovers.
            val hosts = rng.shuffled(hostsOf(cell, w, h, region).toList()).sortedBy { sizes[it] }

            var chosen = -1
            if (intact) {
                for (host in hosts) {
                    val grown = tetrominoes(cells[host] + cell, w, h)
                    if (grown.isEmpty()) continue
                    val trial = opts.copyOf()
                    trial[host] = grown
                    onSearch()
                    if (search(w, h, trial.asList()) is Verdict.ExactlyOne) {
                        chosen = host
                        opts[host] = grown
                        break
                    }
                }
                if (chosen == -1) {
                    refused[cell] = hosts.toSet()
                    continue
                }
            } else {
                chosen = hosts.first()
                opts[chosen] = tetrominoes(cells[chosen] + cell, w, h)
            }
            cells[chosen] += cell
            region[cell] = chosen
            sizes[chosen]++
            remaining--
        }

        if (remaining > 0) intact = false
        // Whatever is still unclaimed joins a neighbour, so the partition always covers the grid.
        var sweep = 0
        while (region.contains(-1) && sweep++ < n) {
            for (cell in 0 until n) {
                if (region[cell] != -1) continue
                val hosts = hostsOf(cell, w, h, region)
                if (hosts.isNotEmpty()) region[cell] = rng.pick(hosts.toList())
            }
        }
        return region.map { if (it == -1) 0 else it } to intact
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
        floor: Int,
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

        // [target] is an ambition, not a requirement: the denser the shading the more constrained
        // the board, but past about two thirds of the grid the no-2x2 rule makes a full house
        // impossible. Giving up after a run of dead ends rather than grinding out the whole guard
        // is what keeps a near-miss layout cheap, and near misses are the common case.
        var guard = 0
        var idle = 0
        while (placed.size < target && guard++ < target * 300 && idle < 120) {
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
            if (candidates.isEmpty()) {
                idle++
                continue
            }
            idle = 0

            val (quad, piece) = rng.pick(candidates)
            quad.forEach { shaded[it] = true; pieceAt[it] = piece }
            placed += quad to piece
        }

        // Four is the hard floor FallbackTest pins: fewer regions than that and the board reads as
        // a puzzle that gave up.
        return if (placed.size >= maxOf(4, floor)) placed else null
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

    /**
     * Grows each region outward from its tetromino until every square belongs to one.
     *
     * This used to cap region size and bail out when it could not place a square, which it could
     * not help doing: piece count times the cap was smaller than the grid at every difficulty, so
     * the partition *always* failed and every board shipped came from the degenerate fallback.
     * Growing the currently smallest neighbouring region instead always terminates — the grid is
     * connected, so while squares remain unclaimed at least one of them touches a region — and
     * keeps the regions close to the same size without needing a cap to enforce it.
     */
    private fun hostsOf(cell: Int, w: Int, h: Int, region: List<Int>): Set<Int> =
        neighbours(cell, w, h).map { region[it] }.filter { it != -1 }.toSet()

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
        if (cells.isEmpty()) return false
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
     * row counts and the odd square's position separate L, T and S. Reflections collapse onto the
     * same letter because only the *distance* of the odd square from the end of the long row is
     * read, which is what the rule about touching pieces means by "the same letter".
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

    /**
     * What a bounded search is entitled to conclude — and nothing more.
     *
     * The old solver returned a list of solutions and gave up silently at a node cap, so the caller
     * read `solutions.size == 1` and could not tell "there is exactly one answer" from "the search
     * ran out of budget after finding one". Ambiguous boards were recorded as unique and shipped;
     * that was the root cause of players' valid answers being rejected. A count cannot carry that
     * distinction, so the search returns this instead, and only [ExactlyOne] — which is issued
     * solely after the tree has been covered — is allowed to become a puzzle.
     */
    private sealed interface Verdict {
        /** The tree was covered and held no legal shading. */
        data object None : Verdict

        /** The tree was covered and held exactly this one. Safe to ship. */
        data class ExactlyOne(val shading: List<Boolean>) : Verdict

        /** Two distinct shadings were produced. Proof of ambiguity needs no exhaustion. */
        data object Ambiguous : Verdict

        /** The node budget ran out first, so *nothing* is proven. Never a puzzle. */
        data object Truncated : Verdict
    }

    /**
     * Exhaustive backtracking over one tetromino per region, stopping early only at proof.
     *
     * Two prunes carry the search. Placements are rejected the moment they complete a 2x2 block or
     * sit a letter against its twin, and — the expensive rule made cheap — the shading so far must
     * still be reachable through squares that are yet to be decided. Connectivity was previously
     * only tested at the leaf, so the search walked whole subtrees whose shading had already been
     * cut in two by regions it had finished with.
     */
    private fun search(
        w: Int,
        h: Int,
        options: List<List<kotlin.Pair<List<Int>, Piece>>>,
    ): Verdict {
        val n = w * h
        val shaded = BooleanArray(n)
        val pieceAt = arrayOfNulls<Piece>(n)
        var first: List<Boolean>? = null
        var count = 0
        var nodes = 0
        var truncated = false

        // Fewest choices first. Region order does not change the answer, only how fast it is found.
        val order = options.indices.sortedBy { options[it].size }

        // undecided[d] marks the squares still owned by regions this search has not reached at
        // depth d — the only squares through which shading may still be joined up.
        val undecided = Array(options.size + 1) { BooleanArray(n) }
        for (d in options.indices.reversed()) {
            undecided[d + 1].copyInto(undecided[d])
            for (cell in 0 until n) {
                if (options[order[d]].any { cell in it.first }) undecided[d][cell] = true
            }
        }

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

        /** Can every shaded square still reach every other, allowing for squares not yet decided? */
        fun stillJoinable(open: BooleanArray): Boolean {
            val start = (0 until n).firstOrNull { shaded[it] } ?: return true
            val seen = BooleanArray(n)
            val stack = ArrayDeque<Int>()
            stack.addLast(start)
            seen[start] = true
            var reached = 1   // [start] is itself shaded
            while (stack.isNotEmpty()) {
                val cell = stack.removeLast()
                for (next in neighbours(cell, w, h)) {
                    if (seen[next] || !(shaded[next] || open[next])) continue
                    seen[next] = true
                    if (shaded[next]) reached++
                    stack.addLast(next)
                }
            }
            return reached == (0 until n).count { shaded[it] }
        }

        fun place(depth: Int) {
            if (count >= 2 || truncated) return
            if (nodes++ > NODE_BUDGET) {
                truncated = true
                return
            }
            if (depth == options.size) {
                // undecided[size] is empty, so this is plain connectivity.
                if (stillJoinable(undecided[depth])) {
                    count++
                    if (first == null) first = shaded.toList()
                }
                return
            }
            for ((quad, piece) in options[order[depth]]) {
                quad.forEach { shaded[it] = true; pieceAt[it] = piece }
                if (!makesSquare(quad) &&
                    !touchesSameLetter(quad, piece) &&
                    stillJoinable(undecided[depth + 1])
                ) {
                    place(depth + 1)
                }
                quad.forEach { shaded[it] = false; pieceAt[it] = null }
                if (count >= 2 || truncated) return
            }
        }

        place(0)

        // Order matters: two answers in hand is proof of ambiguity whether or not the budget also
        // ran out, but one answer plus a truncated search proves nothing at all.
        val witness = first
        return when {
            count >= 2 -> Verdict.Ambiguous
            truncated -> Verdict.Truncated
            witness != null -> Verdict.ExactlyOne(witness)
            else -> Verdict.None
        }
    }

    // ---- play ---------------------------------------------------------------------------------

    // ---- drawing ------------------------------------------------------------------------------

    /**
     * A colour per letter, so that the rule about touching tetrominoes becomes something a player
     * can see rather than something the win condition knows privately. Every shaded square used to
     * be the one accent, which made an L and an S identical on screen: the rule was enforced all
     * along — LitsAuditTest pins that — but nobody could tell it was there.
     *
     * Blue, amber, green and vermillion: four hues that stay apart from each other, sit clear of
     * the unshaded [androidx.compose.material3.ColorScheme.surfaceVariant] in both themes, and are
     * mid-toned enough that the region walls — ink on parchment, parchment on ink — still read
     * across them. They are deliberately not the scheme's own colours, which are a green and an
     * amber that the board would then share with its own furniture.
     *
     * Hue is only half of it. Four colours is exactly where colour blindness stops being a corner
     * case — green against vermillion is the common confusion, and blue against purple the next —
     * so the letter is *also* written on the square. The glyph is what a player who cannot separate
     * two of these hues reads instead, and it costs nothing: it says the same thing the rules
     * already print.
     */
    private fun colourOf(piece: Piece): Long = when (piece) {
        Piece.I -> 0xFF4C86D9
        Piece.L -> 0xFFE0B23C
        Piece.T -> 0xFF54B07A
        Piece.S -> 0xFFD9584C
    }

    /**
     * Shaded, but not yet anything: the puzzle's own accent, which is what every shaded square
     * looked like before the letters had colours.
     *
     * A part-shaded region is a decision in progress, and this is the one tone on the board that
     * makes no claim about which letter it will become. It is a warm neutral against four
     * saturated hues, so "still working on it" and "settled into an L" are never each other.
     */
    private val inProgress = Color(accent)

    /** The share of a cell a crossed-off square keeps clear, matching Kings' crosses. */
    private const val CROSS_INSET = 0.28f

    override fun hint(state: PuzzleState): PuzzleState? {
        val s = state as LitsState
        val wrong = s.shaded.indices.firstOrNull { s.shaded[it] != s.solution[it] } ?: return null
        return s.toggle(wrong)
    }

    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme

        Canvas(modifier) {
            val side = minOf(size.width, size.height)
            val origin = Offset((size.width - side) / 2f, (size.height - side) / 2f)
            val step = side / PREVIEW_SIDE
            // The board's wall is 6% of a cell. At tile size that is under two pixels and the
            // regions stop reading as regions, so the motif takes the walls up to a tenth of a
            // cell — the one thing about a LITS board that has to survive being shrunk.
            val wall = step * 0.10f

            for (i in 0 until PREVIEW_SIDE * PREVIEW_SIDE) {
                val at = Offset(
                    origin.x + (i % PREVIEW_SIDE) * step,
                    origin.y + (i / PREVIEW_SIDE) * step,
                )
                drawRect(
                    // The motif's shading is a finished L, so the tile wears the L's colour —
                    // otherwise the home grid would advertise a board that no longer exists. The
                    // letter itself is left off: at a 20dp cell the glyph is a smudge, and the tile
                    // has to read as LITS from across a grid, not be read word by word.
                    color = if (previewShaded[i]) Color(colourOf(Piece.L)) else scheme.surfaceVariant,
                    topLeft = at,
                    size = Size(step, step),
                )
                drawRect(
                    color = scheme.background.copy(alpha = 0.35f),
                    topLeft = at,
                    size = Size(step, step),
                    style = Stroke(width = 1f),
                )
            }

            for (i in 0 until PREVIEW_SIDE * PREVIEW_SIDE) {
                val r = i / PREVIEW_SIDE
                val c = i % PREVIEW_SIDE
                if (c + 1 < PREVIEW_SIDE && previewRegions[i] != previewRegions[i + 1]) {
                    drawLine(
                        scheme.onBackground,
                        Offset(origin.x + (c + 1) * step, origin.y + r * step),
                        Offset(origin.x + (c + 1) * step, origin.y + (r + 1) * step),
                        strokeWidth = wall,
                    )
                }
                if (r + 1 < PREVIEW_SIDE && previewRegions[i] != previewRegions[i + PREVIEW_SIDE]) {
                    drawLine(
                        scheme.onBackground,
                        Offset(origin.x + c * step, origin.y + (r + 1) * step),
                        Offset(origin.x + (c + 1) * step, origin.y + (r + 1) * step),
                        strokeWidth = wall,
                    )
                }
            }
            drawRect(
                color = scheme.onBackground,
                topLeft = Offset(origin.x + wall / 2f, origin.y + wall / 2f),
                size = Size(side - wall, side - wall),
                style = Stroke(width = wall),
            )
        }
    }

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as LitsState
        val scheme = MaterialTheme.colorScheme
        val measurer = rememberTextMeasurer()

        // Both are a function of the shading and nothing else, so they are recomputed when — and
        // only when — the board changes, the way Mambo remembers its violations.
        val letters = remember(s) { s.letters() }
        val impossible = remember(s) { s.impossible() }

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
                    val at = Offset(c * stepPx, r * stepPx)
                    val piece = letters[i]
                    drawRect(
                        color = when {
                            !s.shaded[i] -> scheme.surfaceVariant
                            piece == null -> inProgress
                            else -> Color(colourOf(piece))
                        },
                        topLeft = at,
                        size = Size(stepPx, stepPx),
                    )
                    drawRect(
                        color = scheme.background.copy(alpha = 0.35f),
                        topLeft = at,
                        size = Size(stepPx, stepPx),
                        style = Stroke(width = 1f),
                    )
                    // Marks go on before the walls, so a wall is never drawn under one: the walls
                    // are what the player reads the regions from and they outrank both of these.
                    if (piece != null) {
                        // Written in whichever of the page and its ink is the darker, rather than
                        // in the page colour Kings uses for its marks. All four letter colours are
                        // mid-to-light, so a dark letter carries three to five times the contrast
                        // of a pale one on every one of them — and the glyph is the channel a
                        // player who cannot separate two of the hues is left with, so it is the one
                        // place on this board where legibility outranks matching Kings' polarity.
                        val glyph = listOf(scheme.background, scheme.onBackground)
                            .minBy { it.luminance() }
                        val layout = measurer.measure(
                            piece.name,
                            TextStyle(
                                color = glyph.copy(alpha = 0.85f),
                                fontSize = (stepPx * 0.42f).toSp(),
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        drawText(
                            layout,
                            topLeft = Offset(
                                at.x + (stepPx - layout.size.width) / 2f,
                                at.y + (stepPx - layout.size.height) / 2f,
                            ),
                        )
                    } else if (i in impossible) {
                        // Kings' cross, at Kings' proportions, so the two boards agree that a cross
                        // means "not here". Drawn in the ink rather than the page, which is the one
                        // departure: Kings crosses saturated region tiles, while an unshaded LITS
                        // square is surfaceVariant — a pale tint of the page — and a
                        // background-coloured cross on it would be all but invisible.
                        val inset = stepPx * CROSS_INSET
                        val span = stepPx - inset * 2f
                        val ink = scheme.onSurfaceVariant.copy(alpha = 0.55f)
                        drawLine(
                            ink,
                            Offset(at.x + inset, at.y + inset),
                            Offset(at.x + inset + span, at.y + inset + span),
                            strokeWidth = span * 0.2f,
                            cap = StrokeCap.Round,
                        )
                        drawLine(
                            ink,
                            Offset(at.x + inset, at.y + inset + span),
                            Offset(at.x + inset + span, at.y + inset),
                            strokeWidth = span * 0.2f,
                            cap = StrokeCap.Round,
                        )
                    }
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
                    style = Stroke(width = edge),
                )
            }
        }
    }
}
