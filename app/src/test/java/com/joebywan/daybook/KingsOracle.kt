package com.joebywan.daybook

/**
 * Every legal Kings placement, found by brute force over the columns of each row rather than by
 * the generator's own pruned search: one king a row, column and touching pruned as it goes (the
 * only constraints cheap enough to prune on), regions checked once a placement is whole, so the
 * enumeration stays obviously complete.
 */
object KingsOracle {

    /** Every legal placement on an [n] x [n] board cut into [region], up to [cap] of them. */
    fun allSolutions(n: Int, region: List<Int>, cap: Int = 5000): List<Set<Int>> {
        val out = mutableListOf<Set<Int>>()
        val cols = IntArray(n)
        fun walk(row: Int) {
            if (out.size >= cap) return
            if (row == n) {
                val kings = (0 until n).map { it * n + cols[it] }
                if (kings.map { region[it] }.toSet().size == n) out += kings.toSet()
                return
            }
            for (c in 0 until n) {
                if ((0 until row).any { cols[it] == c }) continue
                if (row > 0 && kotlin.math.abs(cols[row - 1] - c) <= 1) continue
                cols[row] = c
                walk(row + 1)
            }
        }
        walk(0)
        return out
    }
}
