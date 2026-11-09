package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.IntArrayList
import com.joebywan.daybook.core.LoopEdges
import com.joebywan.daybook.core.LoopFound
import com.joebywan.daybook.core.LoopGrid
import com.joebywan.daybook.core.LoopRules
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.Verdict
import kotlinx.serialization.Serializable

/**
 * A Loop board in progress: the numbers, the answer loop, and the player's lines and crosses.
 *
 * [size] is the side in squares; the loop runs along the squares' sides, between the `(size + 1) x (size + 1)` dots at
 * their corners ([LoopLogic.grid], the shared [LoopGrid]: horizontal edges first, `r * size + c` from dot `(r, c)` to
 * `(r, c + 1)`, then vertical, `size * (size + 1) + r * (size + 1) + c` from dot `(r, c)` to `(r + 1, c)`). [clues] is
 * one character per square, row by row: `.` none, `0`-`3` how many of its sides the loop uses. [loop] has a `1` on
 * every edge of the answer, read only for mistakes and tests. [edges] is the player's: `.` open, `-` a line, `x` a
 * cross (a note: no line here; it never counts).
 */
@Serializable
data class LoopState(
    val size: Int,
    val clues: String,
    val loop: String,
    val edges: String = LoopEdges.OPEN_MARK.toString().repeat(2 * (size + 1) * size),
    override val moves: Int = 0,
) : PuzzleState {

    /** Solved when the lines are one closed loop and every number has that many lines round it. The rules, not [loop]. */
    override val solved: Boolean get() = LoopLogic.isSolved(size, clues, edges)

    /** [mark] on every edge of [indices] that is not already it; one state, one move per edge. */
    fun paint(indices: Collection<Int>, mark: Char): LoopState = moved(LoopEdges.painted(edges, indices, mark))

    /** A tap on edge [e]: an open edge is crossed, and a cross or a line is rubbed out. A tap never draws a line. */
    fun tap(e: Int): LoopState = moved(LoopEdges.tapped(edges, e))

    /** A drag through dots [path]: draws, or over a line rubs out ([LoopEdges.dragged]). One state for the whole drag. */
    fun drag(path: List<Int>): LoopState = moved(LoopEdges.dragged(LoopLogic.grid(size), edges, path))

    private fun moved(painted: Pair<String, Int>): LoopState =
        if (painted.second == 0) this else copy(edges = painted.first, moves = moves + painted.second)
}

/**
 * The rules, the ladder, the exhaustive count and the generator. The lattice, the loop rule, the what-if, the ladder,
 * the count and loop growth are Pearls' ([LoopEdges]); what is Loop's own is the numbers.
 *
 * ## The ladder
 * 1. Local rules, at a dot or a number:
 *    - [K_COUNT]: a number with all its lines drawn crosses its other sides; one that needs every side it has left draws them.
 *    - the dot rules ([LoopEdges.pointRule]): one line and one way on carries on; one way in is a dead end; two lines
 *      take no more.
 *    - [K_CORNER]: a number and the dots at its four corners together (each dot takes no line or two): an edge that is
 *      the same in every way to give the number its count with every corner kept is settled. This is where the
 *      patterns players learn come from (a 3 in a corner, a 1 in a corner, a line running into a 3's corner).
 * 2. The loop rule ([LoopEdges.smallLoop]): an edge that would close a loop that is not the whole answer is crossed.
 * 3. A what-if ([LoopEdges.whatIf]) of at most [LoopEdges.MAX_ROUNDS] rounds.
 * The generator ships a board only when its tier's rungs finish it, needing exactly the deepest, and the teacher
 * ([LoopTeacher]) explains exactly these, so the hints never run out.
 */
internal object LoopLogic {

    const val OPEN = LoopEdges.OPEN
    const val LINE = LoopEdges.LINE
    const val CROSS = LoopEdges.CROSS

    /** A number's own kinds (the dot and loop rules' are [LoopEdges]'). Lower kinds are hinted first. */
    const val K_COUNT = 0
    const val K_CORNER = 8
    const val C_COUNT = 16 // a number with too many lines, or too few sides left
    const val C_CORNER = 17 // a number whose corners leave it no way to have its count

    private val NONE = IntArray(0)

    /** The dots of an `n x n` board. */
    fun grid(n: Int): LoopGrid = LoopGrid.of(n + 1)

    /** Square [q]'s sides on an `n x n` board, in direction order: top, right, bottom, left. */
    fun sides(n: Int, q: Int): IntArray {
        val r = q / n
        val c = q % n
        val m = n + 1
        val h = n * m
        return intArrayOf(r * n + c, h + r * m + c + 1, (r + 1) * n + c, h + r * m + c)
    }

    /** Square [q]'s corner dots: top-left, top-right, bottom-right, bottom-left. Corner `k` touches sides `k` and `(k + 3) % 4`. */
    fun corners(n: Int, q: Int): IntArray {
        val r = q / n
        val c = q % n
        val m = n + 1
        return intArrayOf(r * m + c, r * m + c + 1, (r + 1) * m + c + 1, (r + 1) * m + c)
    }

    /** The squares (one or two) whose side edge [e] is, on an `n x n` board. */
    fun squaresBeside(n: Int, e: Int): IntArray {
        val m = n + 1
        val h = n * m
        val out = IntArrayList()
        if (e < h) {
            val r = e / n
            val c = e % n
            if (r > 0) out.add((r - 1) * n + c)
            if (r < n) out.add(r * n + c)
        } else {
            val r = (e - h) / m
            val c = (e - h) % m
            if (c > 0) out.add(r * n + c - 1)
            if (c < n) out.add(r * n + c)
        }
        return out.toArray()
    }

    // ---- a number's rules -----------------------------------------------------------------------

    /** The count rule at square [q] holding [k]: null when it settles nothing. */
    fun countRule(n: Int, st: IntArray, q: Int, k: Int): LoopFound? {
        val sides = sides(n, q)
        var lines = 0
        var open = 0
        for (e in sides) when (st[e]) {
            LINE -> lines++
            OPEN -> open++
        }
        if (lines > k || lines + open < k) return LoopFound(C_COUNT, q, NONE, NONE)
        if (open == 0) return null
        val opens = sides.filter { st[it] == OPEN }.toIntArray()
        return when (k) {
            lines -> LoopFound(K_COUNT, q, NONE, opens)
            lines + open -> LoopFound(K_COUNT, q, opens, NONE)
            else -> null
        }
    }

    /**
     * The corner rule at square [q] holding [k], reading only the corners in [use] (bit `i` for corner `i`; every corner
     * by default): every way to give [k] lines among its open sides with each used corner dot left able to take no line
     * or two. An open side, or an open edge out of a used corner, with one value in every such way is settled. Null when
     * nothing is; a contradiction when no way is left.
     */
    fun cornerRule(g: LoopGrid, n: Int, st: IntArray, q: Int, k: Int, use: Int = 0xF): LoopFound? {
        val sides = sides(n, q)
        val dots = corners(n, q)
        // The edges out of each corner that are not this square's sides.
        val outer = Array(4) { i ->
            val mine = intArrayOf(sides[i], sides[(i + 3) % 4])
            (0 until 4).map { g.edge(dots[i], it) }.filter { it >= 0 && it !in mine }.toIntArray()
        }
        val open = sides.indices.filter { st[sides[it]] == OPEN }
        var fixed = 0
        for (i in 0 until 4) if (st[sides[i]] == LINE) fixed = fixed or (1 shl i)
        // What each open side and each open outer edge can be, over every way that works: bit 1 line, bit 2 cross.
        val sideCan = IntArray(4)
        val outerCan = Array(4) { IntArray(outer[it].size) }
        var ways = 0
        for (pick in 0 until (1 shl open.size)) {
            var on = fixed
            for (j in open.indices) if (pick and (1 shl j) != 0) on = on or (1 shl open[j])
            if (on.countOneBits() != k) continue
            var ok = true
            val forced = Array(4) { IntArray(0) }
            for (i in 0 until 4) {
                if (use and (1 shl i) == 0) continue
                val a = (on shr i and 1) + (on shr ((i + 3) % 4) and 1)
                var lo = 0
                var oo = 0
                for (e in outer[i]) when (st[e]) {
                    LINE -> lo++
                    OPEN -> oo++
                }
                val total = a + lo
                val can = IntArray(outer[i].size)
                when {
                    total > 2 || total == 1 && oo == 0 -> { ok = false; break }
                    // Two already: the rest are crossed. One: it carries on through the one open edge if that is all.
                    total == 2 -> for (j in outer[i].indices) can[j] = 2
                    total == 1 && oo == 1 -> for (j in outer[i].indices) can[j] = if (st[outer[i][j]] == OPEN) 1 else 0
                    total == 0 && oo == 1 -> for (j in outer[i].indices) can[j] = 2
                    else -> for (j in outer[i].indices) can[j] = 3
                }
                forced[i] = can
            }
            if (!ok) continue
            ways++
            for (i in 0 until 4) sideCan[i] = sideCan[i] or (if (on and (1 shl i) != 0) 1 else 2)
            for (i in 0 until 4) {
                if (use and (1 shl i) == 0) continue
                for (j in outer[i].indices) if (st[outer[i][j]] == OPEN) outerCan[i][j] = outerCan[i][j] or forced[i][j]
            }
        }
        if (ways == 0) return LoopFound(C_CORNER, q, NONE, NONE)
        val lines = IntArrayList()
        val crosses = IntArrayList()
        fun settle(e: Int, can: Int) {
            if (st[e] != OPEN) return
            when (can) {
                1 -> if (!has(lines, e)) lines.add(e)
                2 -> if (!has(crosses, e)) crosses.add(e)
            }
        }
        for (i in open) settle(sides[i], sideCan[i])
        for (i in 0 until 4) {
            if (use and (1 shl i) == 0) continue
            for (j in outer[i].indices) settle(outer[i][j], outerCan[i][j])
        }
        return if (lines.size + crosses.size == 0) null else LoopFound(K_CORNER, q, lines.toArray(), crosses.toArray())
    }

    private fun has(list: IntArrayList, v: Int): Boolean {
        for (i in 0 until list.size) if (list[i] == v) return true
        return false
    }

    /** The number rules at square [q]: its count first, then its corners. */
    fun numberRule(g: LoopGrid, n: Int, clues: String, st: IntArray, q: Int): LoopFound? {
        val k = clues[q] - '0'
        return countRule(n, st, q, k) ?: cornerRule(g, n, st, q, k)
    }

    // ---- the shared ladder ----------------------------------------------------------------------

    /**
     * Loop's rules as the shared ladder ([LoopEdges]) reads them: units `0 until n * n` are the squares (a rule only where
     * there is a number), then `n * n + d` is dot `d`. An edge touches its two dots, the squares beside it, and every
     * square with a corner at either dot (its corner rule reads the edge).
     */
    class Rules(val n: Int, val clues: String) : LoopRules {
        override val g = grid(n)
        override val units = n * n + g.nodes

        override fun rule(st: IntArray, u: Int): LoopFound? = when {
            u >= n * n -> LoopEdges.pointRule(g, st, u - n * n, mustPass = false)
            clues[u] == '.' -> null
            else -> numberRule(g, n, clues, st, u)
        }

        override fun touch(e: Int, add: (Int) -> Unit) {
            for (d in intArrayOf(g.endA[e], g.endB[e])) {
                add(n * n + d)
                for (q in squaresAt(n, d)) if (clues[q] != '.') add(q)
            }
        }

        override fun whole(st: IntArray, s: LoopEdges.Stretches, root: Int, closing: Int): Boolean {
            if (s.edgesIn[root] != s.total) return false
            // Every number kept by exactly these lines and the closing edge.
            for (q in 0 until n * n) {
                if (clues[q] == '.') continue
                var lines = 0
                for (e in sides(n, q)) if (e == closing || st[e] == LINE) lines++
                if (lines != clues[q] - '0') return false
            }
            return true
        }

        override fun finished(st: IntArray): Boolean = isFinished(n, clues, st)
    }

    /** The squares with a corner at dot [d] (up to four), in reading order. */
    fun squaresAt(n: Int, d: Int): IntArray {
        val m = n + 1
        val r = d / m
        val c = d % m
        val out = IntArrayList()
        for (dr in intArrayOf(-1, 0)) for (dc in intArrayOf(-1, 0)) {
            val qr = r + dr
            val qc = c + dc
            if (qr in 0 until n && qc in 0 until n) out.add(qr * n + qc)
        }
        return out.toArray()
    }

    /** Whether the lines of [st] are the whole answer: one closed loop, every number kept. */
    fun isFinished(n: Int, clues: String, st: IntArray): Boolean {
        val g = grid(n)
        LoopEdges.oneLoop(g) { st[it] == LINE } ?: return false
        for (q in 0 until n * n) {
            if (clues[q] == '.') continue
            if (sides(n, q).count { st[it] == LINE } != clues[q] - '0') return false
        }
        return true
    }

    /** The rules, read off the lines alone (crosses are notes). */
    fun isSolved(n: Int, clues: String, edges: String): Boolean = isFinished(n, clues, LoopEdges.stateOf(edges))

    // ---- the ladder, the count -------------------------------------------------------------------

    /** The rungs a board may need. */
    const val LOCAL = LoopEdges.LOCAL
    const val LOOP = LoopEdges.LOOP
    const val WHAT_IF = LoopEdges.WHAT_IF

    /** Everything the rungs up to [depth] settle from [start]: the edges reached, or null on a contradiction. */
    fun settle(n: Int, clues: String, start: IntArray, depth: Int): IntArray? = LoopEdges.settle(Rules(n, clues), start, depth)

    /** The deepest rung the board needs from blank, or null when the ladder does not finish it. */
    fun depth(n: Int, clues: String): Int? = LoopEdges.depth(Rules(n, clues))

    fun depth(s: LoopState): Int? = depth(s.size, s.clues)

    /**
     * How many loops keep every number, with no budget ([LoopEdges.solutions], from the whole ladder's fixpoint: every
     * step of it is sound). Only [Verdict.ExactlyOne] carries the loop.
     */
    fun solutions(n: Int, clues: String): Verdict<String> {
        val rules = Rules(n, clues)
        val root = LoopEdges.settle(rules, IntArray(rules.g.edgeCount), WHAT_IF) ?: return Verdict.None
        return LoopEdges.solutions(rules, root)
    }

    // ---- generation -------------------------------------------------------------------------------

    /** Side in squares, the loop's length as a share of the dots (per cent, a range), and the deepest rung a board must need. */
    class Spec(val side: Int, val lengthLow: Int, val lengthHigh: Int, val needs: Int)

    fun specFor(difficulty: Difficulty): Spec = when (difficulty) {
        Difficulty.STANDARD -> Spec(5, 50, 80, LOCAL)
        Difficulty.HARD -> Spec(7, 50, 80, LOOP)
        Difficulty.EXPERT -> Spec(10, 50, 80, WHAT_IF)
    }

    /** Draws tried per seed before the generator gives up and falls back. */
    const val ATTEMPTS = 200

    fun generate(seed: Long, difficulty: Difficulty): LoopState = generateVerified(seed, difficulty) ?: lastResort(difficulty)

    /**
     * A board for [seed] that its tier's rungs finish, needing exactly the deepest, and the count proves, or null when
     * none of [ATTEMPTS] draws was. Draw `a` uses `Rng(seed + a)`, so a board that needed three draws still depends on
     * its seed alone.
     */
    fun generateVerified(seed: Long, difficulty: Difficulty): LoopState? {
        for (a in 0 until ATTEMPTS) attempt(seed, difficulty, a)?.let { return it }
        return null
    }

    /** Draw number [a] for [seed], if it is accepted. Public to tests so they can count the redraws. */
    fun attempt(seed: Long, difficulty: Difficulty, a: Int): LoopState? {
        val spec = specFor(difficulty)
        val n = spec.side
        val rng = Rng(seed + a)
        val loop = LoopEdges.growLoop(rng, n + 1, spec.lengthLow, spec.lengthHigh, 0) { 0 } ?: return null
        val all = cluesOf(n, loop)
        val clues = LoopEdges.takeBack(rng, all, spec.needs) { c, d -> LoopEdges.finishes(Rules(n, c), d) } ?: return null
        return LoopState(n, clues, loop).takeIf { accepts(it, spec) }
    }

    /** Every square's number for [loop]: how many of its sides the loop uses. */
    fun cluesOf(n: Int, loop: String): String =
        CharArray(n * n) { q -> '0' + sides(n, q).count { loop[it] == '1' } }.concatToString()

    /** Whether [s] may ship at [spec]: the ladder finishes it needing exactly the tier's rung, and it has one answer, [LoopState.loop]. */
    fun accepts(s: LoopState, spec: Spec): Boolean {
        if (s.size != spec.side || depth(s) != spec.needs) return false
        val verdict = solutions(s.size, s.clues)
        return verdict is Verdict.ExactlyOne && verdict.value == s.loop
    }

    /**
     * Only when none of [ATTEMPTS] draws was accepted, which a year of daily seeds on every tier never reaches
     * (`FallbackTest`). A fixed board per tier (what seed 424242 drew), which `LoopRulesTest` proves is accepted at its
     * tier: one loop, the tier's rung needed exactly.
     */
    fun lastResort(difficulty: Difficulty): LoopState = when (difficulty) {
        Difficulty.STANDARD -> LoopState(
            5, ".1.00...1.101221....22.23",
            "000001100000110000010011011001000000101000100010100001101011",
        )
        Difficulty.HARD -> LoopState(
            7, ".0.00.1.01.1......3..2....013..001.2.1.22.2.3....",
            "00000000000011101100001001001000000100001100101001101000000000000000" +
                "01011110110110000001010000011000010010111000",
        )
        Difficulty.EXPERT -> LoopState(
            10,
            "1..2.111...1.0...0.12..1....1.30...0...3.....0.12........03..1.1.0.....2.3...0.3..1..........132131.",
            "01011111110010000000000000000010010000101010000001010100001011100000101001000011011000101100000100000000" +
                "10010001110000001010000000010100000000110011000111011010001000001000001010000000100010110000010000101110000001101100",
        )
    }
}
