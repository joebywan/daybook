package com.joebywan.daybook

/**
 * Reckon's answers, found with nothing the app's prover uses: square by square in reading order over 1..n, a row or
 * column checked as each digit goes in, and a cage checked only once its last square is filled, by its own reading of
 * the arithmetic (sum, product, difference, whole quotient). No candidates, no propagation, no repeat rule of its own:
 * a cage's repeats are already ruled by the lines.
 */
object ReckonOracle {

    private fun makes(op: Char, target: Int, d: List<Int>): Boolean = when (op) {
        '+' -> d.sum() == target
        '*' -> d.reduce { a, b -> a * b } == target
        '-' -> d.size == 2 && (d[0] - d[1] == target || d[1] - d[0] == target)
        '/' -> d.size == 2 && (d[0] == d[1] * target || d[1] == d[0] * target)
        '=' -> d.size == 1 && d[0] == target
        else -> error("op $op")
    }

    /** Every answer up to [limit], as digit lists. [cells] holds 0 for an open square; a filled one is kept. */
    fun solutions(n: Int, cells: List<Int>, cageOf: List<Int>, ops: String, targets: List<Int>, limit: Int = 2): List<List<Int>> {
        val members = cageOf.indices.groupBy { cageOf[it] }
        val lastOf = members.mapValues { (_, sq) -> sq.max() }
        val g = cells.toIntArray()
        val out = mutableListOf<List<Int>>()
        fun go(i: Int) {
            if (out.size >= limit) return
            if (i == n * n) { out += g.toList(); return }
            val r = i / n
            val c = i % n
            val digits = if (cells[i] != 0) listOf(cells[i]) else (1..n).toList()
            for (d in digits) {
                if ((0 until c).any { g[r * n + it] == d } || (0 until r).any { g[it * n + c] == d }) continue
                g[i] = d
                val k = cageOf[i]
                if (lastOf[k] == i && !makes(ops[k], targets[k], members.getValue(k).map { g[it] })) continue
                go(i + 1)
            }
            g[i] = cells[i]
        }
        go(0)
        return out
    }
}
