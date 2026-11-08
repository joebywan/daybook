package com.joebywan.daybook.core

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Where everything is on an `n x n` lattice of points joined to their neighbours: each point's edge and neighbour in
 * each direction (0 up, 1 right, 2 down, 3 left; -1 off the lattice) and each edge's two points. Pearls' points are the
 * middles of its squares (a lattice of the board's side); Loop's are the dots at the squares' corners (one more than
 * the board's side). Horizontal edge `(r, c)-(r, c+1)` is `r * (n - 1) + c`, then vertical edge `(r, c)-(r+1, c)` is
 * `n * (n - 1) + r * n + c`. Cached per size (a probe-only lookup: nothing iterates the cache).
 */
internal class LoopGrid private constructor(val n: Int) {
    val nodes = n * n
    val horizontal = n * (n - 1)
    val edgeCount = 2 * n * (n - 1)
    private val edgeAt = IntArray(nodes * 4) { -1 }
    private val step = IntArray(nodes * 4) { -1 }
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

    /** The edge between neighbouring points [a] and [b], or -1 when they are not neighbours. */
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

        private val cache = arrayOfNulls<LoopGrid>(32)

        fun of(n: Int): LoopGrid = cache[n] ?: LoopGrid(n).also { cache[n] = it }
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

/**
 * One rule applied at unit [at] (a point, a square, a pearl: whatever the puzzle's rules are read at): the edges it
 * makes lines and crosses, or a contradiction ([kind] at or above [LoopEdges.CONTRADICTION]).
 */
internal class LoopFound(val kind: Int, val at: Int, val lines: IntArray, val crosses: IntArray) {
    val contradiction: Boolean get() = kind >= LoopEdges.CONTRADICTION
}

/**
 * A loop puzzle's own rules, as the shared ladder ([LoopEdges.propagate], [LoopEdges.settle], [LoopEdges.solutions])
 * reads them. Units are whatever a local rule is read at, numbered `0 until units`.
 */
internal interface LoopRules {
    val g: LoopGrid
    val units: Int

    /** The first local rule at unit [u] that settles something or breaks, or null. */
    fun rule(st: IntArray, u: Int): LoopFound?

    /** Hands [add] every unit whose rule reads edge [e], in a fixed order. */
    fun touch(e: Int, add: (Int) -> Unit)

    /**
     * Whether stretch [root] of [s] (the stretches of [st]), closed (with [closing] drawn, or -1 when it is closed already), would be the whole
     * answer, so that the loop rule must let it close.
     */
    fun whole(st: IntArray, s: LoopEdges.Stretches, root: Int, closing: Int): Boolean

    /** Whether the lines in [st] are already the whole answer. */
    fun finished(st: IntArray): Boolean

    /** Whether point [p] must be on the loop whatever else is drawn (a pearl): a prune for [LoopEdges.solutions]. */
    fun mustVisit(p: Int): Boolean = false
}

/**
 * What every loop puzzle shares: one closed loop along the edges of a [LoopGrid], drawn as lines with crosses as notes.
 * Lifted from Pearls (whose loop runs through square middles) so Loop (whose loop runs along the squares' sides) reads
 * the same edges, the same loop rule and the same ladder, never a copy. Nothing here touches the screen, the platform
 * or a hash container.
 *
 * ## The shared ladder
 * Every rule reads edge states ([OPEN], [LINE], [CROSS]) and only ever adds lines and crosses: a puzzle's local rules
 * ([LoopRules.rule]), then [smallLoop] (an edge closing a loop that is not the whole answer is crossed), then [whatIf]
 * (suppose an edge, follow the first two for at most [MAX_ROUNDS] rounds, and something breaks). [settle] takes the
 * rungs up to a depth to a fixpoint; [depth] is the deepest a board needs from blank.
 */
internal object LoopEdges {

    const val OPEN_MARK = '.'
    const val LINE_MARK = '-'
    const val CROSS_MARK = 'x'

    const val OPEN = 0
    const val LINE = 1
    const val CROSS = 2

    /** Kinds at or above this are contradictions. */
    const val CONTRADICTION = 10

    // The point rules' and the loop rule's kinds (Pearls' numbers, which order its hints).
    const val K_ONE_WAY = 3
    const val K_TWO_WAYS = 4
    const val K_DEAD_END = 5
    const val K_FULL = 6
    const val K_SMALL_LOOP = 7

    const val C_THREE = 10 // a point with three or more lines
    const val C_DEAD_LINE = 11 // a line into a point with no way on
    const val C_SHUT = 12 // a point that must be passed, left fewer than two ways
    const val C_SMALL_LOOP = 15 // a closed loop that leaves something out

    /** The rungs a board may need: local rules alone, the loop rule as well, or a what-if as well. */
    const val LOCAL = 0
    const val LOOP = 1
    const val WHAT_IF = 2

    /** How many rounds a what-if may follow: the explanation has to carry them. */
    const val MAX_ROUNDS = 6

    private val NONE = IntArray(0)

    /** The player's edges as the rules read them. */
    fun stateOf(edges: String): IntArray = IntArray(edges.length) {
        when (edges[it]) {
            LINE_MARK -> LINE
            CROSS_MARK -> CROSS
            else -> OPEN
        }
    }

    /**
     * The edges as a teacher reads them: the player's lines and crosses, and every open edge of a point that already has
     * two lines read as crossed (so a hint never asks for that tidying).
     */
    fun view(g: LoopGrid, edges: String): IntArray {
        val st = stateOf(edges)
        val lines = IntArray(g.nodes)
        for (e in 0 until g.edgeCount) if (st[e] == LINE) {
            lines[g.endA[e]]++
            lines[g.endB[e]]++
        }
        for (e in 0 until g.edgeCount) {
            if (st[e] == OPEN && (lines[g.endA[e]] >= 2 || lines[g.endB[e]] >= 2)) st[e] = CROSS
        }
        return st
    }

    // ---- the player's marks ---------------------------------------------------------------------

    /** [edges] with [mark] on every edge of [indices] that is not already it, and how many changed. */
    fun painted(edges: String, indices: Collection<Int>, mark: Char): Pair<String, Int> {
        val next = edges.toCharArray()
        var changed = 0
        for (i in indices) {
            if (next[i] != mark) {
                next[i] = mark
                changed++
            }
        }
        return (if (changed == 0) edges else next.concatToString()) to changed
    }

    /** A tap on edge [e]: an open edge is crossed, and a cross or a line is rubbed out. A tap never draws a line. */
    fun tapped(edges: String, e: Int): Pair<String, Int> =
        painted(edges, listOf(e), if (edges[e] == OPEN_MARK) CROSS_MARK else OPEN_MARK)

    /**
     * A drag through points [path] (each a neighbour of the one before). Its first edge decides what the whole drag does,
     * as a pen does: over a line it rubs lines out, anywhere else it draws them (over crosses too).
     */
    fun dragged(g: LoopGrid, edges: String, path: List<Int>): Pair<String, Int> {
        val crossed = (1 until path.size).mapNotNull { g.between(path[it - 1], path[it]).takeIf { e -> e >= 0 } }
        if (crossed.isEmpty()) return edges to 0
        return if (edges[crossed[0]] == LINE_MARK) {
            painted(edges, crossed.filter { edges[it] == LINE_MARK }, OPEN_MARK)
        } else {
            painted(edges, crossed, LINE_MARK)
        }
    }

    // ---- input geometry (pure, tested) ----------------------------------------------------------

    /** How near the middle of an edge a tap must land to name it, in lattice steps. */
    const val TAP_REACH = 0.42f

    /**
     * The edge of an `n x n` lattice whose middle is nearest ([x], [y]), with point `(r, c)` at `(c + 0.5, r + 0.5)` (the
     * middle of square `(r, c)` of a board one step a square), if within [TAP_REACH]; else -1. A tap on a point names no
     * edge (all four are half a step away).
     */
    fun edgeNear(n: Int, x: Float, y: Float): Int {
        var best = -1
        var bestD = TAP_REACH * TAP_REACH
        // Horizontal edge (r, c)-(r, c+1) has its middle at (c + 1, r + 0.5); vertical (r, c)-(r+1, c) at (c + 0.5, r + 1).
        val r = floor(y).toInt()
        val k = x.roundToInt()
        if (r in 0 until n && k in 1 until n) {
            val d = (x - k) * (x - k) + (y - r - 0.5f) * (y - r - 0.5f)
            if (d < bestD) { bestD = d; best = r * (n - 1) + (k - 1) }
        }
        val c = floor(x).toInt()
        val j = y.roundToInt()
        if (c in 0 until n && j in 1 until n) {
            val d = (y - j) * (y - j) + (x - c - 0.5f) * (x - c - 0.5f)
            if (d < bestD) { best = n * (n - 1) + (j - 1) * n + c }
        }
        return best
    }

    // ---- the point rules ------------------------------------------------------------------------

    /**
     * The point rules at [s]: two lines take no more ([K_FULL], skipped when [full] is off); one line and one way on
     * continues ([K_ONE_WAY]); a point that [mustPass] with two ways left takes both ([K_TWO_WAYS]); any other point with
     * one way in is a dead end ([K_DEAD_END]). Null when they settle nothing.
     */
    fun pointRule(g: LoopGrid, st: IntArray, s: Int, mustPass: Boolean, full: Boolean = true): LoopFound? {
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
        return when {
            lines > 2 -> LoopFound(C_THREE, s, NONE, NONE)
            lines == 2 -> if (open > 0 && full) LoopFound(K_FULL, s, NONE, opens(g, st, s)) else null
            lines == 1 -> when (open) {
                0 -> LoopFound(C_DEAD_LINE, s, NONE, NONE)
                1 -> LoopFound(K_ONE_WAY, s, opens(g, st, s), NONE)
                else -> null
            }
            mustPass && open < 2 -> LoopFound(C_SHUT, s, NONE, NONE)
            mustPass && open == 2 -> LoopFound(K_TWO_WAYS, s, opens(g, st, s), NONE)
            !mustPass && open == 1 -> LoopFound(K_DEAD_END, s, NONE, opens(g, st, s))
            else -> null
        }
    }

    /** The open edges at point [s], in direction order. */
    fun opens(g: LoopGrid, st: IntArray, s: Int): IntArray {
        val out = IntArrayList()
        for (d in 0 until 4) {
            val e = g.edge(s, d)
            if (e >= 0 && st[e] == OPEN) out.add(e)
        }
        return out.toArray()
    }

    // ---- the loop rule ----------------------------------------------------------------------------

    /**
     * The stretches of line: each point's stretch (a union-find root, or -1 with no line), its edge count, each point's
     * line count, and how many lines there are in all.
     */
    class Stretches(val root: IntArray, val edgesIn: IntArray, val degree: IntArray, val total: Int)

    fun stretches(g: LoopGrid, st: IntArray): Stretches {
        val parent = IntArray(g.nodes) { it }
        fun find(x: Int): Int {
            var a = x
            while (parent[a] != a) {
                parent[a] = parent[parent[a]]
                a = parent[a]
            }
            return a
        }
        val degree = IntArray(g.nodes)
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
        val root = IntArray(g.nodes) { if (degree[it] == 0) -1 else find(it) }
        val edgesIn = IntArray(g.nodes)
        for (e in 0 until g.edgeCount) if (st[e] == LINE) edgesIn[root[g.endA[e]]]++
        return Stretches(root, edgesIn, degree, total)
    }

    /**
     * The loop rule: every open edge that would close a stretch into a loop that is not the whole answer (in edge order;
     * the first alone with [firstOnly]), or the contradiction of a loop already closed that is not; null when neither.
     */
    fun smallLoop(r: LoopRules, st: IntArray, firstOnly: Boolean = false): LoopFound? {
        val g = r.g
        val s = stretches(g, st)
        if (s.total == 0) return null
        // A stretch with no ends is a closed loop.
        val hasEnd = BooleanArray(g.nodes)
        for (i in 0 until g.nodes) if (s.degree[i] == 1) hasEnd[s.root[i]] = true
        for (i in 0 until g.nodes) {
            val root = s.root[i]
            if (root == i && !hasEnd[root] && !r.whole(st, s, root, -1)) return LoopFound(C_SMALL_LOOP, i, NONE, NONE)
        }
        val crosses = IntArrayList()
        var at = -1
        for (e in 0 until g.edgeCount) {
            if (st[e] != OPEN) continue
            val a = g.endA[e]
            val b = g.endB[e]
            if (s.degree[a] != 1 || s.degree[b] != 1 || s.root[a] != s.root[b]) continue
            if (r.whole(st, s, s.root[a], e)) continue
            if (at < 0) at = a
            crosses.add(e)
            if (firstOnly) break
        }
        return if (crosses.size == 0) null else LoopFound(K_SMALL_LOOP, at, NONE, crosses.toArray())
    }

    /**
     * Each point's two loop neighbours (`nb[2p]`, `nb[2p + 1]`, -1 for none) when the edges [isLine] says are lines make
     * exactly one closed loop (every point has none or two, at least one line, and walking it meets them all); else null.
     */
    fun oneLoop(g: LoopGrid, isLine: (Int) -> Boolean): IntArray? {
        val nb = IntArray(g.nodes * 2) { -1 }
        val degree = IntArray(g.nodes)
        var total = 0
        for (e in 0 until g.edgeCount) {
            if (!isLine(e)) continue
            total++
            for ((s, o) in listOf(g.endA[e] to g.endB[e], g.endB[e] to g.endA[e])) {
                if (degree[s] >= 2) return null
                nb[s * 2 + degree[s]] = o
                degree[s]++
            }
        }
        if (total == 0) return null
        for (s in 0 until g.nodes) if (degree[s] == 1) return null
        // One loop: walk it from any point on it and count the steps.
        val start = (0 until g.nodes).first { degree[it] == 2 }
        var prev = start
        var at = nb[start * 2]
        var steps = 1
        while (at != start) {
            val nx = if (nb[at * 2] == prev) nb[at * 2 + 1] else nb[at * 2]
            prev = at
            at = nx
            steps++
        }
        return if (steps == total) nb else null
    }

    // ---- propagation ------------------------------------------------------------------------------

    /** How a propagation ended: settled ([contradiction] null), or the contradiction it reached and after how many rounds. */
    class Outcome(val contradiction: LoopFound?, val rounds: Int)

    /**
     * Applies the local rules (and with [loop] the loop rule) to [st] in place, to a fixpoint or a contradiction. Units
     * are worked in rounds: round 0 is [seeds] (every unit when null), and a round's changes queue the units they touch
     * for the next. With [maxRounds] it stops quietly after that many rounds (a what-if that found nothing in time). The
     * loop rule reads lines only, so when [st] already keeps it and no line has been drawn since ([linesNew] false and
     * none added), it is not asked again: the outcome is the same, and a what-if that only crosses an edge is far cheaper.
     */
    fun propagate(
        r: LoopRules,
        st: IntArray,
        loop: Boolean,
        seeds: IntArray? = null,
        maxRounds: Int = Int.MAX_VALUE,
        linesNew: Boolean = true,
    ): Outcome {
        var drawn = linesNew
        val queued = BooleanArray(r.units)
        var current = IntArrayList()
        for (s in seeds ?: IntArray(r.units) { it }) if (!queued[s]) { queued[s] = true; current.add(s) }
        var next = IntArrayList()
        var rounds = 0
        val add: (Int) -> Unit = { u -> if (!queued[u]) { queued[u] = true; next.add(u) } }
        while (true) {
            while (current.size > 0) {
                if (rounds >= maxRounds) return Outcome(null, rounds)
                for (i in 0 until current.size) queued[current[i]] = false
                for (i in 0 until current.size) {
                    val s = current[i]
                    // Every rule at the unit in turn, until none settles anything more there.
                    while (true) {
                        val f = r.rule(st, s) ?: break
                        if (f.contradiction) return Outcome(f, rounds)
                        for (e in f.lines) if (st[e] == OPEN) { st[e] = LINE; drawn = true; r.touch(e, add) }
                        for (e in f.crosses) if (st[e] == OPEN) { st[e] = CROSS; r.touch(e, add) }
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
            val f = smallLoop(r, st) ?: return Outcome(null, rounds)
            if (f.contradiction) return Outcome(f, rounds)
            if (rounds >= maxRounds) return Outcome(null, rounds)
            for (e in f.crosses) { st[e] = CROSS; r.touch(e, add) }
            rounds++
            val t = current
            current = next
            next = t
            next.clear()
        }
    }

    /** A supposition that failed: [edge] set to [assume] ([LINE] or [CROSS]) led to [contradiction] in [rounds] rounds. */
    class WhatIf(val edge: Int, val assume: Int, val contradiction: LoopFound, val rounds: Int)

    /** The units whose rules read edge [e], in [LoopRules.touch]'s order. */
    fun seedsOf(r: LoopRules, e: Int): IntArray {
        val seeds = IntArrayList()
        r.touch(e) { seeds.add(it) }
        return seeds.toArray()
    }

    /** [edge] set to [assume], followed for at most [maxRounds] rounds: the contradiction it reaches, or null. */
    fun whatIf(r: LoopRules, st: IntArray, edge: Int, assume: Int, maxRounds: Int = MAX_ROUNDS): WhatIf? {
        val trial = st.copyOf()
        trial[edge] = assume
        val out = propagate(r, trial, loop = true, seeds = seedsOf(r, edge), maxRounds = maxRounds, linesNew = assume == LINE)
        return out.contradiction?.let { WhatIf(edge, assume, it, out.rounds) }
    }

    /**
     * The what-if with the fewest rounds over every open edge (a line tried before a cross), the first in edge order on a
     * tie; with [first] the first found at all. At most [cap] rounds. Null when none settles anything.
     */
    fun bestWhatIf(r: LoopRules, st: IntArray, first: Boolean = false, cap: Int = MAX_ROUNDS): WhatIf? {
        var best: WhatIf? = null
        for (e in 0 until r.g.edgeCount) {
            if (st[e] != OPEN) continue
            for (assume in intArrayOf(LINE, CROSS)) {
                // Strictly fewer rounds than the best so far, so a tie keeps the earlier edge.
                val w = whatIf(r, st, e, assume, best?.rounds ?: cap) ?: continue
                if (first || w.rounds == 0) return w
                best = w
            }
        }
        return best
    }

    // ---- the ladder -------------------------------------------------------------------------------

    /** Everything the rungs up to [depth] settle from [start], to a fixpoint: the edges reached, or null on a contradiction. */
    fun settle(r: LoopRules, start: IntArray, depth: Int): IntArray? {
        val st = start.copyOf()
        if (propagate(r, st, loop = depth >= LOOP).contradiction != null) return null
        if (depth < WHAT_IF) return st
        while (!r.finished(st)) {
            val w = bestWhatIf(r, st, first = true) ?: return st
            st[w.edge] = if (w.assume == LINE) CROSS else LINE
            if (propagate(r, st, loop = true, seeds = seedsOf(r, w.edge)).contradiction != null) return null
        }
        return st
    }

    /** Whether the rungs up to [depth] finish the board from blank. */
    fun finishes(r: LoopRules, depth: Int): Boolean = settle(r, IntArray(r.g.edgeCount), depth)?.let { r.finished(it) } == true

    /** The deepest rung the board needs from blank ([LOCAL], [LOOP] or [WHAT_IF]), or null when the ladder does not finish it. */
    fun depth(r: LoopRules): Int? {
        val blank = IntArray(r.g.edgeCount)
        for (d in LOCAL..WHAT_IF) {
            val st = settle(r, blank, d) ?: return null
            if (r.finished(st)) return d
        }
        return null
    }

    /**
     * Clues taken back from [all] (`.` for none) in a random order while the rungs up to [needs] still finish the board
     * ([finishes] of the clues and a depth), so none is left that the rest already imply. When [needs] is [WHAT_IF] the
     * order is walked twice: first with the local and loop rules alone (cheap), then the clues still left with the
     * what-if as well. Null when [all] itself is not finished.
     */
    fun takeBack(rng: Rng, all: String, needs: Int, finishes: (String, Int) -> Boolean): String? {
        val order = rng.shuffled(all.indices.filter { all[it] != '.' })
        val clues = all.toCharArray()
        val passes = if (needs == WHAT_IF) listOf(LOOP, WHAT_IF) else listOf(needs)
        if (!finishes(all, needs)) return null
        for (d in passes) {
            if (!finishes(clues.concatToString(), d)) continue
            for (p in order) {
                if (clues[p] == '.') continue
                val kept = clues[p]
                clues[p] = '.'
                if (!finishes(clues.concatToString(), d)) clues[p] = kept
            }
        }
        return clues.concatToString()
    }

    // ---- the exhaustive count ---------------------------------------------------------------------

    /**
     * How many loops keep every rule, with no budget, from [root] (edges already settled soundly): the local and loop
     * rules to a fixpoint (sound, so they lose no answer), then a branch on an open edge (one beside the end of a stretch
     * first), line and then cross, until two answers are found or every branch is closed. A finished branch is checked by
     * [LoopRules.finished], not by the rules that led to it. Only [Verdict.ExactlyOne] carries the loop, as a `0`/`1`
     * edge string.
     */
    fun solutions(r: LoopRules, root: IntArray): Verdict<String> {
        val g = r.g
        var found = 0
        var answer = ""
        fun walk(st: IntArray) {
            if (found > 1) return
            if (propagate(r, st, loop = true).contradiction != null) return
            if (!connected(r, st)) return
            if (r.finished(st)) {
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
        walk(root)
        return Verdict.of(found) { answer }
    }

    /**
     * Whether every point that must be on the loop (one with a line, or [LoopRules.mustVisit]) can still meet the others:
     * one piece of the lattice joined by edges not crossed. A loop is one piece, so a board cut in two by crosses has no
     * answer (a prune for [solutions]).
     */
    private fun connected(r: LoopRules, st: IntArray): Boolean {
        val g = r.g
        val need = BooleanArray(g.nodes)
        var start = -1
        for (e in 0 until g.edgeCount) if (st[e] == LINE) { need[g.endA[e]] = true; need[g.endB[e]] = true }
        for (s in 0 until g.nodes) {
            if (r.mustVisit(s)) need[s] = true
            if (need[s] && start < 0) start = s
        }
        if (start < 0) return true
        val seen = BooleanArray(g.nodes)
        val queue = IntArray(g.nodes)
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
        for (s in 0 until g.nodes) if (need[s] && !seen[s]) return false
        return true
    }

    // ---- growing a loop -----------------------------------------------------------------------------

    /**
     * A random simple loop on an `n x n` lattice as a `0`/`1` edge string: the boundary of a region of the `(n-1) x (n-1)`
     * faces between points, grown from one random face. A face joins only if the boundary stays one simple closed curve.
     * Each step scores every face that could join with [score] (lower is better), on [greedyPercent] of candidates, and
     * takes one of the best at random (the others score 0). Growth stops once the loop is [low]-[high] per cent of `n * n`
     * edges long (drawn), or when nothing can join (kept if it already reaches [low] per cent); null otherwise.
     *
     * Every candidate is walked in face order and scored the same on every platform; no hash order.
     */
    fun growLoop(rng: Rng, n: Int, low: Int, high: Int, greedyPercent: Int, score: (LoopShape) -> Int): String? {
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
                val sc = if (rng.nextInt(100) < greedyPercent) score(shape) else 0
                if (sc < bestScore) {
                    bestScore = sc
                    best.clear()
                }
                if (sc == bestScore) best.add(f)
            }
            // Nowhere left to grow: good enough if it already reaches the share's low end.
            if (best.size == 0) break
            inside[best[rng.nextInt(best.size)]] = true
            shape.trace(inside)
            length = shape.length
        }
        if (length < target && length < n * n * low / 100) return null
        return CharArray(shape.on.size) { if (shape.on[it]) '1' else '0' }.concatToString()
    }

    /**
     * The boundary of a face region, worked out in place with nothing allocated per candidate: [trace] fills [on] and says
     * whether it is one simple closed curve, [length] is its edge count, and [nb] / [degree] each point's neighbours on it.
     */
    class LoopShape(val n: Int) {
        val g = LoopGrid.of(n)
        val on = BooleanArray(g.edgeCount)
        val nb = IntArray(g.nodes * 2)
        val degree = IntArray(g.nodes)
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
            for (s in 0 until g.nodes) {
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

        /** Whether the loop turns at point [s] (which it must pass). */
        fun turns(s: Int): Boolean {
            val a = nb[s * 2]
            val b = nb[s * 2 + 1]
            return a / n != b / n && a % n != b % n
        }
    }
}
