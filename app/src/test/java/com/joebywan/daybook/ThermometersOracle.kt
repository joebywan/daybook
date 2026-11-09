package com.joebywan.daybook

/**
 * An independent Thermometers solver for tests: square by square in reading order, each filled or empty, the
 * thermometer rule checked pair by pair once both squares of a pair are decided, a row checked when it ends and the
 * columns at the very end (with only "no more than the count" on the way). It knows nothing of levels, lines' least and
 * most, or the generator's tube-by-tube count, so it cannot share their blind spots.
 */
object ThermometersOracle {

    /** Every answer, as `1`/`0` strings, up to [cap] of them. Counts are [rows] then [cols]. */
    fun answers(n: Int, tubes: List<List<Int>>, rows: List<Int>, cols: List<Int>, cap: Int = Int.MAX_VALUE): List<String> {
        val below = IntArray(n * n) { -1 }
        val above = IntArray(n * n) { -1 }
        for (tube in tubes) for (k in 1 until tube.size) {
            below[tube[k]] = tube[k - 1]
            above[tube[k - 1]] = tube[k]
        }
        val v = IntArray(n * n)
        val rowSum = IntArray(n)
        val colSum = IntArray(n)
        val out = ArrayList<String>()
        fun go(i: Int) {
            if (out.size >= cap) return
            if (i == n * n) {
                if ((0 until n).all { colSum[it] == cols[it] }) out += v.joinToString("")
                return
            }
            val r = i / n
            val c = i % n
            for (x in 0..1) {
                // A filled square needs the one below it filled; an empty one needs the one above it empty.
                if (below[i] in 0 until i && x == 1 && v[below[i]] == 0) continue
                if (above[i] in 0 until i && x == 0 && v[above[i]] == 1) continue
                v[i] = x
                rowSum[r] += x
                colSum[c] += x
                val ok = rowSum[r] <= rows[r] && colSum[c] <= cols[c] && (c < n - 1 || rowSum[r] == rows[r])
                if (ok) go(i + 1)
                rowSum[r] -= x
                colSum[c] -= x
            }
        }
        go(0)
        return out
    }
}
