package com.joebywan.daybook

/**
 * Independent of the generator's prover: instead of scanning squares, it walks the trees and gives each
 * one a tent beside it, and counts the distinct tent sets (two pairings can make the same set).
 * Checks the clues, the corners and the pairing itself, written out here.
 */
object TentsOracle {
    fun count(n: Int, trees: List<Boolean>, rowCounts: List<Int>, colCounts: List<Int>): Int {
        val treeIdx = trees.indices.filter { trees[it] }
        val sets = HashSet<Set<Int>>()
        val chosen = ArrayList<Int>()
        fun go(k: Int) {
            if (k == treeIdx.size) {
                val set = chosen.toSet()
                for (l in 0 until n) {
                    if (set.count { it / n == l } != rowCounts[l] || set.count { it % n == l } != colCounts[l]) return
                }
                sets += set
                return
            }
            val t = treeIdx[k]
            val r = t / n
            val c = t % n
            for ((dr, dc) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                val rr = r + dr
                val cc = c + dc
                if (rr !in 0 until n || cc !in 0 until n) continue
                val sq = rr * n + cc
                if (trees[sq] || sq in chosen) continue
                if (chosen.any { Math.abs(it / n - rr) <= 1 && Math.abs(it % n - cc) <= 1 }) continue
                chosen += sq
                go(k + 1)
                chosen.removeAt(chosen.size - 1)
            }
        }
        go(0)
        return sets.size
    }
}
