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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.joebywan.daybook.ui.rememberHintPaint
import com.joebywan.daybook.ui.theme.BoardHues
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joebywan.daybook.core.BoardHighlight
import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.movedCursor
import com.joebywan.daybook.core.gridCursor
import com.joebywan.daybook.core.boardKeys
import com.joebywan.daybook.core.keepClear
import com.joebywan.daybook.core.highlightGrid
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.TutorialFrame
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.forEachBit
import kotlinx.serialization.Serializable
import com.joebywan.daybook.ui.feelSquare
import com.joebywan.daybook.ui.rememberMoveFeel
import com.joebywan.daybook.ui.drawWalls
import com.joebywan.daybook.ui.drawHintDim
import com.joebywan.daybook.ui.hintRims
import com.joebywan.daybook.ui.squareRim
import com.joebywan.daybook.ui.gridCellAt

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
    /**
     * The board before the first fill, and every fill since as `cell * 8 + colour`. Only the hint
     * reads them: a board that can no longer be finished has to be able to say *which* fill lost it
     * and how far back Undo has to go, and the cells alone cannot say that because a fill is not
     * reversible. Both are the player's own moves, so nothing here is hidden from them.
     *
     * Empty on a board that has never been filled, and [start] stays empty on a game saved before
     * these existed — the hint then says "undo back" without naming the fill.
     */
    val start: List<Int> = emptyList(),
    val trail: List<Int> = emptyList(),
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
        return copy(
            cells = next,
            moves = moves + 1,
            start = if (moves == 0) cells else start,
            trail = trail + (index * 8 + colour),
        )
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
        "Squares of one colour joined edge to edge form a single area, drawn with a heavy outline.",
        "Pick a colour below, then tap an area to flood it with that colour.",
        "A flooded area merges with every neighbouring area already in that colour — " +
            "growing one big blob is how you cover ground quickly.",
        "Turn the whole board one colour before the fills run out.",
        "The limit is the fewest fills the board can be done in, so every fill has to count.",
        "Tapping an area in its own colour does nothing and costs nothing.",
    )

    override val keyboardHelp = listOf(
        "Number keys pick a colour, arrows move, Space or Enter pours it.",
    )

    /**
     * Board colours: five of the six non-Coral/Green hues of docs/COLOUR.md (Coral and Green stay free for the "out of
     * fills" error and for right/wrong elsewhere), the same in both schemes. Each takes the step that spreads the set
     * across brightness as well as hue, since equal-lightness hues collapse to one grey for a colour-blind player:
     * Amber fill (pale), Teal ink, Violet mark, Rose ink (deep), Blue fill (pale). Order is part of the contract: saves
     * store the index, so index i is always "colour i", and [MosaicTeacher.colourNames] follows it. The first 3 / 4 / 5
     * (by tier) are at least 20 / 15 / 15 CIEDE2000 apart including colour-blind vision; [MosaicPaletteTest] pins it.
     * Digits go on each fill in [BoardHues.onFill].
     */
    val palette: List<Color> = listOf(
        BoardHues.fill(34f, false),
        BoardHues.ink(175f, false),
        BoardHues.mark(268f),
        BoardHues.ink(330f, false),
        BoardHues.fill(215f, false),
    )


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
     * @param optimum the length the *proven* optimal solution must have for a board to ship. The move
     *   limit is exactly that optimum on every tier (zero slack, settled): a limit above it rewards a
     *   wasted fill, which teaches the habit the puzzle exists to break. Difficulty is carried by grid
     *   size, section count, palette and how long the optimal line is -- not by how much of that line
     *   a player is allowed to miss.
     *
     * | tier     | grid  | blobs | colours | areas | optimum = limit |
     * |----------|-------|------:|--------:|------:|----------------:|
     * | STANDARD | 8x12  |    16 |       3 | 11-16 |               4 |
     * | HARD     | 9x14  |    20 |       4 | 16-20 |               5 |
     * | EXPERT   | 10x16 |    24 |       5 | 21-24 |               6 |
     *
     * The areas and optimum columns are measured, not hoped for — a hundred and twenty daily seeds
     * per tier land in those ranges, and a board whose proven optimum misses the tier's number is
     * thrown back. That costs a few extra generations and buys a tier that plays the same on a
     * Tuesday as on a Friday, which random blob growth does not otherwise give.
     *
     * Slack used to run 3 / 2 / 1 and a Standard board was reported finished with four of its seven
     * fills unspent: three spare fills on a four-fill optimum is the absence of a constraint, since
     * no order of sensible pours loses. It went to one a tier, then to none.
     *
     * The bands are set from where the tier's boards actually fall, checked against a greedy player
     * that always takes the fill swallowing the most areas. Over 120 seeds a tier, with one spare
     * fill, greedy finished 100% of Standard boards, 88% of Hard and 64% of Expert.
     *
     * Expert's band moved from seven to six for two reasons. Six is where nine boards in ten
     * naturally land, so proving one costs a tenth of what hunting for a seven did — and it is the
     * *harder* band, because a seven-fill board has slack built into its own shape: greedy matched
     * the optimum on 43% of the optimum-seven boards against 23% of the optimum-six ones.
     */
    private class Spec(
        val width: Int,
        val height: Int,
        val blobs: Int,
        val colours: Int,
        val optimum: Int,
    )

    private fun specFor(difficulty: Difficulty) = when (difficulty) {
        Difficulty.STANDARD -> Spec(8, 12, 16, 3, 4)
        Difficulty.HARD -> Spec(9, 14, 20, 4, 5)
        Difficulty.EXPERT -> Spec(10, 16, 24, 5, 6)
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
                if (pass == 0 && line.size != spec.optimum) return@repeat
                return board.copy(limit = line.size)
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
        return stripes.copy(limit = line?.size ?: (stripes.areaCount() - 1))
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
    internal class Position(
        val member: LongArray,
        val nbr: LongArray,
        val hue: IntArray,
        val alive: Long,
        val count: Int,
    ) {
        /** The groups touching [group] that wear [colour]: what pouring [colour] into it swallows. */
        fun eaten(group: Int, colour: Int): Long {
            var out = 0L
            nbr[group].forEachBit { if (hue[it] == colour) out = out or (1L shl it) }
            return out
        }

        /** How many colours the live groups wear. */
        fun colours(): Int {
            var present = 0L
            alive.forEachBit { present = present or (1L shl hue[it]) }
            return present.countOneBits()
        }

        /** The board after pouring [colour] into group [group], neighbours in that colour merged in. */
        fun play(group: Int, colour: Int): Position {
            val eaten = eaten(group, colour)
            val member = this.member.copyOf()
            val nbr = this.nbr.copyOf()
            val hue = this.hue.copyOf()

            var grown = member[group]
            var touching = nbr[group]
            eaten.forEachBit { j ->
                grown = grown or member[j]
                touching = touching or nbr[j]
            }
            touching = touching and (eaten or (1L shl group)).inv()

            member[group] = grown
            nbr[group] = touching
            hue[group] = colour
            // Only the blob's own neighbours can have been pointing at something it swallowed.
            touching.forEachBit { k -> nbr[k] = (nbr[k] and eaten.inv()) or (1L shl group) }
            return Position(member, nbr, hue, alive and eaten.inv(), count - eaten.countOneBits())
        }
    }

    /**
     * The single-colour areas of [cells], labelled in reading order (area k is the k-th area whose
     * first cell is met), and the board lifted into a root [Position]; null past [max] areas.
     * Shared by the generator's search and [MosaicTeacher].
     */
    internal fun areas(width: Int, height: Int, cells: List<Int>, max: Int): Pair<IntArray, Position>? {
        val n = cells.size
        val label = IntArray(n) { -1 }
        val queue = IntArray(n)
        var count = 0
        for (i in 0 until n) {
            if (label[i] >= 0) continue
            if (count == max) return null
            var head = 0
            var tail = 0
            label[i] = count
            queue[tail++] = i
            while (head < tail) {
                val x = queue[head++]
                val r = x / width
                val c = x % width
                for (y in intArrayOf(
                    if (r > 0) x - width else -1,
                    if (r < height - 1) x + width else -1,
                    if (c > 0) x - 1 else -1,
                    if (c < width - 1) x + 1 else -1,
                )) {
                    if (y >= 0 && label[y] < 0 && cells[y] == cells[x]) {
                        label[y] = count
                        queue[tail++] = y
                    }
                }
            }
            count++
        }
        val nbr = LongArray(count)
        for (x in 0 until n) {
            val right = if (x % width < width - 1) x + 1 else -1
            val down = if (x / width < height - 1) x + width else -1
            for (y in intArrayOf(right, down)) {
                if (y < 0 || label[x] == label[y]) continue
                nbr[label[x]] = nbr[label[x]] or (1L shl label[y])
                nbr[label[y]] = nbr[label[y]] or (1L shl label[x])
            }
        }
        val hue = IntArray(count)
        for (x in 0 until n) hue[label[x]] = cells[x]
        val root = Position(
            LongArray(count) { 1L shl it },
            nbr,
            hue,
            if (count == 64) -1L else (1L shl count) - 1,
            count,
        )
        return label to root
    }

    /**
     * The searches' lower bound, with the scratch for its sweeps: positions of up to [size] groups.
     * One per search, never shared between threads.
     */
    internal class Bound(size: Int) {
        private val dist = IntArray(size)
        private val queue = IntArray(size)

        /** BFS from [from] over the live groups; returns the farthest one (high half) and its distance (low half). */
        fun sweep(pos: Position, from: Int): Long {
            dist.fill(-1)
            var head = 0
            var tail = 0
            dist[from] = 0
            queue[tail++] = from
            var far = from
            var best = 0
            while (head < tail) {
                val u = queue[head++]
                pos.nbr[u].forEachBit { v ->
                    if (dist[v] < 0) {
                        dist[v] = dist[u] + 1
                        if (dist[v] > best) {
                            best = dist[v]
                            far = v
                        }
                        queue[tail++] = v
                    }
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
         *
         * Past [cutoff] the exact value stops mattering — the caller only asks whether the bound
         * exceeds what is left — so a colour count already over it is returned without the two
         * sweeps. That changes no answer, only how often the sweeps run.
         */
        fun of(pos: Position, cutoff: Int = Int.MAX_VALUE): Int {
            if (pos.count <= 1) return 0
            val byColour = pos.colours() - 1
            if (byColour > cutoff) return byColour
            val first = sweep(pos, pos.alive.countTrailingZeroBits())
            val spread = sweep(pos, (first ushr 32).toInt()).toInt()
            return maxOf(byColour, (spread + 1) / 2)
        }
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

        private val bounds = Bound(areas)
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
            pos.alive.forEachBit { i ->
                gain.fill(0)
                pos.nbr[i].forEachBit { j -> gain[pos.hue[j]]++ }
                for (colour in 0 until colours) {
                    if (colour == pos.hue[i]) continue
                    out[k] = (i shl 4) or colour
                    score[k] = gain[colour]
                    if (gain[colour] > top) top = gain[colour]
                    k++
                }
            }
            bucket.fill(0, 0, top + 2)
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
            pos.alive.forEachBit { j ->
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
            if (bounds.of(pos, remaining) > remaining) return false
            val key = fingerprint(pos)
            if ((failedAt[key] ?: -1) >= remaining) return false
            for (move in candidates(pos)) {
                path[depth] = move
                if (descend(pos.play(move ushr 4, move and 15), depth + 1, ceiling)) return true
                if (spent > budget) return false
            }
            failedAt[key] = remaining
            return false
        }

        /** Runs the iterative deepening. Null means the budget ran out, never "no solution". */
        fun run(root: Position, firstCell: IntArray): List<MosaicMove>? {
            var ceiling = bounds.of(root)
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
        val (label, root) = areas(state.width, state.height, state.cells, 64) ?: return null
        if (root.count <= 1) return emptyList()
        val firstCell = IntArray(root.count)
        for (i in label.indices.reversed()) firstCell[label[i]] = i
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

    // ---- teaching ---------------------------------------------------------------------------

    /**
     * A board that can no longer be finished, or a fill still on an optimal line and the reason for
     * it — see [MosaicTeacher]. Replaces nothing: [hint] stays as the fallback for a board the
     * teacher's search cannot decide inside its budget.
     */
    override fun teach(state: PuzzleState): Deduction? {
        val s = state as MosaicState
        val step = MosaicTeacher.teach(s) ?: return null
        val mistake = step.technique == MosaicTeacher.MISTAKE
        val base = s.cells
        val after = if (mistake) null else s.flood(step.cell, step.colour).cells
        return Deduction(
            technique = step.technique,
            nudge = step.nudge,
            explanation = step.explanation,
            focus = step.focus,
            cited = step.cited,
            targets = step.targets,
            mistake = mistake,
            fallback = step.technique == MosaicTeacher.KEEPS,
            applyTo = { now ->
                val m = now as MosaicState
                when {
                    // "Show me" on a lost board goes back to where it could still be done: the
                    // same board Undo would reach, as one state, so one more Undo returns here.
                    mistake -> step.rewind ?: m
                    // A fill is only meaningful on the board it was worked out for.
                    m.cells == base -> m.flood(step.cell, step.colour)
                    else -> m
                }
            },
            reachedBy = { now ->
                val m = now as MosaicState
                if (mistake) {
                    val rewind = step.rewind
                    // Back on the good line: a prefix of the fills that still finished.
                    rewind != null && m.moves <= step.rewindTo &&
                        (m.moves == 0 || (m.start == rewind.start && m.trail == rewind.trail.take(m.moves)))
                } else {
                    m.cells == after
                }
            },
        )
    }

    // ---- the walkthrough ---------------------------------------------------------------------

    /**
     * The walkthrough's board, 5x5, three colours, finished in exactly three fills:
     *
     * ```
     * T T A T V
     * T T A T V
     * T T A V T
     * T T V A A
     * A A V V V   (A amber, T teal, V violet)
     * ```
     *
     * Hand-picked, by exhaustive search, so that each fill the frames teach is the *only* fill that
     * keeps the board inside its limit at that point, and so that each is the teacher's own choice
     * for the reason the caption gives: first the biggest swallow (the violet corner, poured amber,
     * takes three amber areas), then counting (two fills for three colours, and amber is the only
     * colour down to one patch), then the finish. MosaicTeachingTest proves all three.
     */
    internal val TUTORIAL_CELLS = listOf(
        1, 1, 0, 1, 2,
        1, 1, 0, 1, 2,
        1, 1, 0, 2, 1,
        1, 1, 2, 0, 0,
        0, 0, 2, 2, 2,
    )

    /**
     * The "your turn" board: 6x6, three colours, four fills and none spare, with plenty of ways to
     * go right and to go wrong. The teacher finishes it without its fallback.
     */
    internal val PRACTICE_CELLS = listOf(
        0, 0, 0, 1, 2, 2,
        0, 0, 2, 2, 2, 2,
        0, 0, 2, 2, 2, 2,
        0, 0, 0, 1, 0, 0,
        0, 2, 2, 2, 1, 0,
        0, 1, 1, 0, 0, 2,
    )

    /** The three fills the walkthrough teaches, as (cell, colour). */
    internal val TUTORIAL_FILLS = listOf(17 to 0, 2 to 1, 0 to 2)

    private fun tutorialBoard() = MosaicState(5, 5, 3, 3, TUTORIAL_CELLS)

    /**
     * Accepts exactly the board [base] reaches by pouring [colour] into [cell]'s area, from any cell
     * of that area. Strict otherwise: each frame's caption is written for the board the one before
     * it leaves.
     */
    private fun pour(base: MosaicState, cell: Int, colour: Int): (PuzzleState) -> Boolean {
        val want = base.flood(cell, colour).cells
        return { next -> next is MosaicState && next.cells == want }
    }

    override val tutorial: List<TutorialFrame> by lazy {
        val start = tutorialBoard()
        val (c1, k1) = TUTORIAL_FILLS[0]
        val (c2, k2) = TUTORIAL_FILLS[1]
        val (c3, k3) = TUTORIAL_FILLS[2]
        val second = start.flood(c1, k1)
        val third = second.flood(c2, k2)
        val tealArea = start.area(0).toSet()
        listOf(
            TutorialFrame(
                state = start,
                caption = "Touching squares of one colour make an area, outlined in black. Turn the whole board " +
                    "one colour. The line above counts your fills against the fewest this board needs: here 3.",
                highlight = BoardHighlight(strong = tealArea),
            ),
            TutorialFrame(
                state = start,
                caption = "Pick amber below, then tap the glowing violet area. It joins every amber " +
                    "area it touches: three at once, more than any other fill can.",
                highlight = BoardHighlight(strong = start.area(c1).toSet(), soft = amberNeighbours(start, c1)),
                accepts = pour(start, c1, k1),
                retry = "Pick amber below, then tap the glowing area.",
                done = "Four areas became one. The bigger it grows, the more each fill takes.",
            ),
            TutorialFrame(
                state = second,
                caption = "2 fills left, 3 colours. A fill wipes out a colour only by covering its last " +
                    "patch, so both fills must. Amber is down to one patch: pour teal into it.",
                highlight = BoardHighlight(strong = second.area(c2).toSet()),
                accepts = pour(second, c2, k2),
                retry = "Pick teal below, then tap the glowing area.",
                done = "Amber is gone. Two colours, one fill.",
            ),
            TutorialFrame(
                state = third,
                caption = "Every other area touches the teal one, and they're all violet. " +
                    "Pour violet into teal to finish.",
                highlight = BoardHighlight(strong = third.area(c3).toSet()),
                accepts = pour(third, c3, k3),
                retry = "Pick violet below, then tap the glowing area.",
                done = "One colour, three fills: the least it could take.",
            ),
            TutorialFrame(
                state = MosaicState(6, 6, 3, 4, PRACTICE_CELLS),
                caption = "Your turn: one colour in 4 fills, none spare. Stuck, or out of fills? Hint shows you why.",
                freePlay = true,
                done = "Solved. That's all there is to it.",
            ),
        )
    }

    /** Cells of the areas a fill of [cell]'s area with amber would swallow, for the first lesson. */
    private fun amberNeighbours(s: MosaicState, cell: Int): Set<Int> {
        val mine = s.area(cell).toSet()
        val out = mutableSetOf<Int>()
        for (x in mine) {
            val r = x / s.width
            val c = x % s.width
            for (y in listOf(
                if (r > 0) x - s.width else -1,
                if (r < s.height - 1) x + s.width else -1,
                if (c > 0) x - 1 else -1,
                if (c < s.width - 1) x + 1 else -1,
            )) {
                if (y >= 0 && y !in mine && s.cells[y] == 0) out += s.area(y).toList()
            }
        }
        return out
    }

    // ---- home-grid motif ----------------------------------------------------------------------

    /**
     * A hand-picked 4x4 corner, as indexes into [palette]. Written out rather than generated so
     * the tile looks the same on every device and every day.
     *
     * Four areas, two of them square and two not, because the shape of an area is the thing a
     * player is reading when they choose where to pour; four squares in a row would say "grid"
     * rather than "regions". Coarse on purpose — sixteen cells is the most that can carry a
     * ten-percent outline at 80dp and still leave flat colour inside each area.
     */
    private val PREVIEW_CELLS = listOf(
        0, 0, 1, 1,
        0, 0, 1, 3,
        2, 2, 3, 3,
        2, 2, 3, 3,
    )

    /** Side of [PREVIEW_CELLS]; it is square, unlike a real board. */
    private const val PREVIEW_SIDE = 4

    /**
     * Flat areas with the heavy seams between them, which is the whole of what Mosaic looks like.
     * Drawn straight rather than through [MosaicState] so nothing here can spend a fill or touch
     * the solver.
     */
    @Composable
    override fun Preview(modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        Canvas(modifier) {
            val stepPx = size.minDimension / PREVIEW_SIDE
            for (cell in PREVIEW_CELLS.indices) {
                drawRect(
                    color = palette[PREVIEW_CELLS[cell]],
                    topLeft = Offset((cell % PREVIEW_SIDE) * stepPx, (cell / PREVIEW_SIDE) * stepPx),
                    size = Size(stepPx, stepPx),
                )
            }

            drawWalls(PREVIEW_SIDE, PREVIEW_SIDE, stepPx, stepPx * 0.1f, scheme.onBackground) { PREVIEW_CELLS[it] }
        }
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
        val measurer = rememberTextMeasurer()
        val live = interactive && !s.failed
        val (highlight, glow, pulse) = rememberHintPaint()

        // The keyboard cursor (see MosaicKeys): transient, so not in the state.
        var cursor by remember(s.colours, s.width, s.height) { mutableStateOf<Int?>(null) }
        Column(
            Modifier
                .fillMaxSize()
                .boardKeys(live) { key, _, repeat ->
                    movedCursor(cursor, key, s.height, s.width)?.let { cursor = it; return@boardKeys true }
                    val action = mosaicKeyAction(key, s.colours) ?: return@boardKeys false
                    when (action) {
                        is MosaicKeyAction.Pick -> selected = action.colour
                        MosaicKeyAction.Fill -> {
                            val at = cursor ?: return@boardKeys false
                            if (!repeat) s.fillKey(at, selected)?.let(onState)
                        }
                    }
                    true
                }
                .padding(horizontal = 16.dp),
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
                // The area a fill recoloured settles. No units: the only one is the whole board, which is the solve.
                val feel = rememberMoveFeel(s.cells)

                Canvas(
                    Modifier
                        .width(step * s.width)
                        .height(step * s.height)
                        .highlightGrid(s.width, s.height)
                        .gridCursor(cursor.takeIf { live }, s.width, s.height, scheme.primary)
                        .pointerInput(s, live, selected) {
                            if (!live) return@pointerInput
                            detectTapGestures { offset: Offset ->
                                val cell = gridCellAt(offset, stepPx, s.width, s.height)
                                cursor = cell
                                // Silence rather than a wasted fill when the area is already this
                                // colour: no state out means no move spent and no undo entry.
                                if (s.cells[cell] != selected) onState(s.flood(cell, selected))
                            }
                        }
                ) {
                    for (cell in s.cells.indices) {
                        val r = cell / s.width
                        val c = cell % s.width
                        feelSquare(feel, cell, Offset(c * stepPx, r * stepPx), Size(stepPx, stepPx), Color(accent)) {
                            drawRect(
                                color = palette[s.cells[cell]],
                                topLeft = Offset(c * stepPx, r * stepPx),
                                size = Size(stepPx, stepPx),
                            )
                        }
                    }

                    // Colour is never the only signal: one digit per area, in its first cell, the same
                    // digit as the swatch and its number key.
                    val seen = BooleanArray(s.cells.size)
                    for (cell in s.cells.indices) {
                        if (seen[cell]) continue
                        for (x in s.area(cell)) seen[x] = true
                        val fill = palette[s.cells[cell]]
                        val layout = measurer.measure(
                            (s.cells[cell] + 1).toString(),
                            TextStyle(color = BoardHues.onFill(fill), fontSize = (stepPx * 0.34f).toSp(), fontWeight = FontWeight.Bold),
                        )
                        drawText(
                            layout,
                            topLeft = Offset(
                                (cell % s.width) * stepPx + stepPx * 0.12f,
                                (cell / s.width) * stepPx + stepPx * 0.06f,
                            ),
                        )
                    }

                    // Heavy strokes only where the colour changes, so each area reads as one
                    // outlined shape rather than a grid of tiles.
                    drawWalls(s.width, s.height, stepPx, stepPx * 0.1f, scheme.onBackground) { s.cells[it] }

                    if (!highlight.isEmpty) {
                        // Everything the hint does not name steps back, so the glow is findable on
                        // a 160-cell board full of outlines.
                        drawHintDim(highlight, s.width, s.height, stepPx, scheme.background)

                        // A band of page colour just inside the glow, so a strong rim reads as
                        // something other than one more of the black seams between areas. Widths
                        // checked in a rendered mock, light and dark: much wider and the band eats
                        // a one-cell arm's colour.
                        hintRims(highlight, stepPx, glow, pulse.value, scheme.background) { set, width, colour ->
                            squareRim(set, width, colour, s.width, s.height, { i ->
                                val x0 = i % s.width * stepPx
                                val y0 = i / s.width * stepPx
                                Rect(x0, y0, x0 + stepPx, y0 + stepPx)
                            })
                        }
                    }
                }
            }

            // A lost board says so in the palette's own box (its swatches can do nothing then), so the board above
            // never moves: the swatches stay laid out, unseen, and come back with an Undo.
            Box(Modifier.fillMaxWidth().padding(bottom = 4.dp).keepClear(), contentAlignment = Alignment.Center) {
                Row(
                    Modifier.fillMaxWidth().alpha(if (s.failed) 0f else 1f),
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
                                .background(palette[colour])
                                .border(1.dp, scheme.outline, RoundedCornerShape(11.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                (colour + 1).toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = BoardHues.onFill(palette[colour]),
                            )
                        }
                    }
                }

                if (s.failed) {
                    Text(
                        "Out of fills. Undo, or tap Hint to find the fill that lost it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.error,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
