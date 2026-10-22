package com.joebywan.daybook

/**
 * An independent checker for Tally boards, written on a different principle from the prover
 * (`TallyLogic.countSolutions`, which walks rows, takes each row's ways from a bitmask loop and cuts
 * a branch when a column can no longer reach its target). Here every keep mask of each *column* is
 * listed by plain recursion over its squares, and the columns are then combined left to right with
 * nothing cut but a row that has already passed its target; the rows are checked only once every
 * column is placed. It shares nothing with the code under test but the reading of the rules.
 */
object TallyOracle {

    /** Every subset of [values] (as lists of kept positions) whose values add up to [target]. */
    fun subsets(values: List<Int>, target: Int): List<List<Int>> {
        val out = mutableListOf<List<Int>>()
        fun walk(i: Int, chosen: List<Int>, sum: Int) {
            if (i == values.size) {
                if (sum == target) out += chosen
                return
            }
            walk(i + 1, chosen, sum)
            walk(i + 1, chosen + i, sum + values[i])
        }
        walk(0, emptyList(), 0)
        return out
    }

    /**
     * Every answer (a kept flag per square, row by row) of the [n] x [n] board [digits] with row
     * targets [rows] and column targets [cols], up to [cap].
     */
    fun answers(n: Int, digits: List<Int>, rows: List<Int>, cols: List<Int>, cap: Int = 10_000): List<BooleanArray> {
        val columnWays = List(n) { c -> subsets(List(n) { r -> digits[r * n + c] }, cols[c]) }
        val rowSum = IntArray(n)
        val kept = BooleanArray(n * n)
        val out = mutableListOf<BooleanArray>()
        fun place(c: Int) {
            if (out.size >= cap) return
            if (c == n) {
                if ((0 until n).all { rowSum[it] == rows[it] }) out += kept.copyOf()
                return
            }
            for (way in columnWays[c]) {
                for (r in way) {
                    kept[r * n + c] = true
                    rowSum[r] += digits[r * n + c]
                }
                if ((0 until n).all { rowSum[it] <= rows[it] }) place(c + 1)
                for (r in way) {
                    kept[r * n + c] = false
                    rowSum[r] -= digits[r * n + c]
                }
            }
        }
        place(0)
        return out
    }

    /** The answers as `1`/`0` strings, the format `TallyState.keep` uses. */
    fun answerStrings(n: Int, digits: List<Int>, rows: List<Int>, cols: List<Int>, cap: Int = 10_000): List<String> =
        answers(n, digits, rows, cols, cap).map { a -> a.joinToString("") { if (it) "1" else "0" } }
}
