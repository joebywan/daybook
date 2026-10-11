package com.joebywan.daybook

/**
 * Counts the answers of a Regions board the naive way, sharing nothing with the generator's solver
 * or with `RegionsLayout`: square by square in reading order, no candidate masks, no propagation,
 * no limit, every digit tried, a digit refused only when a square already filled breaks a rule
 * against it (same region and same digit; or within one row and one column and the same digit).
 * Counts every answer, so "1" cannot mean "stopped looking".
 */
object RegionsOracle {
    fun count(n: Int, region: List<Int>, given: List<Int>): Long = walk(n, region, given) {}

    /** Every answer, each as a full list of digits. */
    fun solutions(n: Int, region: List<Int>, given: List<Int>): List<List<Int>> =
        mutableListOf<List<Int>>().also { out -> walk(n, region, given) { out += it } }

    private fun walk(n: Int, region: List<Int>, given: List<Int>, onSolution: (List<Int>) -> Unit): Long {
        val size = region.map { r -> region.count { it == r } }
        val g = given.toIntArray()
        fun ok(i: Int, d: Int): Boolean {
            if (d > size[i]) return false
            for (j in 0 until n * n) {
                if (j == i || g[j] != d) continue
                if (region[j] == region[i]) return false
                if (kotlin.math.abs(j / n - i / n) <= 1 && kotlin.math.abs(j % n - i % n) <= 1) return false
            }
            return true
        }
        fun go(i: Int): Long {
            if (i == n * n) { onSolution(g.toList()); return 1 }
            if (given[i] != 0) return go(i + 1)
            var total = 0L
            for (d in 1..size[i]) {
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
