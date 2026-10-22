package com.joebywan.daybook

import com.joebywan.daybook.puzzles.Domino
import com.joebywan.daybook.puzzles.Placement
import com.joebywan.daybook.puzzles.RegionRule
import com.joebywan.daybook.puzzles.RuleKind

/**
 * An independent Dominoes solver, on the opposite principle to `DominoLogic.solutions` at every
 * point: squares are filled in reading order (never the most constrained), each paired with the
 * square to its right or below, every unused domino tried both ways round (a double once), and a
 * region's rule is checked only once the region is fully covered, by its own rule arithmetic. No
 * bounds, no pruning by the tray, no shared code.
 */
object DominoOracle {

    /** Every answer, up to [cap], each as one placement per tray index. */
    fun answers(
        width: Int,
        height: Int,
        open: List<Boolean>,
        regionOf: List<Int>,
        rules: List<RegionRule>,
        tray: List<Domino>,
        cap: Int = 2,
        placed: List<Placement?> = List(tray.size) { null },
    ): List<List<Placement>> {
        val n = width * height
        val pips = IntArray(n) { -1 }
        val at = arrayOfNulls<Placement>(tray.size)
        for ((k, p) in placed.withIndex()) {
            if (p == null) continue
            pips[p.first] = tray[k].a
            pips[p.second] = tray[k].b
            at[k] = p
        }
        val members = rules.indices.map { r -> (0 until n).filter { regionOf[it] == r } }
        val out = ArrayList<List<Placement>>()

        fun holds(r: Int): Boolean {
            val v = members[r].map { pips[it] }
            if (v.any { it < 0 }) return true // not finished: not checked yet
            val rule = rules[r]
            var total = 0
            for (x in v) total += x
            return when (rule.kind) {
                RuleKind.SUM -> total == rule.n
                RuleKind.LESS -> total < rule.n
                RuleKind.MORE -> total > rule.n
                RuleKind.EQUAL -> v.all { it == v.first() }
                RuleKind.DIFFERENT -> v.indices.all { i -> v.indices.all { j -> i == j || v[i] != v[j] } }
            }
        }

        fun fill(from: Int) {
            if (out.size >= cap) return
            var q = from
            while (q < n && (!open[q] || pips[q] >= 0)) q++
            if (q == n) {
                if (at.all { it != null }) out.add(at.map { it!! })
                return
            }
            for (partner in listOf(q + 1, q + width)) {
                if (partner >= n || (partner == q + 1 && q % width == width - 1)) continue
                if (!open[partner] || pips[partner] >= 0) continue
                for (k in tray.indices) {
                    if (at[k] != null) continue
                    val ways = if (tray[k].a == tray[k].b) listOf(Placement(q, partner))
                    else listOf(Placement(q, partner), Placement(partner, q))
                    for (p in ways) {
                        pips[p.first] = tray[k].a
                        pips[p.second] = tray[k].b
                        at[k] = p
                        val ok = (regionOf[q] < 0 || holds(regionOf[q])) && (regionOf[partner] < 0 || holds(regionOf[partner]))
                        if (ok) fill(q + 1)
                        at[k] = null
                        pips[p.first] = -1
                        pips[p.second] = -1
                        if (out.size >= cap) return
                    }
                }
            }
        }
        fill(0)
        return out
    }
}
