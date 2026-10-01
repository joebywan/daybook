package com.joebywan.daybook.puzzles

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.LocalBoardHighlight
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.jvmHashSetOrder
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

    /**
     * Sets every square in [cells] to [on] in a single state, for a drag across the board.
     *
     * One state for the whole drag because the play screen pushes an undo entry per state: one
     * Undo walks back one gesture. The drag repeats the decision taken on its first square, as
     * Kings' sweep does, rather than flipping each square it crosses. It scores one move per
     * square actually changed, the taps it stands in for.
     */
    fun paint(cells: Collection<Int>, on: Boolean): LitsState {
        val next = shaded.toMutableList()
        var changed = 0
        for (i in cells) {
            if (next[i] == on) continue
            next[i] = on
            changed++
        }
        return if (changed == 0) this else copy(shaded = next, moves = moves + changed)
    }

    /** The letter of the tetromino each shaded square belongs to, where its region has settled. */
    fun letters(): List<Lits.Piece?> = Lits.letters(width, height, region, shaded)

    /** Squares no legal answer can shade while the shading already on the board stays put. */
    fun impossible(): Set<Int> = Lits.impossible(width, height, region, shaded)
}

/**
 * LITS.
 *
 * Shade a four-square tetromino in each region so the shading contains no full two-by-two block
 * and never puts two tetrominoes of the same letter edge to edge. Unlike classic LITS, the shading
 * does not have to form one connected area.
 *
 * Generation partitions the grid, enumerates each region's legal tetrominoes, and keeps the
 * partition only when the solver *proves* the board has exactly one solution under the win check's
 * rules, connected or not. The carving steps search connected shadings only (fast, and monotone as
 * squares are handed out); the finished candidate is then proved again without that restriction,
 * because a disconnected answer that keeps every other rule is accepted by [isSolved].
 */
object Lits : PuzzleType {

    override val id = "lits"
    override val displayName = "LITS"
    override val tagline = "One tetromino in every region"
    override val accent = 0xFF9A8264
    override val rules = listOf(
        "Shade exactly four squares in every region, forming an L, I, T or S tetromino.",
        "A 2x2 square of shading is never allowed.",
        "Two tetrominoes of the same letter may not touch edge to edge, even across regions.",
        "Tap a square to shade or clear it, or drag across several to do the same to each.",
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

        // No connectivity check. The shading may fall into separate pieces: a board with a legal
        // tetromino in every region, no 2x2 and no same-letter contact is finished. A player's
        // disconnected answer on the 23 Sept 2026 Expert board used to be refused, with every
        // other square already crossed off and nothing saying why.
        return true
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

    /**
     * The most squares a region may hold, four of them its tetromino.
     *
     * Uncapped, one region in six or so came out at eight squares or more (fourteen at worst on
     * Expert), big enough to nearly hold two tetrominoes. Most of such a region is squares that
     * end up crossed off, which makes the board easier without making it more interesting. Six
     * was tried and leaves too little room: almost no board could then be proved unique.
     */
    private const val MAX_REGION = 7

    /**
     * Piece layouts laid down per attempt, keeping the one with the most tetrominoes. More pieces
     * means fewer spare squares to share out, which is what lets the regions fit under
     * [MAX_REGION]. Measured over 40 days per tier: from one layout, 17 of 40 Expert boards could
     * be proved unique under the cap; from 24, 39 of 40. Twelve was no faster, because the boards
     * it could not prove under the cap paid for the uncapped retry.
     */
    private const val LAYOUTS = 24

    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState = build(seed, difficulty).let { (proved, floor) ->
        // Nothing was *proved* unique inside the budget. The floor still obeys every rule and can
        // be finished, and a second legal answer is a blemish rather than a wrong-answer bug (the
        // win check is the rules, not a stored answer), so it beats shipping nothing. FallbackTest
        // pins how rarely this is reached.
        proved ?: floor ?: lastResort(seed, shape(difficulty).first, shape(difficulty).second)
    }

    /**
     * The board [generate] makes, or null when nothing was proved to have exactly one answer under
     * the win check's rules: what a test asserts, because [generate] would then ship a floor.
     */
    internal fun generateVerified(seed: Long, difficulty: Difficulty): LitsState? = build(seed, difficulty).first

    /** The proved board if any, and the first legal-but-unproved candidate built along the way. */
    private fun build(seed: Long, difficulty: Difficulty): kotlin.Pair<LitsState?, LitsState?> {
        val (w, h) = shape(difficulty)
        val n = w * h
        val g = geometry(w, h)
        // Denser shading means a more constrained — and far more often unique — board, but the
        // no-2x2 rule caps it near two thirds of the grid, so the generator aims at a fifth of the
        // squares' worth of pieces and settles for a sixth.
        val targetPieces = maxOf(4, n / 5)
        val minPieces = maxOf(4, n / 6)

        // Every candidate is a legal, finishable board by construction — the shading is laid down
        // first and the regions drawn around it — so the first one built is kept as a floor. Only
        // its *uniqueness* is ever in doubt.
        var floor: LitsState? = null

        // Capped first. Should no board be provable under the cap, a proved board with one larger
        // region still beats the unproved floor, so the cap is dropped rather than the proof.
        for (cap in listOf(MAX_REGION, Int.MAX_VALUE)) {
            var searches = 0
            for (attempt in 0 until ATTEMPTS) {
                if (searches >= SEARCH_BUDGET) break
                val rng = Rng(seed + attempt)
                val placed = (0 until LAYOUTS)
                    .mapNotNull { placeTetrominoes(rng, g, targetPieces, minPieces) }
                    .maxByOrNull { it.size } ?: continue
                // Too few pieces to share the grid out under the cap, so no partition can exist.
                if (placed.size.toLong() * cap < n) continue
                val shadingMask = placed.fold(0L) { acc, q -> acc or g.mask[q] }
                val shading = List(n) { cell -> shadingMask and (1L shl cell) != 0L }

                // The shading is fixed at this point; only the walls are still free, so a couple
                // of partitions are carved per layout of pieces before giving up on it.
                for (wallTry in 0 until WALL_TRIES) {
                    if (searches >= SEARCH_BUDGET) break
                    val (region, intact) = carve(rng, g, placed, cap) { searches++ }
                    if (floor == null) floor = LitsState(w, h, region, List(n) { false }, shading)
                    if (!intact) continue

                    val masks = LongArray(placed.size)
                    region.forEachIndexed { cell, r -> masks[r] = masks[r] or (1L shl cell) }
                    val options = Array(placed.size) { g.tetrominoesIn(masks[it]) }
                    if (options.any { it.isEmpty() }) continue
                    searches++
                    // Only an exactly-one verdict may ship. Truncated is explicitly not one.
                    val verdict = search(g, options)
                    if (verdict is Verdict.ExactlyOne) {
                        // Connected shadings were all the search above looked at, but the win
                        // check accepts disconnected ones too, so a board is only a puzzle when
                        // it has one answer under *those* rules. Checked last, so a board that
                        // already satisfied both keeps its layout; one that did not goes on to
                        // the next candidate.
                        searches++
                        if (search(g, options, connected = false) is Verdict.ExactlyOne) {
                            return LitsState(w, h, region, List(n) { false }, verdict.shading) to floor
                        }
                    }
                }
            }
        }

        return null to floor
    }

    /**
     * Only reachable if every placement attempt dead-ended, which has never been observed. It
     * exists so [generate] is total: a daily puzzle must never throw.
     */
    private fun lastResort(seed: Long, w: Int, h: Int): LitsState {
        val n = w * h
        val g = geometry(w, h)
        val placed = (0 until 64).firstNotNullOfOrNull {
            placeTetrominoes(Rng(seed + 7717L + it), g, 4, 4)
        } ?: intArrayOf(g.idOf(1L or (1L shl w) or (1L shl 2 * w) or (1L shl 3 * w)))
        val shading = placed.fold(0L) { acc, q -> acc or g.mask[q] }
        return LitsState(
            w, h,
            carve(Rng(seed), g, placed, Int.MAX_VALUE) {}.first,
            List(n) { false },
            List(n) { cell -> shading and (1L shl cell) != 0L },
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
     *
     * No region grows past [maxRegion] squares while the invariant holds; a square whose every
     * neighbouring region is full is set aside like one that would break uniqueness.
     *
     * Squares are sets of bits in a `Long` (see [Geometry]); every list the `Rng` picks from is
     * still in ascending square order, as it was when these were `List<Int>`s.
     */
    private fun carve(
        rng: Rng,
        g: Geometry,
        placed: IntArray,
        maxRegion: Int,
        onSearch: () -> Unit,
    ): kotlin.Pair<List<Int>, Boolean> {
        val n = g.n
        val region = IntArray(n) { -1 }
        val masks = LongArray(placed.size) { g.mask[placed[it]] }
        var claimed = 0L
        masks.forEachIndexed { index, m ->
            claimed = claimed or m
            forEachBit(m) { region[it] = index }
        }
        val sizes = IntArray(placed.size) { 4 }
        val opts = Array(placed.size) { g.tetrominoesIn(masks[it]) }

        var intact = true
        var remaining = n - claimed.countOneBits()
        // Squares no region can take without adding a second answer, as the regions (bits) that
        // refused them. They are set aside rather than fatal: claiming their other neighbours can
        // give them a region that will. No bits and "never refused" read the same, because a
        // frontier square always has a neighbouring region.
        val refused = LongArray(n)

        var guard = 0
        while (remaining > 0 && guard++ < n * 8) {
            val frontier = g.spread(claimed) and claimed.inv()
            if (frontier == 0L) break
            var open = frontier
            if (intact) forEachBit(frontier) { cell ->
                if (hostBits(g, cell, region) and refused[cell].inv() == 0L) {
                    open = open and (1L shl cell).inv()
                }
            }
            if (open == 0L) { intact = false; continue }

            val cell = nthBit(open, rng.nextInt(open.countOneBits()))
            val bit = 1L shl cell
            // Smallest region first, so no one region swallows the leftovers.
            val hosts = rng.shuffled(hostsOf(g, cell, region)).sortedBy { sizes[it] }
                .filter { !intact || sizes[it] < maxRegion }

            var chosen = -1
            if (intact) {
                for (host in hosts) {
                    val grown = g.tetrominoesIn(masks[host] or bit)
                    if (grown.isEmpty()) continue
                    val trial = opts.copyOf()
                    trial[host] = grown
                    onSearch()
                    if (search(g, trial) is Verdict.ExactlyOne) {
                        chosen = host
                        opts[host] = grown
                        break
                    }
                }
                if (chosen == -1) {
                    // Every neighbouring region, including any the cap skipped over.
                    refused[cell] = hostBits(g, cell, region)
                    continue
                }
            } else {
                // Once the invariant is broken the options are never searched again.
                chosen = hosts.first()
            }
            masks[chosen] = masks[chosen] or bit
            claimed = claimed or bit
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
                val hosts = hostsOf(g, cell, region)
                if (hosts.isNotEmpty()) region[cell] = rng.pick(hosts)
            }
        }
        return region.map { if (it == -1) 0 else it } to intact
    }

    // ---- building a solution first --------------------------------------------------------

    /**
     * Lays down [target] tetrominoes that already satisfy every LITS rule, as [Geometry] placement
     * ids.
     *
     * Building the answer before the regions is what makes this generator work at all: regions
     * drawn at random almost never admit a legal shading, let alone exactly one. Each new piece is
     * grown off the existing shading, so the union is connected by construction.
     */
    private fun placeTetrominoes(
        rng: Rng,
        g: Geometry,
        target: Int,
        floor: Int,
    ): IntArray? {
        var shaded = 0L
        val letter = LongArray(Piece.entries.size)
        val placed = IntArray(target)
        var count = 0
        val found = IntArray(g.mostHolding)
        val legal = IntArray(g.mostHolding)

        // [target] is an ambition, not a requirement: the denser the shading the more constrained
        // the board, but past about two thirds of the grid the no-2x2 rule makes a full house
        // impossible. Giving up after a run of dead ends rather than grinding out the whole guard
        // is what keeps a near-miss layout cheap, and near misses are the common case.
        var guard = 0
        var idle = 0
        while (count < target && guard++ < target * 300 && idle < 120) {
            // After the first piece, only grow from squares touching what is already shaded.
            val seedCells = if (count == 0) g.full else g.spread(shaded) and shaded.inv()
            if (seedCells == 0L) break
            val from = nthBit(seedCells, rng.nextInt(seedCells.countOneBits()))

            // Every tetromino through [from] on free squares, in the order the old walk first found
            // them — a placement on free squares is reached by exactly the paths it always was, so
            // dropping the rest keeps the order of what remains. The Rng picks from these in the
            // order a JVM HashSet holding all of them (the 2x2 blocks included) iterates in.
            var kept = 0
            var fitting = 0
            for (q in g.holding[from]) {
                if (g.mask[q] and shaded != 0L) continue
                found[kept++] = q
                if (fits(g, q, shaded, letter)) legal[fitting++] = q
            }
            if (fitting == 0) {
                idle++
                continue
            }
            idle = 0

            // The hash order only matters when there is a choice to make; the Rng is drawn from
            // either way.
            val index = rng.nextInt(fitting)
            val pick = if (fitting == 1) {
                legal[0]
            } else {
                jvmHashSetOrder(found.asList().subList(0, kept)) { g.hash[it] }
                    .filter { fits(g, it, shaded, letter) }[index]
            }
            shaded = shaded or g.mask[pick]
            val p = g.piece[pick]
            letter[p] = letter[p] or g.mask[pick]
            placed[count++] = pick
        }

        // Four is the hard floor FallbackTest pins: fewer regions than that and the board reads as
        // a puzzle that gave up.
        return if (count >= maxOf(4, floor)) placed.copyOf(count) else null
    }

    /**
     * Whether placement [q] could join [shaded]: a letter, not the 2x2 block, completing no 2x2
     * block with what is down and touching no piece of its own letter.
     */
    private fun fits(g: Geometry, q: Int, shaded: Long, letter: LongArray): Boolean {
        val p = g.piece[q]
        if (p < 0) return false
        if (g.rim[q] and letter[p] != 0L) return false
        return !g.makesSquare(q, shaded or g.mask[q])
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
    // Its order reaches the Rng (carve shuffles and picks from it): first-seen, in neighbour order
    // (up, down, left, right), never a hash order.
    private fun hostsOf(g: Geometry, cell: Int, region: IntArray): List<Int> {
        val out = ArrayList<Int>(4)
        for (next in g.adjacent[cell]) {
            val r = region[next]
            if (r != -1 && r !in out) out += r
        }
        return out
    }

    /** The regions next to [cell], as bits. */
    private fun hostBits(g: Geometry, cell: Int, region: IntArray): Long {
        var bits = 0L
        for (next in g.adjacent[cell]) {
            val r = region[next]
            if (r != -1) bits = bits or (1L shl r)
        }
        return bits
    }

    /** The [k]th lowest set bit of [bits], counting from zero. */
    private fun nthBit(bits: Long, k: Int): Int {
        var rest = bits
        repeat(k) { rest = rest and (rest - 1) }
        return rest.countTrailingZeroBits()
    }

    private inline fun forEachBit(bits: Long, action: (Int) -> Unit) {
        var rest = bits
        while (rest != 0L) {
            action(rest.countTrailingZeroBits())
            rest = rest and (rest - 1)
        }
    }

    /**
     * A w x h grid (at most 64 squares) worked out once for the generator: squares as bits of a
     * `Long`, and every four-square connected placement, the 2x2 block included, as an id with its
     * mask, letter, 2x2 blocks and rim.
     *
     * Two orders are recorded because the `Rng` sees them. [holding] is the order the old
     * `quadsContaining` walk first reached each placement through a square, and [byLowest] lists
     * the legal placements in the lexicographic order of their squares, which is the order the old
     * `tetrominoes` enumerated a region's four-square subsets in.
     */
    private class Geometry(val w: Int, val h: Int) {
        val n = w * h
        val full: Long = if (n == 64) -1L else (1L shl n) - 1
        private val notFirstColumn: Long
        private val notLastColumn: Long

        val adjacent: Array<IntArray> = Array(n) { neighbours(it, w, h).toIntArray() }

        val mask: LongArray
        /** [Piece] ordinal, or -1 for the 2x2 block. */
        val piece: IntArray
        /** `List<Int>.hashCode()` of the sorted squares, which is what the JVM HashSet hashed. */
        val hash: IntArray
        private val blocks: Array<LongArray>
        /** Squares edge-adjacent to the placement and not in it. */
        val rim: LongArray
        val holding: Array<IntArray>
        private val byLowest: Array<IntArray>
        val mostHolding: Int

        init {
            require(n <= 64) { "LITS boards are at most 64 squares" }
            var first = 0L
            var last = 0L
            for (cell in 0 until n) {
                if (cell % w != 0) first = first or (1L shl cell)
                if (cell % w != w - 1) last = last or (1L shl cell)
            }
            notFirstColumn = first
            notLastColumn = last

            // The old walk, run once per square on an empty board.
            val ids = HashMap<List<Int>, Int>()   // probe only: ids are labels, never an order
            val shapes = ArrayList<List<Int>>()
            holding = Array(n) { cell ->
                val order = ArrayList<Int>()
                val seen = HashSet<Int>()          // probe only
                fun grow(current: List<Int>) {
                    if (current.size == 4) {
                        val id = ids.getOrPut(current) { shapes += current; shapes.size - 1 }
                        if (seen.add(id)) order += id
                        return
                    }
                    for (c in current) {
                        for (next in neighbours(c, w, h)) {
                            if (next in current) continue
                            grow((current + next).sorted())
                        }
                    }
                }
                grow(listOf(cell))
                order.toIntArray()
            }
            mostHolding = holding.maxOf { it.size }

            val count = shapes.size
            mask = LongArray(count) { shapes[it].fold(0L) { m, c -> m or (1L shl c) } }
            piece = IntArray(count) { classify(shapes[it], w)?.ordinal ?: -1 }
            hash = IntArray(count) { shapes[it].fold(1) { acc, e -> 31 * acc + e } }
            blocks = Array(count) { q ->
                val out = ArrayList<Long>()
                for (cell in shapes[q]) {
                    val r = cell / w
                    val c = cell % w
                    for (rr in r - 1..r) for (cc in c - 1..c) {
                        if (rr < 0 || cc < 0 || rr + 1 >= h || cc + 1 >= w) continue
                        val block = (1L shl rr * w + cc) or (1L shl rr * w + cc + 1) or
                            (1L shl (rr + 1) * w + cc) or (1L shl (rr + 1) * w + cc + 1)
                        if (block !in out) out += block
                    }
                }
                out.toLongArray()
            }
            rim = LongArray(count) { q -> spread(mask[q]) and mask[q].inv() }

            val lexicographic = Comparator<Int> { a, b ->
                val x = shapes[a]
                val y = shapes[b]
                var d = 0
                for (i in 0 until 4) {
                    d = x[i].compareTo(y[i])
                    if (d != 0) break
                }
                d
            }
            byLowest = Array(n) { cell ->
                (0 until count).filter { piece[it] >= 0 && shapes[it][0] == cell }
                    .sortedWith(lexicographic).toIntArray()
            }
        }

        /** [bits] and every square edge-adjacent to one of them. */
        fun spread(bits: Long): Long =
            bits or ((bits shl 1) and notFirstColumn) or ((bits ushr 1) and notLastColumn) or
                ((bits shl w) and full) or (bits ushr w)

        /** Does [shaded] (which includes placement [q]) fill a 2x2 block touching [q]? */
        fun makesSquare(q: Int, shaded: Long): Boolean {
            for (block in blocks[q]) if (block and shaded.inv() == 0L) return true
            return false
        }

        /** Every legal tetromino inside [cells], in the old `tetrominoes` order. */
        fun tetrominoesIn(cells: Long): IntArray {
            var size = 0
            var buffer = IntArray(40)
            forEachBit(cells) { cell ->
                for (q in byLowest[cell]) {
                    if (mask[q] and cells.inv() != 0L) continue
                    if (size == buffer.size) buffer = buffer.copyOf(size * 2)
                    buffer[size++] = q
                }
            }
            return buffer.copyOf(size)
        }

        fun idOf(cells: Long): Int = mask.indices.first { mask[it] == cells }
    }

    private var geometries: List<Geometry> = emptyList()

    /**
     * Built once per board size. Racing threads may each build one; the loser's is dropped, and a
     * [Geometry] is immutable once constructed.
     */
    private fun geometry(w: Int, h: Int): Geometry =
        geometries.firstOrNull { it.w == w && it.h == h }
            ?: Geometry(w, h).also { geometries = geometries + it }

    internal fun neighbours(cell: Int, w: Int, h: Int): List<Int> {
        val r = cell / w
        val c = cell % w
        return buildList {
            if (r > 0) add(cell - w)
            if (r < h - 1) add(cell + w)
            if (c > 0) add(cell - 1)
            if (c < w - 1) add(cell + 1)
        }
    }

    /** Every four-square legal tetromino that fits inside [cells]. Internal for [LitsTeacher]. */
    internal fun tetrominoes(cells: List<Int>, w: Int, h: Int): List<kotlin.Pair<List<Int>, Piece>> {
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
    private fun search(g: Geometry, options: Array<IntArray>, connected: Boolean = true): Verdict {
        val s = Search(g, options, connected)
        s.place(0)

        // Order matters: two answers in hand is proof of ambiguity whether or not the budget also
        // ran out, but one answer plus a truncated search proves nothing at all.
        return when {
            s.count >= 2 -> Verdict.Ambiguous
            s.truncated -> Verdict.Truncated
            s.found -> Verdict.ExactlyOne(List(g.n) { s.first and (1L shl it) != 0L })
            else -> Verdict.None
        }
    }

    /**
     * [search]'s state, on bitmasks. It visits exactly the nodes the list-based search it replaced
     * did, in the same order — the same prunes, only cheaper — so [NODE_BUDGET] truncates exactly
     * the searches it always did.
     */
    private class Search(private val g: Geometry, options: Array<IntArray>, private val connected: Boolean) {
        // Fewest choices first. Region order does not change the answer, only how fast it is found.
        private val order = options.indices.sortedBy { options[it].size }
        private val choices = Array(options.size) { options[order[it]] }

        // undecided[d] marks the squares still owned by regions this search has not reached at
        // depth d — the only squares through which shading may still be joined up.
        private val undecided = LongArray(options.size + 1)
        private var shaded = 0L
        private val letter = LongArray(Piece.entries.size)
        var first = 0L
        var found = false
        var count = 0
        private var nodes = 0
        var truncated = false

        init {
            for (d in options.indices.reversed()) {
                var cells = undecided[d + 1]
                for (q in choices[d]) cells = cells or g.mask[q]
                undecided[d] = cells
            }
        }

        /** Can every shaded square still reach every other, allowing for squares not yet decided? */
        private fun stillJoinable(open: Long): Boolean {
            // Without the connectivity rule (the win check's own) nothing is ever cut off.
            if (!connected || shaded == 0L) return true
            val through = shaded or open
            var reached = shaded and -shaded
            while (true) {
                val next = g.spread(reached) and through
                if (next == reached) break
                reached = next
            }
            return shaded and reached.inv() == 0L
        }

        fun place(depth: Int) {
            if (count >= 2 || truncated) return
            if (nodes++ > NODE_BUDGET) {
                truncated = true
                return
            }
            if (depth == choices.size) {
                // undecided[size] is empty, so this is plain connectivity.
                if (stillJoinable(undecided[depth])) {
                    count++
                    if (!found) {
                        found = true
                        first = shaded
                    }
                }
                return
            }
            for (q in choices[depth]) {
                val m = g.mask[q]
                val p = g.piece[q]
                shaded = shaded or m
                if (!g.makesSquare(q, shaded) &&
                    g.rim[q] and letter[p] == 0L &&
                    stillJoinable(undecided[depth + 1])
                ) {
                    letter[p] = letter[p] or m
                    place(depth + 1)
                    letter[p] = letter[p] and m.inv()
                }
                shaded = shaded and m.inv()
                if (count >= 2 || truncated) return
            }
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

    // ---- teaching -----------------------------------------------------------------------------

    /**
     * A mistake to take back or a step to reason out — see [LitsTeacher]. Replaces the old [hint],
     * which toggled a square straight out of [LitsState.solution] and taught nothing.
     */
    override fun teach(state: PuzzleState): Deduction? {
        val s = state as LitsState
        val step = LitsTeacher.teach(s) ?: return null
        return Deduction(
            technique = step.technique,
            nudge = step.nudge,
            explanation = step.explanation,
            focus = step.focus,
            cited = step.cited,
            targets = step.targets,
            mistake = step.technique == LitsTeacher.MISTAKE,
            fallback = step.technique == LitsTeacher.FALLBACK,
            applyTo = { now -> applyStep(now as LitsState, step) },
            reachedBy = { now ->
                val l = now as LitsState
                step.shades.all { l.shaded[it] } && step.clears.all { !l.shaded[it] }
            },
        )
    }

    /** "Show me": the step, made on the board as it is now. One state, so one undo entry. */
    private fun applyStep(s: LitsState, step: LitsTeacher.Step): LitsState =
        s.paint(step.clears, false).paint(step.shades, true)

    // ---- the walkthrough ------------------------------------------------------------------------

    /**
     * The walkthrough's board, 5x5 so every square is big enough to aim at while learning the
     * gestures. Regions, with the answer's shading marked `#`:
     *
     * ```
     * 1 .  0 #  0 #  0 #  3 .
     * 1 .  1 .  1 .  0 #  3 #
     * 2 #  2 .  1 #  3 .  3 #
     * 2 #  2 #  1 #  3 .  3 #
     * 2 #  2 .  1 #  1 #  3 #
     * ```
     *
     * Chosen, from a few hundred boards carved the way the generator carves them, because the moves
     * the frames teach are the moves it actually needs, in that order: the top region is exactly
     * four squares (a drag); its L crosses off the middle region's top, leaving that region exactly
     * four squares (a second drag); that L then crosses off both squares that would make the left
     * region an L too, so the left region's bump is forced (a tap). LitsTeachingTest proves it has
     * exactly one answer under the win check's rules, and walks it with the hints.
     */
    internal val TUTORIAL_REGIONS = listOf(
        1, 0, 0, 0, 3,
        1, 1, 1, 0, 3,
        2, 2, 1, 3, 3,
        2, 2, 1, 3, 3,
        2, 2, 1, 1, 3,
    )
    internal val TUTORIAL_SOLUTION = setOf(1, 2, 3, 8, 9, 10, 12, 14, 15, 16, 17, 19, 20, 22, 23, 24)
    private const val TUTORIAL_SIDE = 5
    internal val TUTORIAL_TOP = setOf(1, 2, 3, 8)
    internal val TUTORIAL_MIDDLE = setOf(12, 17, 22, 23)
    internal const val TUTORIAL_BUMP = 16

    private fun tutorialBoard(shaded: Set<Int>) = LitsState(
        TUTORIAL_SIDE, TUTORIAL_SIDE, TUTORIAL_REGIONS,
        List(TUTORIAL_SIDE * TUTORIAL_SIDE) { it in shaded },
        List(TUTORIAL_SIDE * TUTORIAL_SIDE) { it in TUTORIAL_SOLUTION },
    )

    /**
     * Accepts exactly [shaded] and nothing else. Strict on purpose, as Kings' is: each frame's board
     * is written for the one before it, and one tap at a time is not a drag.
     */
    private fun exactly(shaded: Set<Int>): (PuzzleState) -> Boolean = { next ->
        next is LitsState && next.shaded.indices.all { next.shaded[it] == (it in shaded) }
    }

    override val tutorial: List<TutorialFrame> by lazy {
        val solved = tutorialBoard(TUTORIAL_SOLUTION)
        val top = TUTORIAL_TOP
        val middle = top + TUTORIAL_MIDDLE
        val bumped = middle + TUTORIAL_BUMP
        val leftT = setOf(10, 15, 16, 20)
        listOf(
            TutorialFrame(
                state = solved,
                caption = "Shade four squares in every region so they make an L, I, T or S. The " +
                    "thick lines are the walls. Here's a finished board; the glowing T is one region's.",
                highlight = BoardHighlight(strong = leftT, soft = setOf(11, 21)),
            ),
            TutorialFrame(
                state = solved,
                caption = "Shading never fills a 2x2 square. Three squares of this block are shaded, " +
                    "so the glowing one has to stay empty.",
                highlight = BoardHighlight(strong = setOf(7), soft = setOf(2, 3, 8)),
            ),
            TutorialFrame(
                state = solved,
                caption = "Two tetrominoes of the same letter may not touch along an edge, even across " +
                    "a wall. These two L's meet only at a corner, which is allowed.",
                highlight = BoardHighlight(strong = top + TUTORIAL_MIDDLE),
            ),
            TutorialFrame(
                state = tutorialBoard(emptySet()),
                caption = "The top region has exactly four squares, so its tetromino is the whole " +
                    "region. Drag through all four glowing squares to shade them.",
                highlight = BoardHighlight(strong = top),
                accepts = exactly(top),
                retry = "Drag through all four glowing squares in one go.",
                done = "Shaded. A finished shape wears its letter.",
            ),
            TutorialFrame(
                state = tutorialBoard(top + 7),
                caption = "Suppose you'd shaded the glowing square too: it fills a 2x2 with the L. " +
                    "Tap a shaded square to clear it.",
                highlight = BoardHighlight(strong = setOf(7), soft = setOf(2, 3, 8)),
                accepts = exactly(top),
                retry = "Tap the glowing square once.",
                done = "Cleared. A drag that starts on a shaded square clears too.",
            ),
            TutorialFrame(
                state = tutorialBoard(top),
                caption = "Squares that can't be shaded any more are crossed off for you. The middle " +
                    "region has exactly four squares left, so drag through them.",
                highlight = BoardHighlight(strong = TUTORIAL_MIDDLE, soft = setOf(0, 5, 6, 7)),
                accepts = exactly(middle),
                retry = "Drag through the four glowing squares in one go.",
                done = "Another L, touching the first only at a corner.",
            ),
            TutorialFrame(
                state = tutorialBoard(middle),
                caption = "On the left, the crossed squares would make an L touching the middle L, or " +
                    "fill a 2x2 with it. What's left is a T. Tap its bump to shade it.",
                highlight = BoardHighlight(strong = setOf(TUTORIAL_BUMP), soft = setOf(10, 11, 15, 20, 21)),
                accepts = exactly(bumped),
                retry = "Tap the glowing square once.",
                done = "That's the T's bump.",
            ),
            TutorialFrame(
                state = tutorialBoard(bumped),
                caption = "Your turn: finish the board. Stuck? Hint shows you why.",
                freePlay = true,
                done = "Solved. That's all there is to it.",
            ),
        )
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

        // A drag in progress: what it sets squares to (decided by its first square) and the squares
        // it has crossed. Held here rather than in LitsState because the play screen pushes an undo
        // entry for every state it is handed; the drag emits one state, when the finger lifts.
        var painting by remember(s) { mutableStateOf<Boolean?>(null) }
        var swept by remember(s) { mutableStateOf(emptySet<Int>()) }
        val shown = painting?.let { s.paint(swept, it) } ?: s

        // Both are a function of the shading and nothing else, so they are recomputed when — and
        // only when — the shading on show changes, the way Mambo remembers its violations. During
        // a drag that is the preview, so letters and crosses follow the finger.
        val letters = remember(shown.shaded) { shown.letters() }
        val impossible = remember(shown.shaded) { shown.impossible() }

        // What a hint or the walkthrough is pointing at. Everything it does not name is dimmed, so
        // the named squares can be found at a glance; [BoardHighlight.strong] squares get a
        // breathing outline and [BoardHighlight.soft] ones a quiet one — the same language as Kings.
        val highlight = LocalBoardHighlight.current
        val glow = if (highlight.warning) scheme.error else scheme.onBackground
        val dim = scheme.background
        // Read only inside the draw lambda, so the pulse repaints the outlines without recomposing.
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

        // Sized from both axes (CLAUDE.md), so a tall board or a short screen never runs it under
        // the hint slot.
        BoxWithConstraints(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
            val step = if (constraints.hasBoundedHeight) minOf(maxWidth / s.width, maxHeight / s.height) else maxWidth / s.width
            val stepPx = with(LocalDensity.current) { step.toPx() }

            fun cellAt(offset: Offset): Int {
                val c = (offset.x / stepPx).toInt().coerceIn(0, s.width - 1)
                val r = (offset.y / stepPx).toInt().coerceIn(0, s.height - 1)
                return r * s.width + c
            }

            Canvas(
                Modifier
                    .width(step * s.width)
                    .height(step * s.height)
                    .pointerInput(s, interactive) {
                        if (!interactive) return@pointerInput
                        detectTapGestures { offset: Offset -> onState(s.toggle(cellAt(offset))) }
                    }
                    .pointerInput(s, interactive) {
                        if (!interactive) return@pointerInput
                        detectDragGestures(
                            onDragStart = { offset ->
                                val start = cellAt(offset)
                                painting = !s.shaded[start]
                                swept = setOf(start)
                            },
                            onDrag = { change, _ -> swept = swept + cellAt(change.position) },
                            onDragEnd = {
                                val next = painting?.let { s.paint(swept, it) } ?: s
                                painting = null
                                swept = emptySet()
                                // A drag that changed nothing is not a move, and the screen would
                                // push an undo entry for it all the same.
                                if (next !== s) onState(next)
                            },
                            onDragCancel = {
                                painting = null
                                swept = emptySet()
                            },
                        )
                    }
            ) {
                for (i in 0 until s.width * s.height) {
                    val r = i / s.width
                    val c = i % s.width
                    val at = Offset(c * stepPx, r * stepPx)
                    val piece = letters[i]
                    drawRect(
                        color = when {
                            !shown.shaded[i] -> scheme.surfaceVariant
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

                // Dimming goes over the marks but under the walls, so the regions stay readable
                // however much of the board has stepped back.
                if (!highlight.isEmpty) {
                    for (i in 0 until s.width * s.height) {
                        if (i in highlight.strong || i in highlight.soft) continue
                        drawRect(
                            color = dim.copy(alpha = 0.62f),
                            topLeft = Offset((i % s.width) * stepPx, (i / s.width) * stepPx),
                            size = Size(stepPx, stepPx),
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

                // Outlines last, on top of the walls: a glow that a wall half-covers is a glow the
                // player has to hunt for.
                val strongWidth = 4.dp.toPx()
                val softWidth = 1.5.dp.toPx()
                val radius = CornerRadius(4.dp.toPx())
                for (i in 0 until s.width * s.height) {
                    val strong = i in highlight.strong
                    if (!strong && i !in highlight.soft) continue
                    val stroke = if (strong) strongWidth else softWidth
                    val inset = edge / 2f + stroke / 2f
                    drawRoundRect(
                        color = if (strong) glow.copy(alpha = pulse.value) else glow.copy(alpha = 0.5f),
                        topLeft = Offset((i % s.width) * stepPx + inset, (i / s.width) * stepPx + inset),
                        size = Size(stepPx - inset * 2f, stepPx - inset * 2f),
                        cornerRadius = radius,
                        style = Stroke(stroke),
                    )
                }
            }
        }
    }
}
