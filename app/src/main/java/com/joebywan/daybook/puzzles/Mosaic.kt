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
     *
     * It buys less than it used to, now that the search covers every fill rather than only the
     * merging ones: an Expert board takes around thirty thousand nodes to prove and the worst
     * measured runs to a hundred and thirty thousand. Raising the ceiling was still the wrong
     * answer. A budget wide enough for the very worst board is a hint that makes a phone think for
     * seconds, and a board this budget cannot prove is one reseed away from a board it can — the
     * number is a cost ceiling, not a difficulty dial.
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
     * | STANDARD | 8x12  |    16 |       3 | 11-16 |       4 |     1 |     5 |
     * | HARD     | 9x14  |    20 |       4 | 16-20 |       5 |     1 |     6 |
     * | EXPERT   | 10x16 |    24 |       5 | 21-24 |       6 |     1 |     7 |
     *
     * The areas and optimum columns are measured, not hoped for — a hundred and twenty daily seeds
     * per tier land in those ranges, and the optimum column is a single number because the band is
     * a single number: a board whose proven optimum misses it is thrown back. That costs a few
     * extra generations and buys a tier that plays the same on a Tuesday as on a Friday, which
     * random blob growth does not otherwise give.
     *
     * **Every tier now forgives exactly one fill, and that is the fix, not an oversight.** Slack
     * used to run 3 / 2 / 1 and a Standard board was reported finished with four of its seven
     * fills unspent. Three spare fills on a four-fill optimum is not a forgiving tier, it is the
     * absence of a constraint: there is no order of sensible pours that loses. One spare fill is
     * the smallest slack that still lets a player misjudge a pour and recover, and the difference
     * between the tiers is carried where it belongs — board size, palette, and how long the
     * shortest line is.
     *
     * The bands are set from where the tier's boards actually fall, checked against a greedy player
     * that always takes the fill swallowing the most areas. Over 120 seeds a tier, greedy lands
     * inside the limit on 100% of Standard boards, 88% of Hard and 64% of Expert, which is the
     * shape a difficulty curve should have: on Standard, play sensibly and you finish; on Expert,
     * the obvious fill loses better than a third of the time.
     *
     * Expert's band moved from seven to six for both of those reasons at once. Six is where nine
     * boards in ten naturally land, so proving one costs a tenth of what hunting for a seven did —
     * and it is the *harder* band, because a seven-fill board has slack built into its own shape:
     * greedy matched the optimum on 43% of the optimum-seven boards against 23% of the
     * optimum-six ones.
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
        Difficulty.STANDARD -> Spec(8, 12, 16, 3, 4..4, 1)
        Difficulty.HARD -> Spec(9, 14, 20, 4, 5..5, 1)
        Difficulty.EXPERT -> Spec(10, 16, 24, 5, 6..6, 1)
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

        // Three passes, each giving up something rather than giving up the proof. The first insists
        // on the tier's optimum; the second takes any optimum that was proved; the third keeps
        // searching but pays for a deeper one, since a board that defeats the budget is usually
        // expensive rather than unprovable.
        //
        // No pass ships a limit it has not proved. An earlier version let the last pass skip the
        // search and use areaCount - 1 -- a limit that is always reachable, by the argument that a
        // fill eats at least one neighbour, but which can sit far above what the board needs. That
        // is how a board whose optimum was three shipped asking for seven, and the owner finished
        // it with four fills spare. "Reachable" was never the property that mattered; "this is
        // what the board is worth" is.
        for (pass in 0..2) {
            repeat(ATTEMPTS) { attempt ->
                val rng = Rng(seed + attempt * 0x9E3779B9L + pass * 0x7F4A7C15L)
                val board = MosaicState(spec.width, spec.height, spec.colours, 0, paint(rng, spec))
                if (board.solved) return@repeat
                val budget = if (pass == 2) SOLVE_BUDGET * 4 else SOLVE_BUDGET
                val line = solve(board, budget) ?: return@repeat
                if (pass == 0 && line.size !in spec.optimum) return@repeat
                return board.copy(limit = line.size + spec.slack)
            }
        }

        // Only reachable if blob growth painted one flat colour forty times running, three passes
        // over. Stripes cannot do that, and they are cheap to solve, so even this last resort
        // ships a proved number; areaCount - 1 stands behind it purely so the function is total.
        val stripes = MosaicState(
            spec.width, spec.height, spec.colours, 0,
            List(spec.width * spec.height) { (it / spec.width) % spec.colours },
        )
        val line = solve(stripes, SOLVE_BUDGET * 4)
        return stripes.copy(limit = (line?.size ?: (stripes.areaCount() - 1)) + spec.slack)
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
     * A search position: the board's areas, some of them merged into bigger groups.
     *
     * Groups are never renumbered. A group is named by the id of one of the areas the *board being
     * searched* started with — the one that was flooded — and merging only ever widens that group's
     * membership, so a name stays valid for the whole search and [Search.path] can hand back the
     * area that was tapped without a translation table.
     *
     * Adjacency is carried in [nbr] and patched on each fill rather than rebuilt. That matters more
     * than it looks: rebuilding it is quadratic in the group count and the search now visits enough
     * positions that a quadratic step at every node dominates everything else.
     *
     * Sixty-four areas is the ceiling the masks buy; generated boards run to two dozen and flooding
     * never splits an area, so a board in play can only ever have fewer.
     *
     * One invariant carries the merge logic: no two adjacent groups ever share a colour. It holds
     * at the root because areas are read off the cell colours, and a fill keeps it by swallowing
     * every same-coloured neighbour of the group it touches. Without it a fill would have to
     * cascade — swallow a neighbour, then that neighbour's same-coloured neighbours, and so on —
     * and with it, one pass over the flooded group's own neighbours is the whole of a merge.
     */
    private class Position(
        val member: LongArray,
        val nbr: LongArray,
        val hue: IntArray,
        val alive: Long,
        val count: Int,
    )

    /** Labels the board's areas and lifts them into a [Position], or null past the 64-area ceiling. */
    private fun positionOf(state: MosaicState): Pair<Position, IntArray>? {
        val w = state.width
        val n = state.cells.size
        val label = IntArray(n) { -1 }
        val firstCell = ArrayList<Int>()
        for (i in 0 until n) {
            if (label[i] >= 0) continue
            if (firstCell.size >= 64) return null
            firstCell += i
            for (cell in state.area(i)) label[cell] = firstCell.size - 1
        }
        val count = firstCell.size
        val nbr = LongArray(count)
        for (cell in 0 until n) {
            val c = cell % w
            if (c < w - 1 && label[cell] != label[cell + 1]) {
                nbr[label[cell]] = nbr[label[cell]] or (1L shl label[cell + 1])
                nbr[label[cell + 1]] = nbr[label[cell + 1]] or (1L shl label[cell])
            }
            if (cell / w < state.height - 1 && label[cell] != label[cell + w]) {
                nbr[label[cell]] = nbr[label[cell]] or (1L shl label[cell + w])
                nbr[label[cell + w]] = nbr[label[cell + w]] or (1L shl label[cell])
            }
        }
        val position = Position(
            LongArray(count) { 1L shl it },
            nbr,
            IntArray(count) { state.cells[firstCell[it]] },
            if (count == 64) -1L else (1L shl count) - 1,
            count,
        )
        return position to firstCell.toIntArray()
    }

    /**
     * The exact shortest-line search.
     *
     * One instance per [solve] call, because it owns the scratch the hot path would otherwise
     * allocate at every node — two breadth-first sweeps and a move list per position is enough
     * garbage to show up in the generator's time budget.
     *
     * @param areas the board's area count, which is also the width of every scratch array.
     * @param colours how many colours the *board* has, not how big [palette] is. A three-colour
     *   board must not be searched as if five swatches were on offer: the player is never shown
     *   them, and the phantom moves would treble the branching for nothing.
     */
    private class Search(private val areas: Int, private val colours: Int, private val budget: Int) {

        private val dist = IntArray(areas)
        private val queue = IntArray(areas)
        private val gain = IntArray(colours)
        private val bucket = IntArray(areas + 1)
        private val path = IntArray(areas)

        /**
         * The deepest failed search per position.
         *
         * Fills commute far more often than they look like they should — two pours in either order
         * land on the same board — so the same position turns up over and over under different move
         * orders, and with every fill now on offer there are many more orders to arrive by. This
         * table is what makes the full move set affordable at all. Entries stay valid as the
         * ceiling rises, because "no line of r fills from here" does not stop being true.
         */
        private val failedAt = HashMap<Long, Int>()

        private var spent = 0
        private var reached = 0

        /**
         * Every legal fill, encoded as `group shl 4 or colour`, most promising first.
         *
         * *Every* fill: each live group paired with each colour but its own. An earlier version
         * offered only colours a neighbour already wore, reasoning that a fill merging nothing
         * merely repaints one area and can always be swapped for one that does. That is plausible,
         * it was never proved, and the old KDoc said as much — a subset search can only report a
         * line that is too long, which is a move limit too loose to constrain play.
         *
         * Searching the subset was in fact measured as harmless: across 9,630 boards — random small
         * ones exhaustively, and 630 real tier boards — the restricted optimum never once exceeded
         * the true one. The subset went anyway. A shipped move limit should not rest on a
         * conjecture that happens to hold on the boards someone thought to check, and the cost of
         * dropping it turned out to be affordable: branching rises from roughly the area count to
         * `areas x (colours - 1)`, which the transposition table and the bound below absorb.
         *
         * Ordered by how many groups the fill swallows, because finding *a* win early is what lets
         * the ceiling below it be refuted cheaply. Counting sort rather than comparison sort: with
         * the full move set this list runs to a hundred entries at every node.
         */
        private fun candidates(pos: Position): IntArray {
            val out = IntArray(pos.count * (colours - 1))
            val score = IntArray(out.size)
            var top = 0
            var k = 0
            var bits = pos.alive
            while (bits != 0L) {
                val i = java.lang.Long.numberOfTrailingZeros(bits)
                bits = bits and (bits - 1)
                java.util.Arrays.fill(gain, 0)
                var near = pos.nbr[i]
                while (near != 0L) {
                    val j = java.lang.Long.numberOfTrailingZeros(near)
                    near = near and (near - 1)
                    gain[pos.hue[j]]++
                }
                for (colour in 0 until colours) {
                    if (colour == pos.hue[i]) continue
                    out[k] = (i shl 4) or colour
                    score[k] = gain[colour]
                    if (gain[colour] > top) top = gain[colour]
                    k++
                }
            }
            java.util.Arrays.fill(bucket, 0, top + 2, 0)
            for (s in score) bucket[s]++
            // Running totals from the top score down, so the highest-gain fills land first.
            var at = 0
            for (s in top downTo 0) {
                val here = bucket[s]
                bucket[s] = at
                at += here
            }
            val sorted = IntArray(out.size)
            for (i in out.indices) sorted[bucket[score[i]]++] = out[i]
            return sorted
        }

        /** The board after pouring [colour] into group [group], neighbours in that colour merged in. */
        private fun play(pos: Position, group: Int, colour: Int): Position {
            var eaten = 0L
            var near = pos.nbr[group]
            while (near != 0L) {
                val j = java.lang.Long.numberOfTrailingZeros(near)
                near = near and (near - 1)
                if (pos.hue[j] == colour) eaten = eaten or (1L shl j)
            }

            val member = pos.member.copyOf()
            val nbr = pos.nbr.copyOf()
            val hue = pos.hue.copyOf()

            var grown = pos.member[group]
            var touching = pos.nbr[group]
            var bits = eaten
            while (bits != 0L) {
                val j = java.lang.Long.numberOfTrailingZeros(bits)
                bits = bits and (bits - 1)
                grown = grown or pos.member[j]
                touching = touching or pos.nbr[j]
            }
            touching = touching and (eaten or (1L shl group)).inv()

            member[group] = grown
            nbr[group] = touching
            hue[group] = colour
            // Only the blob's own neighbours can have been pointing at something it swallowed.
            var rim = touching
            while (rim != 0L) {
                val k = java.lang.Long.numberOfTrailingZeros(rim)
                rim = rim and (rim - 1)
                nbr[k] = (nbr[k] and eaten.inv()) or (1L shl group)
            }
            return Position(
                member, nbr, hue,
                pos.alive and eaten.inv(),
                pos.count - java.lang.Long.bitCount(eaten),
            )
        }

        /** BFS from [from] over the live groups; returns the farthest one and its distance. */
        private fun sweep(pos: Position, from: Int): Long {
            java.util.Arrays.fill(dist, -1)
            var head = 0
            var tail = 0
            dist[from] = 0
            queue[tail++] = from
            var far = from
            var best = 0
            while (head < tail) {
                val u = queue[head++]
                var bits = pos.nbr[u]
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
            return (far.toLong() shl 32) or best.toLong()
        }

        /**
         * A lower bound on the fills still needed. Admissible, so IDA* built on it returns a
         * genuine optimum rather than a good-looking line.
         *
         * Two bounds, whichever is larger:
         *
         * - **Colours.** A fill recolours one group, so it retires a colour only when that group
         *   was the last one wearing it — at most one colour per fill. One colour must remain,
         *   hence `distinct - 1`.
         * - **Distance.** A fill contracts a group and its same-coloured neighbours into one node.
         *   That set is a star centred on the flooded group, so it has diameter two, and
         *   contracting a set of diameter two shortens no path by more than two. The board ends as
         *   a single node at distance zero, so a spread of `s` needs `ceil(s / 2)` fills.
         *
         * The spread comes from a double sweep (BFS from anywhere, then BFS from the farthest node
         * found) rather than all-pairs. That is a lower bound on the true diameter, which is what
         * keeps the whole thing admissible, and it costs two BFS per node instead of one per group.
         *
         * A radius bound is tempting here and is wrong: two blobs can grow from opposite ends of
         * the board and only meet on the last fill, so nothing forces the line to spread out of a
         * single centre.
         */
        private fun bound(pos: Position): Int {
            if (pos.count <= 1) return 0
            var present = 0L
            var bits = pos.alive
            while (bits != 0L) {
                val j = java.lang.Long.numberOfTrailingZeros(bits)
                bits = bits and (bits - 1)
                present = present or (1L shl pos.hue[j])
            }
            val byColour = java.lang.Long.bitCount(present) - 1
            val first = sweep(pos, java.lang.Long.numberOfTrailingZeros(pos.alive))
            val spread = sweep(pos, (first ushr 32).toInt()).toInt()
            return maxOf(byColour, (spread + 1) / 2)
        }

        /**
         * Order-independent hash of a position, for [failedAt].
         *
         * Summing per-group hashes sidesteps having to keep the groups in a canonical order, and
         * hashing the *membership* mask rather than the group's name means two lines that merged
         * the same areas by flooding different members of the blob collide on purpose — they are
         * the same board.
         *
         * An accidental collision could only ever make the search miss a line and report a longer
         * optimum, which is a looser move limit rather than an unreachable one — but at 64 bits it
         * is not something that happens.
         */
        private fun fingerprint(pos: Position): Long {
            var sum = 0L
            var bits = pos.alive
            while (bits != 0L) {
                val j = java.lang.Long.numberOfTrailingZeros(bits)
                bits = bits and (bits - 1)
                var z = pos.member[j] xor (pos.hue[j].toLong() * 0x517CC1B727220A95L)
                z = (z xor (z ushr 33)) * -0x7ee3623a03d3c83fL
                z = (z xor (z ushr 29)) * -0x3b314601e57a13adL
                sum += z xor (z ushr 32)
            }
            return sum
        }

        /** Depth-first under an `f = depth + bound` ceiling. True once a win is stored in [path]. */
        private fun descend(pos: Position, depth: Int, ceiling: Int): Boolean {
            if (pos.count <= 1) {
                reached = depth
                return true
            }
            if (spent++ > budget) return false
            val remaining = ceiling - depth
            if (bound(pos) > remaining) return false
            val key = fingerprint(pos)
            if ((failedAt[key] ?: -1) >= remaining) return false
            for (move in candidates(pos)) {
                path[depth] = move
                if (descend(play(pos, move ushr 4, move and 15), depth + 1, ceiling)) return true
                if (spent > budget) return false
            }
            failedAt[key] = remaining
            return false
        }

        /** Runs the iterative deepening. Null means the budget ran out, never "no solution". */
        fun run(root: Position, firstCell: IntArray): List<MosaicMove>? {
            var ceiling = bound(root)
            // Flooding any area with a neighbour's colour eats at least that neighbour, so this
            // many fills always suffice and the ceiling can never run away.
            val worst = root.count - 1
            while (ceiling <= worst) {
                if (descend(root, 0, ceiling)) {
                    // Read back only as far as the win actually went. It always reaches the ceiling
                    // — a shorter line would have been found at a lower one — but the path array
                    // still holds stale entries past it, and trusting that invariant silently is
                    // how a win grows a phantom extra fill.
                    return (0 until reached).map {
                        MosaicMove(firstCell[path[it] ushr 4], path[it] and 15)
                    }
                }
                if (spent > budget) return null
                ceiling++
            }
            return null
        }
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
     * Shortest over every fill the player could make, which is what lets the move limit built from
     * it actually constrain play rather than merely be reachable.
     */
    fun solve(state: MosaicState, budget: Int = SOLVE_BUDGET): List<MosaicMove>? {
        val (root, firstCell) = positionOf(state) ?: return null
        if (root.count <= 1) return emptyList()
        return Search(root.count, state.colours, budget).run(root, firstCell)
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
