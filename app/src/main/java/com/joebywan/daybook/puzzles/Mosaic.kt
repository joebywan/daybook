package com.joebywan.daybook.puzzles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.Rng
import kotlinx.serialization.Serializable

/** One fill: the cell tapped, and the colour poured into the area it belongs to. */
data class MosaicMove(val cell: Int, val colour: Int)

@Serializable
data class MosaicState(
    val width: Int,
    val height: Int,
    val colours: Int,
    val limit: Int,
    val cells: List<Int>,
    override val moves: Int = 0,
) : PuzzleState {

    override val solved: Boolean get() = cells.all { it == cells[0] }

    /**
     * Losable, like Tower. Nothing on the board is ever *wrong* here — an area is whatever colour
     * you last poured into it — so the only way to lose is to run the fills out, and the board has
     * to say so itself rather than sit there accepting taps that can no longer win.
     */
    override val failed: Boolean get() = !solved && moves >= limit

    /** Every cell of the single-colour area [index] belongs to, joined edge to edge. */
    fun area(index: Int): IntArray {
        val hue = cells[index]
        val seen = BooleanArray(cells.size)
        // The result doubles as the queue: cells are appended once and read back in order.
        val out = IntArray(cells.size)
        var found = 0
        seen[index] = true
        out[found++] = index

        fun visit(cell: Int) {
            if (seen[cell] || cells[cell] != hue) return
            seen[cell] = true
            out[found++] = cell
        }

        var head = 0
        while (head < found) {
            val cell = out[head++]
            val r = cell / width
            val c = cell % width
            if (r > 0) visit(cell - width)
            if (r < height - 1) visit(cell + width)
            if (c > 0) visit(cell - 1)
            if (c < width - 1) visit(cell + 1)
        }
        return out.copyOf(found)
    }

    /** How many single-colour areas the board is currently made of. One means solved. */
    fun areaCount(): Int {
        val seen = BooleanArray(cells.size)
        var count = 0
        for (i in cells.indices) {
            if (seen[i]) continue
            count++
            for (cell in area(i)) seen[cell] = true
        }
        return count
    }

    /**
     * Pours [colour] into the area holding [index]. Neighbouring areas already in that colour are
     * swallowed for free, because areas are read back off the cell colours rather than stored —
     * which is the whole strategy of the puzzle and costs nothing to implement.
     *
     * Re-pouring an area's own colour returns the same state, so the board can hand a pointless tap
     * straight back instead of spending a fill and an undo entry on it.
     */
    fun flood(index: Int, colour: Int): MosaicState {
        if (cells[index] == colour) return this
        val next = cells.toMutableList()
        for (cell in area(index)) next[cell] = colour
        return copy(cells = next, moves = moves + 1)
    }
}

/**
 * Mosaic — the region flood-fill.
 *
 * Pick a colour, tap an area, and that area takes the colour — merging with every neighbouring area
 * already wearing it. Bigger blobs swallow more on the next fill, so the game is about choosing the
 * order that compounds fastest. Unlike Flood-It there is no fixed corner: any area may be tapped,
 * which is a much larger move space and the reason the move limit has to be computed rather than
 * guessed.
 */
object Mosaic : PuzzleType {

    override val id = "mosaic"
    override val displayName = "Mosaic"
    override val tagline = "Flood the board down to one colour"
    override val accent = 0xFFD97FB0
    override val rules = listOf(
        "Cells of one colour joined edge to edge form a single area, drawn with a heavy outline.",
        "Pick a colour below, then tap an area to flood it with that colour.",
        "A flooded area merges with every neighbouring area already in that colour — " +
            "growing one big blob is how you cover ground quickly.",
        "Turn the whole board one colour before the fills run out.",
        "Tapping an area in its own colour does nothing and costs nothing.",
    )

    /** Board colours. Three of them read as the screenshot's green/red/blue; Expert adds the amber. */
    val palette = listOf(0xFF54B07A, 0xFFD9584C, 0xFF4C86D9, 0xFFE0B23C, 0xFF9B6FD0)

    /**
     * Search nodes before the solver gives up. Generation rejects a board it cannot prove inside
     * this; a hint simply goes quiet. One number for both on purpose — a smaller hint budget would
     * mean the hardest Expert boards, the ones most worth a hint, are exactly the ones that never
     * offer one.
     */
    const val SOLVE_BUDGET = 400_000

    /**
     * Board shape per tier.
     *
     * @param blobs how many seeds the region growth starts from — the region count lands a little
     *   under this, because two neighbouring blobs that end up the same colour are one area.
     * @param optimum the band the *proven* optimal solution has to fall in for a board to ship.
     * @param slack fills handed to the player over that optimum.
     *
     * | tier     | grid  | blobs | colours | areas | optimum | slack | limit |
     * |----------|-------|------:|--------:|------:|--------:|------:|------:|
     * | STANDARD | 8x12  |    16 |       3 | 12-15 |       4 |     3 |     7 |
     * | HARD     | 9x14  |    20 |       4 | 17-20 |       5 |     2 |     7 |
     * | EXPERT   | 10x16 |    24 |       5 | 21-24 |       7 |     1 |     8 |
     *
     * The areas and optimum columns are measured, not hoped for — forty daily seeds per tier land
     * in those ranges, and the optimum column is a single number because the band is a single
     * number: a board whose proven optimum misses it is thrown back. That costs a couple of extra
     * generations and buys a tier that plays the same on a Tuesday as on a Friday, which random
     * blob growth does not otherwise give.
     *
     * Three things make a tier harder, and only one of them is the limit. Palette size sets the
     * floor on the optimum — every colour but the last costs a fill to retire — and it also widens
     * every choice. Area count sets how far apart the ends of the board are in fills. Slack decides
     * how much of the optimal line you are allowed to miss, and it is the sharpest of the three:
     * Standard's three spare fills forgive a whole strategy, Expert's one forgives a single pour.
     *
     * Standard and Hard share a limit of seven on purpose. Hard is not harder because it allows
     * fewer fills; it is harder because it is a bigger board, a fourth colour and one fewer chance
     * to waste a fill.
     */
    private class Spec(
        val width: Int,
        val height: Int,
        val blobs: Int,
        val colours: Int,
        val optimum: IntRange,
        val slack: Int,
    )

    private fun specFor(difficulty: Difficulty) = when (difficulty) {
        Difficulty.STANDARD -> Spec(8, 12, 16, 3, 4..4, 3)
        Difficulty.HARD -> Spec(9, 14, 20, 4, 5..5, 2)
        Difficulty.EXPERT -> Spec(10, 16, 24, 5, 7..7, 1)
    }

    // ---- generation ---------------------------------------------------------------------------

    /**
     * Paints blobs, then proves the board before shipping it.
     *
     * The proof is the point. A move limit that cannot be met is a daily puzzle nobody can finish,
     * and every device gets the same one, so the limit is never an estimate: [solve] returns a
     * shortest line or nothing at all, and a board whose length could not be proved inside
     * [SOLVE_BUDGET] is thrown away rather than shipped with a guessed number on it.
     */
    override fun generate(seed: Long, difficulty: Difficulty): PuzzleState {
        val spec = specFor(difficulty)

        // Three passes, each giving up something rather than giving up the guarantee. The first
        // insists on the tier's optimum. The second takes any optimum that was proved. The third
        // stops searching and falls back on a limit that needs no search: flooding an area with a
        // neighbour's colour always eats at least that neighbour, so a board of n areas always
        // falls in n - 1 fills. Neither fallback has been needed by any tested seed, and the loose
        // limit is still one that has been argued, which an unfinished search is not.
        for (pass in 0..2) {
            repeat(ATTEMPTS) { attempt ->
                val rng = Rng(seed + attempt * 0x9E3779B9L + pass * 0x7F4A7C15L)
                val board = MosaicState(spec.width, spec.height, spec.colours, 0, paint(rng, spec))
                if (board.solved) return@repeat
                if (pass == 2) return board.copy(limit = board.areaCount() - 1)
                val line = solve(board) ?: return@repeat
                if (pass == 0 && line.size !in spec.optimum) return@repeat
                return board.copy(limit = line.size + spec.slack)
            }
        }

        // Only reachable if blob growth painted one flat colour forty times running. Stripes cannot
        // do that, and one fill per stripe always clears them.
        val stripes = MosaicState(
            spec.width, spec.height, spec.colours, 0,
            List(spec.width * spec.height) { (it / spec.width) % spec.colours },
        )
        return stripes.copy(limit = stripes.areaCount() - 1)
    }

    private const val ATTEMPTS = 40

    /**
     * Grows one blob per seed until the grid is covered, then colours the blobs so neighbours
     * differ where it can.
     *
     * Per-cell random colour would be the obvious thing and is exactly wrong: it makes a hundred
     * one-cell areas, which is noise to look at and a search too wide to prove anything about.
     * Growing blobs first gives the chunky outlined shapes the puzzle is played on. Where the
     * greedy colouring is forced into a clash the two blobs simply become one area, which varies
     * the region sizes for free — the areas are read off the cell colours, so nothing downstream
     * knows or cares that a blob boundary was there.
     */
    private fun paint(rng: Rng, spec: Spec): List<Int> {
        val w = spec.width
        val h = spec.height
        val n = w * h
        val owner = IntArray(n) { -1 }

        // Farthest-point sampling rather than plain shuffling: seeds drawn uniformly clump, and a
        // clump of seeds grows one huge blob and a fistful of slivers.
        val seeds = IntArray(spec.blobs)
        seeds[0] = rng.nextInt(n)
        owner[seeds[0]] = 0
        for (b in 1 until spec.blobs) {
            var best = -1
            var bestGap = -1
            repeat(CANDIDATES) {
                val cell = rng.nextInt(n)
                if (owner[cell] != -1) return@repeat
                var gap = Int.MAX_VALUE
                for (k in 0 until b) {
                    val dr = cell / w - seeds[k] / w
                    val dc = cell % w - seeds[k] % w
                    gap = minOf(gap, dr * dr + dc * dc)
                }
                if (gap > bestGap) {
                    bestGap = gap
                    best = cell
                }
            }
            if (best == -1) best = (0 until n).first { owner[it] == -1 }
            seeds[b] = best
            owner[best] = b
        }

        val frontier = ArrayList<Int>(n)
        fun offer(cell: Int) {
            val r = cell / w
            val c = cell % w
            if (r > 0 && owner[cell - w] == -1) frontier += cell - w
            if (r < h - 1 && owner[cell + w] == -1) frontier += cell + w
            if (c > 0 && owner[cell - 1] == -1) frontier += cell - 1
            if (c < w - 1 && owner[cell + 1] == -1) frontier += cell + 1
        }
        seeds.forEach(::offer)

        while (frontier.isNotEmpty()) {
            val cell = frontier.removeAt(rng.nextInt(frontier.size))
            if (owner[cell] != -1) continue
            val r = cell / w
            val c = cell % w
            val claimants = ArrayList<Int>(4)
            if (r > 0 && owner[cell - w] != -1) claimants += owner[cell - w]
            if (r < h - 1 && owner[cell + w] != -1) claimants += owner[cell + w]
            if (c > 0 && owner[cell - 1] != -1) claimants += owner[cell - 1]
            if (c < w - 1 && owner[cell + 1] != -1) claimants += owner[cell + 1]
            if (claimants.isEmpty()) {
                frontier += cell
                continue
            }
            owner[cell] = rng.pick(claimants)
            offer(cell)
        }

        // Blob adjacency, then a greedy colouring in random order that avoids a neighbour's colour
        // where one is free and otherwise takes the least-used clash.
        val touches = Array(spec.blobs) { mutableSetOf<Int>() }
        for (cell in 0 until n) {
            val c = cell % w
            if (c < w - 1 && owner[cell] != owner[cell + 1]) {
                touches[owner[cell]] += owner[cell + 1]
                touches[owner[cell + 1]] += owner[cell]
            }
            if (cell / w < h - 1 && owner[cell] != owner[cell + w]) {
                touches[owner[cell]] += owner[cell + w]
                touches[owner[cell + w]] += owner[cell]
            }
        }

        val hue = IntArray(spec.blobs) { -1 }
        val used = IntArray(spec.colours)
        for (blob in rng.shuffled((0 until spec.blobs).toList())) {
            val clashes = IntArray(spec.colours)
            for (other in touches[blob]) if (hue[other] >= 0) clashes[hue[other]]++
            var pick = 0
            var bestScore = Int.MAX_VALUE
            for (colour in rng.shuffled((0 until spec.colours).toList())) {
                // Clashes dominate: a colour no neighbour holds always wins, and only among equally
                // clashing colours does the spread across the palette break the tie.
                val score = clashes[colour] * 1000 + used[colour]
                if (score < bestScore) {
                    bestScore = score
                    pick = colour
                }
            }
            hue[blob] = pick
            used[pick]++
        }

        return List(n) { hue[owner[it]] }
    }

    private const val CANDIDATES = 14

    // ---- solver -------------------------------------------------------------------------------

    /**
     * A search position: the areas of the board, as bitmasks over the areas the *board being
     * searched* started with.
     *
     * Bitmasks because merging is then an OR, and because the bits keep meaning the same thing all
     * the way down the search, so a group's neighbour mask survives its neighbours merging without
     * being rebuilt. Sixty-four areas is the ceiling that buys; the generated boards run to two
     * dozen and flooding never splits an area, so a board in play can only ever have fewer.
     */
    private class Position(val mask: LongArray, val border: LongArray, val hue: IntArray) {
        val size get() = mask.size
    }

    /** Labels the board's areas and lifts them into a [Position], or null past the 64-area ceiling. */
    private fun positionOf(state: MosaicState): Pair<Position, IntArray>? {
        val w = state.width
        val n = state.cells.size
        val label = IntArray(n) { -1 }
        val firstCell = ArrayList<Int>()
        for (i in 0 until n) {
            if (label[i] >= 0) continue
            val id = firstCell.size
            if (id >= 64) return null
            firstCell += i
            for (cell in state.area(i)) label[cell] = id
        }
        val count = firstCell.size
        val border = LongArray(count)
        for (cell in 0 until n) {
            val c = cell % w
            if (c < w - 1 && label[cell] != label[cell + 1]) {
                border[label[cell]] = border[label[cell]] or (1L shl label[cell + 1])
                border[label[cell + 1]] = border[label[cell + 1]] or (1L shl label[cell])
            }
            if (cell / w < state.height - 1 && label[cell] != label[cell + w]) {
                border[label[cell]] = border[label[cell]] or (1L shl label[cell + w])
                border[label[cell + w]] = border[label[cell + w]] or (1L shl label[cell])
            }
        }
        val position = Position(
            LongArray(count) { 1L shl it },
            border,
            IntArray(count) { state.cells[firstCell[it]] },
        )
        return position to firstCell.toIntArray()
    }

    /** Which groups touch which, as a bitmask per group over group indices. */
    private fun adjacency(pos: Position): LongArray {
        val n = pos.size
        val adj = LongArray(n)
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                if (pos.border[i] and pos.mask[j] != 0L) {
                    adj[i] = adj[i] or (1L shl j)
                    adj[j] = adj[j] or (1L shl i)
                }
            }
        }
        return adj
    }

    /**
     * A lower bound on the fills still needed. Admissible, so IDA* built on it returns a genuine
     * optimum rather than a good-looking line.
     *
     * Two bounds, whichever is larger:
     *
     * - **Colours.** A fill recolours one group, so it can retire at most one colour from the
     *   board. One colour must remain, hence `distinct - 1`.
     * - **Distance.** A fill contracts a group and its same-coloured neighbours into one node. Any
     *   shortest path crosses that blob at most once, spending at most two edges inside it and two
     *   getting in and out, and those four collapse to two — so no distance shrinks by more than
     *   two per fill. The board ends as one node, distance zero, so `ceil(spread / 2)` fills are
     *   needed for a spread of `spread`.
     *
     * The spread is measured by double sweep (BFS from anywhere, then BFS from the farthest node
     * found) rather than all-pairs. That is a lower bound on the true diameter, which keeps the
     * bound admissible, and it costs two BFS per node instead of one per group.
     */
    private fun bound(pos: Position, adj: LongArray): Int {
        if (pos.size <= 1) return 0
        var present = 0L
        for (colour in pos.hue) present = present or (1L shl colour)
        val byColour = java.lang.Long.bitCount(present) - 1
        val far = sweep(pos.size, adj, 0).first
        val spread = sweep(pos.size, adj, far).second
        return maxOf(byColour, (spread + 1) / 2)
    }

    /**
     * Order-independent hash of a position, for the transposition table.
     *
     * Summing per-group hashes rather than hashing the arrays in order sidesteps having to keep the
     * groups in a canonical order after a merge. A collision could only ever make the search miss a
     * line and report a *longer* optimum, which is a looser move limit rather than an unreachable
     * one — but at 64 bits it is not something that happens.
     */
    private fun fingerprint(pos: Position): Long {
        var sum = 0L
        for (j in 0 until pos.size) {
            var z = pos.mask[j] xor (pos.hue[j].toLong() * 0x517CC1B727220A95L)
            z = (z xor (z ushr 33)) * -0x7ee3623a03d3c83fL
            z = (z xor (z ushr 29)) * -0x3b314601e57a13adL
            sum += z xor (z ushr 32)
        }
        return sum
    }

    /** BFS from [from]; returns the farthest node and its distance. */
    private fun sweep(n: Int, adj: LongArray, from: Int): Pair<Int, Int> {
        val dist = IntArray(n) { -1 }
        val queue = IntArray(n)
        var head = 0
        var tail = 0
        dist[from] = 0
        queue[tail++] = from
        var far = from
        var best = 0
        while (head < tail) {
            val u = queue[head++]
            var bits = adj[u]
            while (bits != 0L) {
                val v = java.lang.Long.numberOfTrailingZeros(bits)
                bits = bits and (bits - 1)
                if (dist[v] >= 0) continue
                dist[v] = dist[u] + 1
                if (dist[v] > best) {
                    best = dist[v]
                    far = v
                }
                queue[tail++] = v
            }
        }
        return far to best
    }

    private fun play(pos: Position, group: Int, colour: Int, adj: LongArray): Position {
        var mask = pos.mask[group]
        var border = pos.border[group]
        var eaten = 0L
        var bits = adj[group]
        while (bits != 0L) {
            val j = java.lang.Long.numberOfTrailingZeros(bits)
            bits = bits and (bits - 1)
            if (pos.hue[j] != colour) continue
            mask = mask or pos.mask[j]
            border = border or pos.border[j]
            eaten = eaten or (1L shl j)
        }
        border = border and mask.inv()

        val out = pos.size - java.lang.Long.bitCount(eaten)
        val nm = LongArray(out)
        val nb = LongArray(out)
        val nh = IntArray(out)
        var k = 0
        for (j in 0 until pos.size) {
            if (eaten and (1L shl j) != 0L) continue
            if (j == group) {
                nm[k] = mask
                nb[k] = border
                nh[k] = colour
            } else {
                nm[k] = pos.mask[j]
                nb[k] = pos.border[j]
                nh[k] = pos.hue[j]
            }
            k++
        }
        return Position(nm, nb, nh)
    }

    /**
     * Candidate fills, best-looking first, encoded as `group shl 4 or colour`.
     *
     * Only colours a neighbour already wears are offered. Pouring a colour nothing next door holds
     * merges nothing — it only repaints one area — and dropping those cuts the branching factor by
     * roughly the palette size at every level, which is the difference between proving an Expert
     * board and not.
     *
     * That restriction is worth being precise about, because the shipped move limit rests on it. It
     * is not proved here that no shortest line ever needs a non-merging fill. What *is* guaranteed
     * is the direction of the error: searching a subset of the fills can only ever report a line
     * that is too long, never one that is too short, so the worst a hidden shortcut could do is make
     * a tier play a touch easier than intended. The line [solve] hands back is a real sequence of
     * real fills either way, and the limit built from it is reachable by playing exactly that line.
     *
     * Ordering by how many groups the fill eats finds a solution early, which is what makes the
     * final IDA* pass cheap.
     */
    private fun candidates(pos: Position, adj: LongArray): IntArray {
        val n = pos.size
        val gain = IntArray(n * palette.size)
        for (i in 0 until n) {
            var bits = adj[i]
            while (bits != 0L) {
                val j = java.lang.Long.numberOfTrailingZeros(bits)
                bits = bits and (bits - 1)
                gain[i * palette.size + pos.hue[j]]++
            }
        }
        var found = 0
        for (g in gain) if (g > 0) found++
        val moves = IntArray(found)
        val score = IntArray(found)
        var k = 0
        for (i in gain.indices) {
            if (gain[i] == 0) continue
            moves[k] = ((i / palette.size) shl 4) or (i % palette.size)
            score[k] = gain[i]
            k++
        }
        // Insertion sort: a handful of entries, and it keeps the order reproducible.
        for (a in 1 until found) {
            val m = moves[a]
            val s = score[a]
            var b = a - 1
            while (b >= 0 && score[b] < s) {
                moves[b + 1] = moves[b]
                score[b + 1] = score[b]
                b--
            }
            moves[b + 1] = m
            score[b + 1] = s
        }
        return moves
    }

    /**
     * The shortest sequence of fills that wins, or null when [budget] search nodes ran out first.
     *
     * Null is not "no solution" — every board is solvable — it means the length was not *proved*,
     * and the caller has to treat it as such. [generate] throws the board away; [hint] shows
     * nothing. Returning the best line found so far would be the same mistake as reporting a
     * truncated search as a proof, and it is the reason the budget is spent across all IDA*
     * iterations rather than reset for each one.
     *
     * Shortest over the fills [candidates] offers, which is every fill that merges something. See
     * there for why that is the right set and which way the remaining doubt points.
     */
    fun solve(state: MosaicState, budget: Int = SOLVE_BUDGET): List<MosaicMove>? {
        val (root, firstCell) = positionOf(state) ?: return null
        if (root.size <= 1) return emptyList()
        val path = IntArray(root.size)
        var spent = 0
        var reached = 0

        // Fills commute far more often than they look like they should — two pours in either order
        // land on the same board — so the same position turns up over and over under different move
        // orders. Remembering the deepest search that failed on a position is what takes the proof
        // of Expert boards from minutes to milliseconds. Entries stay valid as the ceiling rises,
        // because "no line of r fills from here" does not stop being true.
        val failedAt = HashMap<Long, Int>()

        // Depth-first under an f = depth + bound ceiling, raising the ceiling a fill at a time. The
        // first line found at a ceiling is optimal because the bound never overestimates.
        fun descend(pos: Position, depth: Int, ceiling: Int): Boolean {
            if (pos.size <= 1) {
                reached = depth
                return true
            }
            if (spent++ > budget) return false
            val adj = adjacency(pos)
            val remaining = ceiling - depth
            if (bound(pos, adj) > remaining) return false
            val key = fingerprint(pos)
            if ((failedAt[key] ?: -1) >= remaining) return false
            for (move in candidates(pos, adj)) {
                val group = move ushr 4
                val colour = move and 15
                path[depth] = (java.lang.Long.numberOfTrailingZeros(pos.mask[group]) shl 4) or colour
                if (descend(play(pos, group, colour, adj), depth + 1, ceiling)) return true
                if (spent > budget) return false
            }
            failedAt[key] = remaining
            return false
        }

        var ceiling = bound(root, adjacency(root))
        // Flooding any area with a neighbour's colour eats at least that neighbour, so this many
        // fills always suffice and the ceiling can never run away.
        val worst = root.size - 1
        while (ceiling <= worst) {
            if (descend(root, 0, ceiling)) {
                // Read back only as far as the win actually went. It always reaches the ceiling —
                // a shorter line would have been found at a lower one — but the path array still
                // holds stale entries past it, and trusting that invariant silently is how a win
                // grows a phantom extra fill.
                return (0 until reached).map {
                    MosaicMove(firstCell[path[it] ushr 4], path[it] and 15)
                }
            }
            if (spent > budget) return null
            ceiling++
        }
        return null
    }

    // ---- play ---------------------------------------------------------------------------------

    /**
     * Re-solves the board in front of the player rather than replaying a line stored at generation.
     * Three fills in, the stored line is advice about a board that no longer exists; this one is
     * always about the board on screen. It stays silent when the search runs out of budget, because
     * a hint that is merely a plausible fill would spend one of a very small number of moves.
     */
    override fun hint(state: PuzzleState): PuzzleState? {
        val s = state as MosaicState
        if (s.solved || s.failed) return null
        val move = solve(s)?.firstOrNull() ?: return null
        return s.flood(move.cell, move.colour)
    }

    @Composable
    override fun Board(state: PuzzleState, onState: (PuzzleState) -> Unit, interactive: Boolean) {
        val s = state as MosaicState
        val scheme = MaterialTheme.colorScheme

        // Kept out of MosaicState deliberately: the play screen pushes an undo entry for every
        // state handed to it, so a selection stored on the board would make "change colour" a step
        // to walk back. Saveable rather than plain remembered so rotating the phone does not also
        // reset the swatch.
        var selected by rememberSaveable(s.colours) { mutableIntStateOf(0) }
        val live = interactive && !s.failed

        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "${s.moves} of ${s.limit} fills used",
                style = MaterialTheme.typography.labelLarge,
                color = if (s.failed) scheme.error else scheme.onSurfaceVariant,
            )

            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Sized from both constraints: the board is half again as tall as it is wide, so
                // the width alone would run it off the bottom of the screen.
                val step = minOf(maxWidth / s.width, maxHeight / s.height)
                val stepPx = with(LocalDensity.current) { step.toPx() }

                Canvas(
                    Modifier
                        .width(step * s.width)
                        .height(step * s.height)
                        .pointerInput(s, live, selected) {
                            if (!live) return@pointerInput
                            detectTapGestures { offset: Offset ->
                                val c = (offset.x / stepPx).toInt().coerceIn(0, s.width - 1)
                                val r = (offset.y / stepPx).toInt().coerceIn(0, s.height - 1)
                                val cell = r * s.width + c
                                // Silence rather than a wasted fill when the area is already this
                                // colour: no state out means no move spent and no undo entry.
                                if (s.cells[cell] != selected) onState(s.flood(cell, selected))
                            }
                        }
                ) {
                    for (cell in s.cells.indices) {
                        val r = cell / s.width
                        val c = cell % s.width
                        drawRect(
                            color = Color(palette[s.cells[cell]]),
                            topLeft = Offset(c * stepPx, r * stepPx),
                            size = Size(stepPx, stepPx),
                        )
                    }

                    // Heavy strokes only where the colour changes, so each area reads as one
                    // outlined shape rather than a grid of tiles.
                    val edge = stepPx * 0.1f
                    for (cell in s.cells.indices) {
                        val r = cell / s.width
                        val c = cell % s.width
                        if (c + 1 < s.width && s.cells[cell] != s.cells[cell + 1]) {
                            drawLine(
                                scheme.onBackground,
                                Offset((c + 1) * stepPx, r * stepPx),
                                Offset((c + 1) * stepPx, (r + 1) * stepPx),
                                strokeWidth = edge,
                            )
                        }
                        if (r + 1 < s.height && s.cells[cell] != s.cells[cell + s.width]) {
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

            Row(
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            ) {
                repeat(s.colours) { colour ->
                    val chosen = selected == colour
                    // The ring is drawn outside the swatch with a gap rather than as a thick
                    // border on it: a border eats into the colour, and which swatch is selected
                    // has to be readable at a glance while the colour itself stays true.
                    Box(
                        Modifier
                            .size(52.dp)
                            .clickable(enabled = live) { selected = colour }
                            .border(
                                if (chosen) 3.dp else 0.dp,
                                if (chosen) scheme.onBackground else Color.Transparent,
                                RoundedCornerShape(16.dp),
                            )
                            .padding(6.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(Color(palette[colour]))
                            .border(1.dp, scheme.outline, RoundedCornerShape(11.dp))
                    )
                }
            }

            if (s.failed) {
                Text(
                    "Out of fills — restart to take another run at it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }
}
