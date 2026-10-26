package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.Verdict
import kotlinx.serialization.Serializable

/**
 * A Pearls board in progress: the pearls, the answer loop, and the player's lines and crosses.
 *
 * [pearls] is one character per square, row by row: `.` none, `b` black, `w` white. Edges join the
 * middles of two neighbouring squares: horizontal edge `(r, c)-(r, c+1)` is `r * (n - 1) + c`, then
 * vertical edge `(r, c)-(r+1, c)` is `n * (n - 1) + r * n + c` ([PearlsGrid]). [loop] has a `1` on
 * every edge of the answer, read only for mistakes and tests. [edges] is the player's: `.` open, `-` a
 * line, `x` a cross (a note: no line here; it never counts).
 *
 * Strings rather than lists, as Tally's: a saved game keeps two dozen undo copies.
 */
@Serializable
data class PearlsState(
    val size: Int,
    val pearls: String,
    val loop: String,
    val edges: String = PearlsLogic.OPEN_MARK.toString().repeat(2 * size * (size - 1)),
    override val moves: Int = 0,
) : PuzzleState {

    /** Solved when the lines are one closed loop keeping every pearl's rule. The rules, not [loop]. */
    override val solved: Boolean get() = PearlsLogic.isSolved(size, pearls, edges)

    /** [mark] on every edge of [indices] that is not already it; one state, one move per edge. */
    fun paint(indices: Collection<Int>, mark: Char): PearlsState {
        val next = edges.toCharArray()
        var changed = 0
        for (i in indices) {
            if (next[i] != mark) {
                next[i] = mark
                changed++
            }
        }
        return if (changed == 0) this else copy(edges = next.concatToString(), moves = moves + changed)
    }

    /**
     * A tap on edge [e]: an open edge is crossed, and a cross or a line is rubbed out. Lines are drawn
     * by dragging, so a tap never draws one.
     */
    fun tap(e: Int): PearlsState =
        paint(listOf(e), if (edges[e] == PearlsLogic.OPEN_MARK) PearlsLogic.CROSS_MARK else PearlsLogic.OPEN_MARK)

    /**
     * A drag through squares [path] (each a neighbour of the one before). Its first edge decides what the
     * whole drag does, as a pen does: over a line it rubs lines out, anywhere else it draws them (over
     * crosses too). One state for the whole drag.
     */
    fun drag(path: List<Int>): PearlsState {
        val grid = PearlsGrid.of(size)
        val crossed = (1 until path.size).mapNotNull { grid.between(path[it - 1], path[it]).takeIf { e -> e >= 0 } }
        if (crossed.isEmpty()) return this
        return if (edges[crossed[0]] == PearlsLogic.LINE_MARK) {
            paint(crossed.filter { edges[it] == PearlsLogic.LINE_MARK }, PearlsLogic.OPEN_MARK)
        } else {
            paint(crossed, PearlsLogic.LINE_MARK)
        }
    }
}


/**
 * Where everything is on an `n x n` board: each square's edge and neighbour in each direction
 * (0 up, 1 right, 2 down, 3 left; -1 off the board) and each edge's two squares. Cached per size
 * (a probe-only lookup: nothing iterates the cache).
 */
internal class PearlsGrid private constructor(val n: Int) {
    val squares = n * n
    val horizontal = n * (n - 1)
    val edgeCount = 2 * n * (n - 1)
    private val edgeAt = IntArray(squares * 4) { -1 }
    private val step = IntArray(squares * 4) { -1 }
    val endA = IntArray(edgeCount)
    val endB = IntArray(edgeCount)

    init {
        for (r in 0 until n) for (c in 0 until n - 1) {
            val e = r * (n - 1) + c
            val a = r * n + c
            link(e, a, a + 1, RIGHT)
        }
        for (r in 0 until n - 1) for (c in 0 until n) {
            val e = horizontal + r * n + c
            val a = r * n + c
            link(e, a, a + n, DOWN)
        }
    }

    private fun link(e: Int, a: Int, b: Int, d: Int) {
        endA[e] = a
        endB[e] = b
        edgeAt[a * 4 + d] = e
        edgeAt[b * 4 + (d + 2) % 4] = e
        step[a * 4 + d] = b
        step[b * 4 + (d + 2) % 4] = a
    }

    fun edge(s: Int, d: Int): Int = edgeAt[s * 4 + d]

    fun next(s: Int, d: Int): Int = step[s * 4 + d]

    /** The edge between neighbouring squares [a] and [b], or -1 when they are not neighbours. */
    fun between(a: Int, b: Int): Int {
        for (d in 0 until 4) if (step[a * 4 + d] == b) return edgeAt[a * 4 + d]
        return -1
    }

    fun isHorizontal(e: Int) = e < horizontal

    companion object {
        const val UP = 0
        const val RIGHT = 1
        const val DOWN = 2
        const val LEFT = 3

        private val cache = arrayOfNulls<PearlsGrid>(32)

        fun of(n: Int): PearlsGrid = cache[n] ?: PearlsGrid(n).also { cache[n] = it }
    }
}

/**
 * The rules, the ladder, the exhaustive count and the generator. Nothing here touches the screen, the
 * platform or a hash container, so the same seed makes the same board on the phone and in the browser.
 *
 * ## The ladder
 * Every rule reads edge states ([OPEN], [LINE], [CROSS]) and only ever adds lines and crosses:
 * 1. [found] at a square: the square rules (two lines take no more; one line and one way on
 *    continues; an empty square with one way in is a dead end; a pearl with two ways left takes both)
 *    and the pearl's own rule (a black pearl's arm that cannot be drawn sends it the other way; a
 *    white pearl that cannot go one way goes the other, and must turn on one side).
 * 2. [smallLoop]: an edge joining the two ends of one stretch of line, when the loop it would close
 *    leaves out a pearl or another stretch, is crossed.
 * 3. [whatIf]: suppose an edge is a line (or a cross), follow 1 and 2 for at most [MAX_ROUNDS] rounds,
 *    and something is left impossible, so it is the other.
 * [settle] takes them to a fixpoint. The generator ships a board only when its tier's rungs finish it,
 * and the teacher ([PearlsTeacher]) explains exactly these, so the hints never run out.
 */
internal object PearlsLogic {

    const val OPEN_MARK = '.'
    const val LINE_MARK = '-'
    const val CROSS_MARK = 'x'

    const val OPEN = 0
    const val LINE = 1
    const val CROSS = 2

    const val BLACK = 'b'
    const val WHITE = 'w'

    // ---- what a rule found ----------------------------------------------------------------

    /** Kinds of step, in the teacher's order (simplest first), and kinds of contradiction. */
    const val K_BLACK = 0
    const val K_WHITE = 1
    const val K_WHITE_TURN = 2
    const val K_ONE_WAY = 3
    const val K_PEARL_WAYS = 4
    const val K_DEAD_END = 5
    const val K_FULL = 6
    const val K_SMALL_LOOP = 7

    const val C_THREE = 10 // a square with three or more lines
    const val C_DEAD_LINE = 11 // a line into a square with no way on
    const val C_PEARL_SHUT = 12 // a pearl left fewer than two ways
    const val C_BLACK = 13 // a black pearl that can no longer turn with straight arms
    const val C_WHITE = 14 // a white pearl that can no longer go straight with a turn beside it
    const val C_SMALL_LOOP = 15 // a closed loop that leaves something out

    /** One rule applied at [square]: the edges it makes lines and crosses, or a contradiction ([kind] >= 10). */
    class Found(val kind: Int, val square: Int, val lines: IntArray, val crosses: IntArray) {
        val contradiction: Boolean get() = kind >= C_THREE
    }

    private val NONE = IntArray(0)

    private fun contra(kind: Int, s: Int) = Found(kind, s, NONE, NONE)

    // ---- the local rules --------------------------------------------------------------------

    /** The square rules at [s]; null when they settle nothing. [full] off skips "two lines take no more". */
    fun squareRule(g: PearlsGrid, pearls: String, st: IntArray, s: Int, full: Boolean = true): Found? {
        var lines = 0
        var open = 0
        for (d in 0 until 4) {
            val e = g.edge(s, d)
            if (e < 0) continue
            when (st[e]) {
                LINE -> lines++
                OPEN -> open++
            }
        }
        val pearl = pearls[s] != '.'
        return when {
            lines > 2 -> contra(C_THREE, s)
            lines == 2 -> if (open > 0 && full) Found(K_FULL, s, NONE, opens(g, st, s)) else null
            lines == 1 -> when (open) {
                0 -> contra(C_DEAD_LINE, s)
                1 -> Found(K_ONE_WAY, s, opens(g, st, s), NONE)
                else -> null
            }
            pearl && open < 2 -> contra(C_PEARL_SHUT, s)
            pearl && open == 2 -> Found(K_PEARL_WAYS, s, opens(g, st, s), NONE)
            !pearl && open == 1 -> Found(K_DEAD_END, s, NONE, opens(g, st, s))
            else -> null
        }
    }

    private fun opens(g: PearlsGrid, st: IntArray, s: Int): IntArray {
        val out = IntArrayList()
        for (d in 0 until 4) {
            val e = g.edge(s, d)
            if (e >= 0 && st[e] == OPEN) out.add(e)
        }
        return out.toArray()
    }

    private fun isLine(st: IntArray, e: Int) = e >= 0 && st[e] == LINE

    private fun usable(st: IntArray, e: Int) = e >= 0 && st[e] != CROSS

    /**
     * Whether a black pearl at [p] can send an arm in direction [d]: the edge out and the next square's
     * edge straight on can both be lines, the next square has no line across, and it is not black.
     */
    fun blackArmOk(g: PearlsGrid, pearls: String, st: IntArray, p: Int, d: Int): Boolean {
        if (!usable(st, g.edge(p, d))) return false
        val q = g.next(p, d)
        if (pearls[q] == BLACK) return false
        if (!usable(st, g.edge(q, d))) return false
        return !isLine(st, g.edge(q, (d + 1) % 4)) && !isLine(st, g.edge(q, (d + 3) % 4))
    }

    /** The black pearl's rule at [p]; null when it settles nothing. */
    fun blackRule(g: PearlsGrid, pearls: String, st: IntArray, p: Int): Found? {
        val lines = IntArrayList()
        val crosses = IntArrayList()
        for (axis in 0 until 2) {
            val a = axis
            val b = axis + 2
            val okA = blackArmOk(g, pearls, st, p, a)
            val okB = blackArmOk(g, pearls, st, p, b)
            val hasA = isLine(st, g.edge(p, a))
            val hasB = isLine(st, g.edge(p, b))
            if (hasA && hasB || !okA && !okB || hasA && !okA || hasB && !okB) return contra(C_BLACK, p)
            val arm = when {
                hasA || !okB -> a
                hasB || !okA -> b
                else -> -1
            }
            if (arm < 0) continue
            val out = g.edge(p, arm)
            val on = g.edge(g.next(p, arm), arm)
            if (st[out] == OPEN) lines.add(out)
            if (st[on] == OPEN) lines.add(on)
            val back = g.edge(p, (arm + 2) % 4)
            if (back >= 0 && st[back] == OPEN) crosses.add(back)
        }
        return if (lines.size + crosses.size == 0) null else Found(K_BLACK, p, lines.toArray(), crosses.toArray())
    }

    /** Whether a white pearl at [p] can go straight along [axis] (0 up-down, 1 left-right). */
    fun whiteAxisOk(g: PearlsGrid, st: IntArray, p: Int, axis: Int): Boolean {
        val a = axis
        val b = axis + 2
        if (!usable(st, g.edge(p, a)) || !usable(st, g.edge(p, b))) return false
        if (isLine(st, g.edge(p, (axis + 1) % 4)) || isLine(st, g.edge(p, (axis + 3) % 4))) return false
        // Both neighbours going straight on would leave it no turn.
        return !(isLine(st, g.edge(g.next(p, a), a)) && isLine(st, g.edge(g.next(p, b), b)))
    }

    /** The white pearl's rule at [p]: its direction ([K_WHITE]), then its turn ([K_WHITE_TURN]); null when neither settles anything. */
    fun whiteRule(g: PearlsGrid, st: IntArray, p: Int): Found? {
        val ok = BooleanArray(2) { whiteAxisOk(g, st, p, it) }
        val has = BooleanArray(2) { isLine(st, g.edge(p, it)) || isLine(st, g.edge(p, it + 2)) }
        if (has[0] && has[1] || !ok[0] && !ok[1] || has[0] && !ok[0] || has[1] && !ok[1]) return contra(C_WHITE, p)
        val axis = when {
            has[0] || !ok[1] -> 0
            has[1] || !ok[0] -> 1
            else -> return null
        }
        val lines = IntArrayList()
        val crosses = IntArrayList()
        for (d in intArrayOf(axis, axis + 2)) {
            val e = g.edge(p, d)
            if (st[e] == OPEN) lines.add(e)
        }
        for (d in intArrayOf((axis + 1) % 4, (axis + 3) % 4)) {
            val e = g.edge(p, d)
            if (e >= 0 && st[e] == OPEN) crosses.add(e)
        }
        if (lines.size + crosses.size > 0) return Found(K_WHITE, p, lines.toArray(), crosses.toArray())
        // Straight through: if one neighbour goes straight on, the other must turn.
        for (d in intArrayOf(axis, axis + 2)) {
            if (!isLine(st, g.edge(g.next(p, d), d))) continue
            val o = (d + 2) % 4
            val other = g.edge(g.next(p, o), o)
            if (other >= 0 && st[other] == OPEN) return Found(K_WHITE_TURN, p, NONE, intArrayOf(other))
        }
        return null
    }

    /** Every local rule at [s], pearl first: the first that settles something, or a contradiction. */
    fun localRule(g: PearlsGrid, pearls: String, st: IntArray, s: Int): Found? {
        val pearl = when (pearls[s]) {
            BLACK -> blackRule(g, pearls, st, s)
            WHITE -> whiteRule(g, st, s)
            else -> null
        }
        return pearl ?: squareRule(g, pearls, st, s)
    }

    // ---- the loop rule ----------------------------------------------------------------------

    /**
     * The stretches of line: each square's stretch (a union-find root, or -1 with no line), its edge
     * count, and how many lines there are in all.
     */
    class Stretches(val root: IntArray, val edgesIn: IntArray, val degree: IntArray, val total: Int)

    fun stretches(g: PearlsGrid, st: IntArray): Stretches {
        val parent = IntArray(g.squares) { it }
        fun find(x: Int): Int {
            var a = x
            while (parent[a] != a) {
                parent[a] = parent[parent[a]]
                a = parent[a]
            }
            return a
        }
        val degree = IntArray(g.squares)
        var total = 0
        for (e in 0 until g.edgeCount) {
            if (st[e] != LINE) continue
            total++
            degree[g.endA[e]]++
            degree[g.endB[e]]++
            val ra = find(g.endA[e])
            val rb = find(g.endB[e])
            if (ra != rb) parent[ra] = rb
        }
        val root = IntArray(g.squares) { if (degree[it] == 0) -1 else find(it) }
        val edgesIn = IntArray(g.squares)
        for (e in 0 until g.edgeCount) if (st[e] == LINE) edgesIn[root[g.endA[e]]]++
        return Stretches(root, edgesIn, degree, total)
    }

    /** Whether a closed stretch [r] (or one about to close) would be the whole loop: every line and every pearl on it. */
    private fun whole(pearls: String, s: Stretches, r: Int): Boolean {
        if (s.edgesIn[r] != s.total) return false
        for (i in pearls.indices) if (pearls[i] != '.' && s.root[i] != r) return false
        return true
    }

    /**
     * The loop rule: every open edge that would close a stretch into a loop leaving something out (in
     * edge order), or the contradiction of a loop already closed that leaves something out; null when
     * neither.
     */
    fun smallLoop(g: PearlsGrid, pearls: String, st: IntArray, firstOnly: Boolean = false): Found? {
        val s = stretches(g, st)
        if (s.total == 0) return null
        // A stretch with no ends is a closed loop.
        val hasEnd = BooleanArray(g.squares)
        for (i in 0 until g.squares) if (s.degree[i] == 1) hasEnd[s.root[i]] = true
        for (i in 0 until g.squares) {
            val r = s.root[i]
            if (r == i && !hasEnd[r] && !whole(pearls, s, r)) return contra(C_SMALL_LOOP, i)
        }
        val crosses = IntArrayList()
        var at = -1
        for (e in 0 until g.edgeCount) {
            if (st[e] != OPEN) continue
            val a = g.endA[e]
            val b = g.endB[e]
            if (s.degree[a] != 1 || s.degree[b] != 1 || s.root[a] != s.root[b]) continue
            if (whole(pearls, s, s.root[a])) continue
            if (at < 0) at = a
            crosses.add(e)
            if (firstOnly) break
        }
        return if (crosses.size == 0) null else Found(K_SMALL_LOOP, at, NONE, crosses.toArray())
    }

    // ---- propagation ------------------------------------------------------------------------

    /** How a propagation ended: settled ([contradiction] null), or the contradiction it reached and after how many rounds. */
    class Outcome(val contradiction: Found?, val rounds: Int)

    /**
     * Applies the local rules (and with [loop] the loop rule) to [st] in place, to a fixpoint or a
     * contradiction. Squares are worked in rounds: round 0 is [seeds] (every square when null), and a
     * round's changes queue the squares they touch for the next. With [maxRounds] it stops quietly
     * after that many rounds (a what-if that found nothing in time). The loop rule reads lines only, so
     * when [st] already keeps it and no line has been drawn since ([linesNew] false and none added),
     * it is not asked again: the outcome is the same, and a what-if that only crosses an edge is far cheaper.
     */
    fun propagate(
        g: PearlsGrid,
        pearls: String,
        st: IntArray,
        loop: Boolean,
        seeds: IntArray? = null,
        maxRounds: Int = Int.MAX_VALUE,
        linesNew: Boolean = true,
    ): Outcome {
        var drawn = linesNew
        val queued = BooleanArray(g.squares)
        var current = IntArrayList()
        for (s in seeds ?: IntArray(g.squares) { it }) if (!queued[s]) { queued[s] = true; current.add(s) }
        var next = IntArrayList()
        var rounds = 0
        fun touch(e: Int) {
            for (end in intArrayOf(g.endA[e], g.endB[e])) {
                if (!queued[end]) { queued[end] = true; next.add(end) }
                for (d in 0 until 4) {
                    val q = g.next(end, d)
                    if (q >= 0 && pearls[q] != '.' && !queued[q]) { queued[q] = true; next.add(q) }
                }
            }
        }
        while (true) {
            while (current.size > 0) {
                if (rounds >= maxRounds) return Outcome(null, rounds)
                for (i in 0 until current.size) queued[current[i]] = false
                for (i in 0 until current.size) {
                    val s = current[i]
                    // Every rule at the square in turn, until none settles anything more there.
                    while (true) {
                        val f = localRule(g, pearls, st, s) ?: break
                        if (f.contradiction) return Outcome(f, rounds)
                        for (e in f.lines) if (st[e] == OPEN) { st[e] = LINE; drawn = true; touch(e) }
                        for (e in f.crosses) if (st[e] == OPEN) { st[e] = CROSS; touch(e) }
                    }
                }
                rounds++
                val t = current
                current = next
                next = t
                next.clear()
            }
            if (!loop || !drawn) return Outcome(null, rounds)
            drawn = false
            val f = smallLoop(g, pearls, st) ?: return Outcome(null, rounds)
            if (f.contradiction) return Outcome(f, rounds)
            if (rounds >= maxRounds) return Outcome(null, rounds)
            for (e in f.crosses) { st[e] = CROSS; touch(e) }
            rounds++
            val t = current
            current = next
            next = t
            next.clear()
        }
    }

    /** How many rounds a what-if may follow: the explanation has to carry them. */
    const val MAX_ROUNDS = 6

    /** A supposition that failed: [edge] set to [assume] ([LINE] or [CROSS]) led to [contradiction] in [rounds] rounds. */
    class WhatIf(val edge: Int, val assume: Int, val contradiction: Found, val rounds: Int)

    /** [edge] set to [assume], followed for at most [maxRounds] rounds: the contradiction it reaches, or null. */
    fun whatIf(g: PearlsGrid, pearls: String, st: IntArray, edge: Int, assume: Int, maxRounds: Int = MAX_ROUNDS): WhatIf? {
        val trial = st.copyOf()
        trial[edge] = assume
        val out = propagate(g, pearls, trial, loop = true, seeds = seedsOf(g, pearls, edge), maxRounds = maxRounds, linesNew = assume == LINE)
        return out.contradiction?.let { WhatIf(edge, assume, it, out.rounds) }
    }

    /** The squares whose rules read edge [e]: its two ends and the pearls beside them. */
    fun seedsOf(g: PearlsGrid, pearls: String, e: Int): IntArray {
        val seeds = IntArrayList()
        for (end in intArrayOf(g.endA[e], g.endB[e])) {
            seeds.add(end)
            for (d in 0 until 4) {
                val q = g.next(end, d)
                if (q >= 0 && pearls[q] != '.') seeds.add(q)
            }
        }
        return seeds.toArray()
    }

    /**
     * The what-if with the fewest rounds over every open edge (a line tried before a cross), the first in
     * edge order on a tie; with [first] the first found at all. At most [cap] rounds. Null when none settles anything.
     */
    fun bestWhatIf(g: PearlsGrid, pearls: String, st: IntArray, first: Boolean = false, cap: Int = MAX_ROUNDS): WhatIf? {
        var best: WhatIf? = null
        for (e in 0 until g.edgeCount) {
            if (st[e] != OPEN) continue
            for (assume in intArrayOf(LINE, CROSS)) {
                // Strictly fewer rounds than the best so far, so a tie keeps the earlier edge.
                val w = whatIf(g, pearls, st, e, assume, best?.rounds ?: cap) ?: continue
                if (first || w.rounds == 0) return w
                best = w
            }
        }
        return best
    }

    // ---- the ladder ---------------------------------------------------------------------------

    /** The rungs a board may need: the local rules alone, the loop rule as well, or a what-if as well. */
    const val LOCAL = 0
    const val LOOP = 1
    const val WHAT_IF = 2

    /**
     * Everything the rungs up to [depth] settle from [start], to a fixpoint: the edges reached, or null on a
     * contradiction.
     */
    fun settle(n: Int, pearls: String, start: IntArray, depth: Int): IntArray? {
        val g = PearlsGrid.of(n)
        val st = start.copyOf()
        if (propagate(g, pearls, st, loop = depth >= LOOP).contradiction != null) return null
        if (depth < WHAT_IF) return st
        while (!finished(g, pearls, st)) {
            val w = bestWhatIf(g, pearls, st, first = true) ?: return st
            st[w.edge] = if (w.assume == LINE) CROSS else LINE
            if (propagate(g, pearls, st, loop = true, seeds = seedsOf(g, pearls, w.edge)).contradiction != null) return null
        }
        return st
    }

    /** Whether the lines in [st] are already the whole answer: one closed loop keeping every rule. */
    fun finished(g: PearlsGrid, pearls: String, st: IntArray): Boolean {
        val marks = CharArray(g.edgeCount) { if (st[it] == LINE) LINE_MARK else OPEN_MARK }.concatToString()
        return isSolved(g.n, pearls, marks)
    }

    /** The deepest rung the board needs from blank ([LOCAL], [LOOP] or [WHAT_IF]), or null when the ladder does not finish it. */
    fun depth(n: Int, pearls: String): Int? {
        val g = PearlsGrid.of(n)
        val blank = IntArray(g.edgeCount)
        for (d in LOCAL..WHAT_IF) {
            val st = settle(n, pearls, blank, d) ?: return null
            if (finished(g, pearls, st)) return d
        }
        return null
    }

    fun depth(s: PearlsState): Int? = depth(s.size, s.pearls)

    /** The player's edges as the rules read them. */
    fun stateOf(edges: String): IntArray = IntArray(edges.length) {
        when (edges[it]) {
            LINE_MARK -> LINE
            CROSS_MARK -> CROSS
            else -> OPEN
        }
    }

    // ---- the win check ------------------------------------------------------------------------

    /**
     * The rules, read off the lines alone: every square has no line or two, every pearl has two, the
     * lines make one closed loop, a black pearl turns with both neighbours on the loop going straight,
     * and a white pearl goes straight with a turn on at least one neighbour.
     */
    fun isSolved(n: Int, pearls: String, edges: String): Boolean {
        val g = PearlsGrid.of(n)
        val nb = IntArray(g.squares * 2) { -1 }
        val degree = IntArray(g.squares)
        var total = 0
        for (e in 0 until g.edgeCount) {
            if (edges[e] != LINE_MARK) continue
            total++
            for ((s, o) in listOf(g.endA[e] to g.endB[e], g.endB[e] to g.endA[e])) {
                if (degree[s] >= 2) return false
                nb[s * 2 + degree[s]] = o
                degree[s]++
            }
        }
        if (total == 0) return false
        for (s in 0 until g.squares) {
            if (degree[s] == 1) return false
            if (pearls[s] != '.' && degree[s] != 2) return false
        }
        // One loop: walk it from any square on it and count the steps.
        val start = (0 until g.squares).first { degree[it] == 2 }
        var prev = start
        var at = nb[start * 2]
        var steps = 1
        while (at != start) {
            val nx = if (nb[at * 2] == prev) nb[at * 2 + 1] else nb[at * 2]
            prev = at
            at = nx
            steps++
        }
        if (steps != total) return false
        fun turns(s: Int): Boolean {
            val a = nb[s * 2]
            val b = nb[s * 2 + 1]
            return a / n != b / n && a % n != b % n
        }
        for (s in 0 until g.squares) {
            when (pearls[s]) {
                BLACK -> if (!turns(s) || turns(nb[s * 2]) || turns(nb[s * 2 + 1])) return false
                WHITE -> if (turns(s) || !turns(nb[s * 2]) && !turns(nb[s * 2 + 1])) return false
            }
        }
        return true
    }

    // ---- the exhaustive count -----------------------------------------------------------------

    /**
     * How many loops keep every rule, with no budget: the local and loop rules to a fixpoint (sound, so
     * they lose no answer), then a branch on an open edge (one beside the end of a stretch first), line
     * and then cross, until two answers are found or every branch is closed. A finished branch is checked
     * by [isSolved], not by the rules that led to it. Only [Verdict.ExactlyOne] carries the loop.
     */
    fun solutions(n: Int, pearls: String): Verdict<String> {
        val g = PearlsGrid.of(n)
        var found = 0
        var answer = ""
        fun walk(st: IntArray) {
            if (found > 1) return
            if (propagate(g, pearls, st, loop = true).contradiction != null) return
            if (!connected(g, pearls, st)) return
            val marks = CharArray(g.edgeCount) { if (st[it] == LINE) LINE_MARK else OPEN_MARK }.concatToString()
            if (isSolved(n, pearls, marks)) {
                found++
                if (found == 1) answer = CharArray(g.edgeCount) { if (st[it] == LINE) '1' else '0' }.concatToString()
                return
            }
            val s = stretches(g, st)
            var pick = -1
            for (e in 0 until g.edgeCount) {
                if (st[e] != OPEN) continue
                if (pick < 0) pick = e
                if (s.degree[g.endA[e]] == 1 || s.degree[g.endB[e]] == 1) {
                    pick = e
                    break
                }
            }
            if (pick < 0) return
            for (v in intArrayOf(LINE, CROSS)) {
                val next = st.copyOf()
                next[pick] = v
                walk(next)
                if (found > 1) return
            }
        }
        // The root is first taken as far as the whole ladder goes, what-ifs included: every step of it is sound, so no
        // answer is lost, and it spares the branches a short what-if would have closed (one board's count fell from half a
        // second to a few milliseconds).
        walk(settle(n, pearls, IntArray(g.edgeCount), WHAT_IF) ?: return Verdict.None)
        return Verdict.of(found) { answer }
    }

    /**
     * Whether every pearl and every square with a line can still meet: one piece of the board joined by edges
     * not crossed. A loop is one piece, so a board cut in two by crosses has no answer (a prune for [solutions]).
     */
    private fun connected(g: PearlsGrid, pearls: String, st: IntArray): Boolean {
        val need = BooleanArray(g.squares)
        var start = -1
        for (e in 0 until g.edgeCount) if (st[e] == LINE) { need[g.endA[e]] = true; need[g.endB[e]] = true }
        for (s in 0 until g.squares) {
            if (pearls[s] != '.') need[s] = true
            if (need[s] && start < 0) start = s
        }
        if (start < 0) return true
        val seen = BooleanArray(g.squares)
        val queue = IntArray(g.squares)
        var lo = 0
        var hi = 0
        queue[hi++] = start
        seen[start] = true
        while (lo < hi) {
            val s = queue[lo++]
            for (d in 0 until 4) {
                val e = g.edge(s, d)
                if (e < 0 || st[e] == CROSS) continue
                val q = g.next(s, d)
                if (!seen[q]) { seen[q] = true; queue[hi++] = q }
            }
        }
        for (s in 0 until g.squares) if (need[s] && !seen[s]) return false
        return true
    }

    // ---- generation ---------------------------------------------------------------------------

    /** Side, the share of the squares the loop visits (per cent, a range), and the deepest rung a board must need. */
    class Spec(val side: Int, val fillLow: Int, val fillHigh: Int, val needs: Int)

    fun specFor(difficulty: Difficulty): Spec = when (difficulty) {
        Difficulty.STANDARD -> Spec(6, 60, 90, LOCAL)
        Difficulty.HARD -> Spec(8, 60, 90, LOOP)
        Difficulty.EXPERT -> Spec(10, 60, 90, WHAT_IF)
    }

    /** Draws tried per seed before the generator gives up and falls back. */
    const val ATTEMPTS = 200

    /**
     * A board for [seed] that its tier's rungs finish, needing exactly the deepest, and the count proves,
     * or null when none of [ATTEMPTS] draws was. Draw `a` uses `Rng(seed + a)`, so a board that needed
     * three draws still depends on its seed alone.
     */
    fun generate(seed: Long, difficulty: Difficulty): PearlsState = generateVerified(seed, difficulty) ?: lastResort(difficulty)

    fun generateVerified(seed: Long, difficulty: Difficulty): PearlsState? {
        for (a in 0 until ATTEMPTS) attempt(seed, difficulty, a)?.let { return it }
        return null
    }

    /** Draw number [a] for [seed], if it is accepted. Public to tests so they can count the redraws. */
    fun attempt(seed: Long, difficulty: Difficulty, a: Int): PearlsState? {
        val spec = specFor(difficulty)
        val rng = Rng(seed + a)
        val loop = randomLoop(rng, spec.side, spec.fillLow, spec.fillHigh) ?: return null
        val pearls = takeBack(rng, spec.side, implied(spec.side, loop), spec.needs) ?: return null
        val s = PearlsState(spec.side, pearls, loop)
        return s.takeIf { accepts(it, spec) }
    }

    /** Whether [s] may ship at [spec]: the ladder finishes it needing exactly the tier's rung, and it has one answer, [PearlsState.loop]. */
    fun accepts(s: PearlsState, spec: Spec): Boolean {
        if (s.size != spec.side || depth(s) != spec.needs) return false
        val verdict = solutions(s.size, s.pearls)
        return verdict is Verdict.ExactlyOne && verdict.value == s.loop
    }

    /**
     * A random simple loop as a `0`/`1` edge string: the boundary of a region of the `(n-1) x (n-1)`
     * faces between square middles, grown from one random face. A face joins only if the boundary stays
     * one simple closed curve. Each step weighs every face that could join by the pearls its loop would
     * allow (squares visited less [bare] ones) and takes one of the best at random (on [GREEDY_PERCENT]
     * of steps; otherwise any). A loop where nearly every square can carry a pearl is one the pearls can
     * pin down: grown at random, 37 of 39 six-by-six loops with every pearl they allow still had two
     * answers. Growth stops once the loop visits [low]-[high] per cent of the squares (drawn), or when
     * nothing can join (kept if it already visits [low] per cent); null otherwise.
     *
     * Every candidate is walked in face order and scored the same on every platform; no hash order.
     */
    fun randomLoop(rng: Rng, n: Int, low: Int, high: Int): String? {
        val fw = n - 1
        val faces = fw * fw
        val inside = BooleanArray(faces)
        inside[rng.nextInt(faces)] = true
        val target = n * n * rng.nextInt(low, high) / 100
        val shape = LoopShape(n)
        shape.trace(inside)
        var length = shape.length
        while (length < target) {
            val best = IntArrayList()
            var bestScore = Int.MAX_VALUE
            for (f in 0 until faces) {
                if (inside[f]) continue
                val r = f / fw
                val c = f % fw
                if (!(r > 0 && inside[f - fw] || r < fw - 1 && inside[f + fw] || c > 0 && inside[f - 1] || c < fw - 1 && inside[f + 1])) continue
                inside[f] = true
                val simple = shape.trace(inside)
                inside[f] = false
                if (!simple) continue
                // The most squares that could carry a pearl.
                val score = if (rng.nextInt(100) < GREEDY_PERCENT) -shape.pearlSquares() else 0
                if (score < bestScore) {
                    bestScore = score
                    best.clear()
                }
                if (score == bestScore) best.add(f)
            }
            // Nowhere left to grow: good enough if it already visits the share's low end.
            if (best.size == 0) break
            inside[best[rng.nextInt(best.size)]] = true
            shape.trace(inside)
            length = shape.length
        }
        if (length < target && length < n * n * low / 100) return null
        return CharArray(shape.on.size) { if (shape.on[it]) '1' else '0' }.concatToString()
    }

    /**
     * The boundary of a face region, worked out in place with nothing allocated per candidate: [trace] fills
     * [on] and says whether it is one simple closed curve, [length] is its edge count, and [pearlSquares] how
     * many of its squares could carry a pearl ([implied]'s rule).
     */
    private class LoopShape(val n: Int) {
        val g = PearlsGrid.of(n)
        val on = BooleanArray(g.edgeCount)
        private val nb = IntArray(g.squares * 2)
        private val degree = IntArray(g.squares)
        var length = 0

        fun trace(inside: BooleanArray): Boolean {
            val fw = n - 1
            fun face(r: Int, c: Int) = r in 0 until fw && c in 0 until fw && inside[r * fw + c]
            degree.fill(0)
            length = 0
            for (r in 0 until n) for (c in 0 until n - 1) on[r * (n - 1) + c] = face(r - 1, c) != face(r, c)
            for (r in 0 until n - 1) for (c in 0 until n) on[g.horizontal + r * n + c] = face(r, c - 1) != face(r, c)
            var simple = true
            for (e in 0 until g.edgeCount) {
                if (!on[e]) continue
                length++
                val a = g.endA[e]
                val b = g.endB[e]
                if (degree[a] == 2 || degree[b] == 2) {
                    simple = false
                    continue
                }
                nb[a * 2 + degree[a]++] = b
                nb[b * 2 + degree[b]++] = a
            }
            if (!simple || length == 0) return false
            var start = -1
            for (s in 0 until g.squares) {
                if (degree[s] == 1) return false
                if (degree[s] == 2 && start < 0) start = s
            }
            var prev = start
            var at = nb[start * 2]
            var steps = 1
            while (at != start) {
                val next = if (nb[at * 2] == prev) nb[at * 2 + 1] else nb[at * 2]
                prev = at
                at = next
                steps++
            }
            return steps == length
        }

        private fun turns(s: Int): Boolean {
            val a = nb[s * 2]
            val b = nb[s * 2 + 1]
            return a / n != b / n && a % n != b % n
        }

        fun pearlSquares(): Int {
            var count = 0
            for (s in 0 until g.squares) {
                if (degree[s] != 2) continue
                val t = turns(s)
                val ta = turns(nb[s * 2])
                val tb = turns(nb[s * 2 + 1])
                if (t && !ta && !tb || !t && (ta || tb)) count++
            }
            return count
        }
    }

    /** How often a growth step is greedy (the face that leaves the most pearls); the rest take any face that keeps the loop simple. */
    private const val GREEDY_PERCENT = 95

    /** Every pearl [loop] allows: black on a turn whose neighbours go straight, white on a straight with a turn beside it. */
    fun implied(n: Int, loop: String): String {
        val g = PearlsGrid.of(n)
        val nb = IntArray(g.squares * 2) { -1 }
        val degree = IntArray(g.squares)
        for (e in 0 until g.edgeCount) {
            if (loop[e] != '1') continue
            nb[g.endA[e] * 2 + degree[g.endA[e]]++] = g.endB[e]
            nb[g.endB[e] * 2 + degree[g.endB[e]]++] = g.endA[e]
        }
        fun turns(s: Int): Boolean {
            val a = nb[s * 2]
            val b = nb[s * 2 + 1]
            return a / n != b / n && a % n != b % n
        }
        return CharArray(g.squares) { s ->
            when {
                degree[s] != 2 -> '.'
                turns(s) && !turns(nb[s * 2]) && !turns(nb[s * 2 + 1]) -> BLACK
                !turns(s) && (turns(nb[s * 2]) || turns(nb[s * 2 + 1])) -> WHITE
                else -> '.'
            }
        }.concatToString()
    }

    /**
     * Pearls taken back from [all] in a random order while the rungs up to [needs] still finish the
     * board, so none is left that the rest already imply. On Expert the order is walked twice: first with
     * the local and loop rules alone (cheap), then the pearls still left with the what-if as well; the
     * result is still a board every pearl of which the full ladder needs. Null when [all] itself is not
     * finished.
     */
    fun takeBack(rng: Rng, n: Int, all: String, needs: Int): String? {
        val g = PearlsGrid.of(n)
        val blank = IntArray(g.edgeCount)
        fun finishes(p: String, d: Int) = settle(n, p, blank, d)?.let { finished(g, p, it) } == true
        val order = rng.shuffled(all.indices.filter { all[it] != '.' })
        val pearls = all.toCharArray()
        val passes = if (needs == WHAT_IF) listOf(LOOP, WHAT_IF) else listOf(needs)
        if (!finishes(all, needs)) return null
        for (d in passes) {
            if (!finishes(pearls.concatToString(), d)) continue
            for (p in order) {
                if (pearls[p] == '.') continue
                val kept = pearls[p]
                pearls[p] = '.'
                if (!finishes(pearls.concatToString(), d)) pearls[p] = kept
            }
        }
        return pearls.concatToString()
    }

    /**
     * Only when none of [ATTEMPTS] draws was accepted, which a year of daily seeds on every tier never
     * reaches (`FallbackTest`). A fixed board per tier (what seed 424242 drew), which `PearlsRulesTest`
     * proves is accepted at its tier: one loop, the tier's rung needed exactly.
     */
    fun lastResort(difficulty: Difficulty): PearlsState = when (difficulty) {
        Difficulty.STANDARD -> PearlsState(
            6, "b.......w.......ww..b....b.w........",
            "110000000110000001100111000001101000101011011011010001000011",
        )
        Difficulty.HARD -> PearlsState(
            8, ".b..w..............bw....w..w...w......b..b....w........b....bw.",
            "0111100000010001100001100000001011100111000000000110001101000100" +
                "010010000001100010111000100000011010010110100101",
        )
        Difficulty.EXPERT -> PearlsState(
            10,
            "..w...b...w....w...w...b...............w.b...w.b.........w....b.b.....w..wb..b...........w.w.....w..",
            "111010111000000000011001000000010000011100011111001000110011000000011011001000000111100111100111" +
                "100110011110011100100001110001000110001101000001101100101100010010111010011000101001",
        )
    }
}

/** A growable list of ints with no boxing; walked by index, so its order is its insertion order everywhere. */
internal class IntArrayList {
    private var data = IntArray(8)
    var size = 0
        private set

    fun add(v: Int) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = v
    }

    operator fun get(i: Int) = data[i]

    fun clear() {
        size = 0
    }

    fun toArray(): IntArray = data.copyOf(size)
}
