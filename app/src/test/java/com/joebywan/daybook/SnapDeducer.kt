package com.joebywan.daybook

/**
 * Snap's whole-board rules, test code only (Snap has no teacher). Each joint between two neighbouring squares is in,
 * out or unknown, and [propagate] applies to a fixpoint: degree (every square two joints, the 1 and the highest number
 * one), pieces (a joint that would close a loop, join the ends early or put the numbers out of order is out) and
 * pockets (the usable joints stay connected; a bridge is used); at level 1 also a one-deep what-if. It began as the
 * feasibility prototype `SnapDeducerReportTest` (deleted 2026-10-26; its results are in `docs/TEACHING.md`), and is
 * kept for `LevelDifficultyBuildTest`, which asks whether the rules alone finish a board from blank.
 */
internal class SnapDeducer(val w: Int, val h: Int, val wp: IntArray, val usePockets: Boolean = true) {
    val n = w * h
    val start = wp.indexOf(1)
    val top = wp.max()
    val end = wp.indexOf(top)
    val ea = ArrayList<Int>()
    val eb = ArrayList<Int>()
    val cellEdges: Array<IntArray>

    init {
        val per = Array(n) { ArrayList<Int>() }
        for (c in 0 until n) {
            for (d in listOf(if (c % w < w - 1) c + 1 else -1, if (c + w < n) c + w else -1)) if (d >= 0) {
                per[c] += ea.size; per[d] += ea.size; ea += c; eb += d
            }
        }
        cellEdges = Array(n) { per[it].toIntArray() }
    }

    val m = ea.size
    fun other(e: Int, c: Int) = if (ea[e] == c) eb[e] else ea[e]
    private fun need(c: Int) = if (c == start || c == end) 1 else 2

    /** Edges set by each rule, for the report: degree, piece, pocket, what-if. */
    val fired = IntArray(4)

    /** For each joint the what-if settled: how many joints the rules set in the trial before it broke. */
    val chains = ArrayList<Int>()

    /** Applies the rules (and, at [level] 1, the what-if) to [st] in place. False on a contradiction. */
    fun propagate(st: ByteArray, level: Int = 0): Boolean {
        while (true) {
            val r = degree(st)
            if (r < 0) return false
            if (r > 0) continue
            val p = pieces(st)
            if (p < 0) return false
            if (p > 0) continue
            val k = if (usePockets) pockets(st) else 0
            if (k < 0) return false
            if (k > 0) continue
            if (level == 0 || !whatIf(st)) return true
        }
    }

    private fun set(st: ByteArray, e: Int, v: Byte, rule: Int) { st[e] = v; fired[rule]++ }

    private fun degree(st: ByteArray): Int {
        var changed = 0
        for (c in 0 until n) {
            var inC = 0
            var unk = 0
            for (e in cellEdges[c]) when (st[e]) { IN -> inC++; UNKNOWN -> unk++ }
            val nd = need(c)
            if (inC > nd || inC + unk < nd) return -1
            if (unk == 0) continue
            if (inC == nd) { for (e in cellEdges[c]) if (st[e] == UNKNOWN) set(st, e, OUT, 0); changed++ }
            else if (inC + unk == nd) { for (e in cellEdges[c]) if (st[e] == UNKNOWN) set(st, e, IN, 0); changed++ }
        }
        return changed
    }

    /** Consecutive numbers, all rising or all falling. */
    private fun inOrder(seq: List<Int>): Boolean =
        seq.size < 2 || seq.zipWithNext().all { (a, b) -> b - a == 1 } || seq.zipWithNext().all { (a, b) -> a - b == 1 }

    private fun pieces(st: ByteArray): Int {
        // The pieces of line the in-joints make, each as its squares from one end to the other.
        val piece = IntArray(n) { -1 }
        val lists = ArrayList<List<Int>>()
        fun inNbrs(c: Int) = cellEdges[c].filter { st[it] == IN }.map { other(it, c) }
        for (c in 0 until n) if (piece[c] < 0 && inNbrs(c).size < 2) {
            val cells = arrayListOf(c)
            piece[c] = lists.size
            var prev = -1
            var cur = c
            while (true) {
                val next = inNbrs(cur).firstOrNull { it != prev } ?: break
                if (piece[next] >= 0) return -1
                piece[next] = lists.size; cells += next; prev = cur; cur = next
            }
            lists += cells
        }
        if (piece.any { it < 0 }) return -1 // a loop
        val seqs = lists.map { l -> l.mapNotNull { c -> wp[c].takeIf { it > 0 } } }
        for (i in lists.indices) {
            if (!inOrder(seqs[i])) return -1
            if (start in lists[i] && end in lists[i] && lists[i].size < n) return -1
        }
        var changed = 0
        for (e in 0 until m) if (st[e] == UNKNOWN) {
            val a = ea[e]
            val b = eb[e]
            val pa = piece[a]
            val pb = piece[b]
            val bad = when {
                pa == pb -> true
                ((start in lists[pa] && end in lists[pb]) || (end in lists[pa] && start in lists[pb])) &&
                    lists[pa].size + lists[pb].size < n -> true
                else -> {
                    val sa = if (lists[pa].last() == a) seqs[pa] else seqs[pa].reversed()
                    val sb = if (lists[pb].first() == b) seqs[pb] else seqs[pb].reversed()
                    !inOrder(sa + sb)
                }
            }
            if (bad) { set(st, e, OUT, 1); changed++ }
        }
        return changed
    }

    private fun pockets(st: ByteArray): Int {
        val disc = IntArray(n) { -1 }
        val low = IntArray(n)
        val fin = IntArray(n)
        val parentEdge = IntArray(n) { -1 }
        var time = 0
        val bridges = ArrayList<Int>()
        // (cell, separated child) pairs: removing cell cuts child's subtree off.
        val cuts = ArrayList<IntArray>()
        fun dfs(v: Int) {
            disc[v] = time; low[v] = time; time++
            for (e in cellEdges[v]) {
                if (st[e] == OUT || e == parentEdge[v]) continue
                val u = other(e, v)
                if (disc[u] < 0) {
                    parentEdge[u] = e
                    dfs(u)
                    low[v] = minOf(low[v], low[u])
                    if (low[u] > disc[v]) bridges += e
                    if (low[u] >= disc[v]) cuts += intArrayOf(v, u)
                } else low[v] = minOf(low[v], disc[u])
            }
            fin[v] = time - 1
        }
        dfs(0)
        if (disc.any { it < 0 }) return -1
        var changed = 0
        for (e in bridges) if (st[e] == UNKNOWN) { set(st, e, IN, 2); changed++ }
        if (changed > 0) return changed
        for (v in 0 until n) {
            val children = cuts.filter { it[0] == v }.map { it[1] }
            // The root's first child is not cut off by it: everything is in that subtree.
            val separated = if (v == 0) children.drop(1) else children
            if (separated.isEmpty()) continue
            val sides = separated.size + 1
            if (sides >= 3 || v == start || v == end) return -1
            val c = separated[0]
            fun inSub(u: Int) = disc[u] >= disc[c] && disc[u] <= fin[c]
            // Each side holds one end, and the numbers on the 1's side all come before those on the other.
            val sideA = (0 until n).filter { it != v && inSub(it) }
            val sideB = (0 until n).filter { it != v && !inSub(it) }
            if ((start in sideA) == (end in sideA)) return -1
            val (first, second) = if (start in sideA) sideA to sideB else sideB to sideA
            val hiFirst = first.maxOf { wp[it] }
            val loSecond = second.filter { wp[it] > 0 }.minOfOrNull { wp[it] } ?: Int.MAX_VALUE
            if (hiFirst >= loSecond || (wp[v] > 0 && (wp[v] < hiFirst || wp[v] > loSecond))) return -1
            for (side in listOf(true, false)) {
                val mouths = cellEdges[v].filter { st[it] != OUT && inSub(other(it, v)) == side }
                if (mouths.size == 1 && st[mouths[0]] == UNKNOWN) { set(st, mouths[0], IN, 2); changed++ }
            }
            if (changed > 0) return changed
        }
        return changed
    }

    private fun whatIf(st: ByteArray): Boolean {
        for (e in 0 until m) if (st[e] == UNKNOWN) {
            for (guess in listOf(IN, OUT)) {
                val trial = st.copyOf()
                trial[e] = guess
                val saved = fired.copyOf()
                val ok = propagate(trial, 0)
                val chain = fired.sum() - saved.sum()
                saved.copyInto(fired)
                if (!ok) { chains += chain; set(st, e, if (guess == IN) OUT else IN, 3); return true }
            }
        }
        return false
    }

    /** The answer, by search over the same rules (the board has one). */
    fun solve(): List<Int>? {
        val saved = fired.copyOf()
        fun go(st: ByteArray): ByteArray? {
            if (!propagate(st, 0)) return null
            val e = (0 until m).firstOrNull { st[it] == UNKNOWN } ?: return st
            for (v in listOf(IN, OUT)) { val t = st.copyOf(); t[e] = v; go(t)?.let { return it } }
            return null
        }
        val st = go(ByteArray(m)) ?: return null
        saved.copyInto(fired)
        val path = arrayListOf(start)
        var prev = -1
        while (path.size < n) {
            val cur = path.last()
            val next = cellEdges[cur].filter { st[it] == IN }.map { other(it, cur) }.first { it != prev }
            prev = cur; path += next
        }
        return path
    }

    fun edge(a: Int, b: Int) = cellEdges[a].first { other(it, a) == b }

    companion object {
        const val UNKNOWN: Byte = 0
        const val IN: Byte = 1
        const val OUT: Byte = 2
    }
}
