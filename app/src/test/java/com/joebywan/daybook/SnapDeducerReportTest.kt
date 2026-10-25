package com.joebywan.daybook

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.SeedHash
import com.joebywan.daybook.puzzles.Snap
import com.joebywan.daybook.puzzles.SnapState
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * A feasibility prototype, test code only: could Snap have a hint teacher that reasons about the whole board rather
 * than only the end of the line (which `Snap.offersHints` records failing)? [SnapDeducer] knows each joint between
 * two neighbouring squares as in, out or unknown and applies, to a fixpoint:
 *
 * - degree: every square has two joints, the 1 and the highest number one (a square with two usable joints left uses both);
 * - pieces: a joint that would close a loop, join the two ends' pieces early, or put the numbers along a piece out of
 *   order (a piece of the line holds consecutive numbers, in order) is out;
 * - pockets: the usable joints must stay connected; a bridge must be used; a square whose removal cuts the board in two
 *   uses one joint into each side (a side reached by one joint uses it), and each side holds one end of the line.
 *
 * Then, separately, a what-if one deep: suppose a joint in (or out), run the rules; a contradiction settles it the other
 * way. That is a trial, so it is reported apart from the rules.
 *
 * It measures, on a year of daily boards per tier: how much of the line the deducer settles from a blank board, and,
 * walking the stored answer from the 1 as a player draws it, on how many steps the next square is settled (the move a
 * hint would have to explain). Env-gated with [TierDepthReportTest] (`DAYBOOK_DEPTH_REPORT`); writes
 * `build/reports/snap-deducer.txt`. Results: `docs/TEACHING.md`, "Tier depth (measured 2026-10)".
 */
class SnapDeducerReportTest {

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

    private class Result(val blankSettled: IntArray, val explained: IntArray, val steps: Int, val fired: IntArray, val noPockets: IntArray, val chains: List<Int>)

    private fun measure(s: SnapState): Result {
        val dd = SnapDeducer(s.width, s.height, s.waypoints.toIntArray())
        val path = dd.solve()
        assertNotNull(path)
        assertTrue(Snap.obeysRules(s, path!!))
        val blank = IntArray(2)
        val explained = IntArray(2)
        for (level in 0..1) {
            val st = ByteArray(dd.m)
            assertTrue(dd.propagate(st, level))
            blank[level] = st.count { it == SnapDeducer.IN }
            for (i in 0 until path.size - 1) {
                val e = dd.edge(path[i], path[i + 1])
                if (st[e] == SnapDeducer.IN) explained[level]++
                st[e] = SnapDeducer.IN
                assertTrue(dd.propagate(st, level))
            }
        }
        // The same walks without the pocket rule, to see what it adds.
        val bare = SnapDeducer(s.width, s.height, s.waypoints.toIntArray(), usePockets = false)
        val noPockets = IntArray(2)
        for (level in 0..1) {
            val st = ByteArray(bare.m)
            assertTrue(bare.propagate(st, level))
            for (i in 0 until path.size - 1) {
                val e = bare.edge(path[i], path[i + 1])
                if (st[e] == SnapDeducer.IN) noPockets[level]++
                st[e] = SnapDeducer.IN
                assertTrue(bare.propagate(st, level))
            }
        }
        return Result(blank, explained, path.size - 1, dd.fired, noPockets, dd.chains)
    }

    @Test
    fun `report how far a whole-board deducer gets on Snap`() {
        val env = System.getenv("DAYBOOK_DEPTH_REPORT")
        assumeTrue("set DAYBOOK_DEPTH_REPORT to measure", env != null)
        val days = env!!.toIntOrNull()?.takeIf { it > 1 } ?: 365
        val first = SeedHash.epochDay(2026, 1, 1)
        val out = StringBuilder("Snap whole-board deducer: $days daily boards per tier from 2026-01-01.\n")
        out.append("rules = degree, pieces, pockets; +what-if = one joint supposed, rules run, a contradiction settles it.\n\n")
        for (d in Difficulty.entries) {
            val t0 = System.nanoTime()
            val rs = (0 until days).toList().parallelStream().map { i ->
                measure(Snap.generate(SeedHash.daily(first + i, Snap.id, d), d) as SnapState)
            }.toList()
            val ms = (System.nanoTime() - t0) / 1_000_000
            val steps = rs.sumOf { it.steps }
            out.append("${d.name}: ${rs[0].steps + 1} squares\n")
            for ((level, name) in listOf("rules", "+what-if").withIndex()) {
                out.append("  %-9s finished from blank %.1f%% of boards; line settled from blank %.2f%%; walk steps explained %.2f%%; boards with every step explained %.1f%%\n".format(
                    name,
                    100.0 * rs.count { it.blankSettled[level] == it.steps } / days,
                    100.0 * rs.sumOf { it.blankSettled[level] } / steps,
                    100.0 * rs.sumOf { it.explained[level] } / steps,
                    100.0 * rs.count { it.explained[level] == it.steps } / days,
                ))
            }
            out.append("  without pockets, walk steps explained: rules %.2f%%, +what-if %.2f%%\n".format(
                100.0 * rs.sumOf { it.noPockets[0] } / steps, 100.0 * rs.sumOf { it.noPockets[1] } / steps))
            val chains = rs.flatMap { it.chains }.sorted()
            if (chains.isNotEmpty()) out.append("  what-if chain (joints set before the contradiction): median ${chains[chains.size / 2]}, p90 ${chains[chains.size * 9 / 10]}, max ${chains.last()}\n")
            val f = IntArray(4).also { a -> rs.forEach { r -> for (k in 0..3) a[k] += r.fired[k] } }
            out.append("  joints set, both walks: degree ${f[0]}, pieces ${f[1]}, pockets ${f[2]}, what-if ${f[3]} | $ms ms\n\n")
        }
        File("build/reports").mkdirs()
        File("build/reports/snap-deducer.txt").writeText(out.toString())
        println(out)
    }
}
