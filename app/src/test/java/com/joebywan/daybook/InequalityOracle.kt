package com.joebywan.daybook

import com.joebywan.daybook.puzzles.Sign

/**
 * Counts the answers of an Inequality board the naive way, sharing nothing with the generator's
 * solver: square by square in reading order, no candidate masks, no propagation, no limit, every
 * digit tried, a digit refused only when it repeats in its line or contradicts a sign whose other
 * end is already filled. Counts every answer, so "1" cannot mean "stopped looking".
 */
object InequalityOracle {
    fun count(n: Int, given: List<Int>, signs: List<Sign>): Long {
        val g = given.toIntArray()
        fun ok(i: Int, d: Int): Boolean {
            for (j in 0 until n * n) {
                if (j != i && g[j] == d && (j / n == i / n || j % n == i % n)) return false
            }
            for (s in signs) {
                if (s.lo == i && g[s.hi] != 0 && d >= g[s.hi]) return false
                if (s.hi == i && g[s.lo] != 0 && d <= g[s.lo]) return false
            }
            return true
        }
        fun go(i: Int): Long {
            if (i == n * n) return 1
            if (given[i] != 0) return go(i + 1)
            var total = 0L
            for (d in 1..n) {
                if (!ok(i, d)) continue
                g[i] = d
                total += go(i + 1)
                g[i] = 0
            }
            return total
        }
        // The givens must agree among themselves too.
        for (i in 0 until n * n) if (given[i] != 0 && !ok(i, given[i])) return 0
        return go(0)
    }
}
