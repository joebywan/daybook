package com.joebywan.daybook

/**
 * An independent checker for Nonogram boards, deliberately written on a different principle from
 * the line solver it checks: no per-line reasoning at all, just every arrangement of every row,
 * laid down one row at a time, with each column's partial runs checked as it goes. It shares
 * nothing with `NonogramLogic` but the clue-reading convention, so the two cannot share a blind
 * spot about what "one answer" means.
 */
object NonogramOracle {

    /** Run lengths of the true squares of [line], left to right. */
    fun runsOf(line: List<Boolean>): List<Int> {
        val out = mutableListOf<Int>()
        var run = 0
        for (b in line) {
            if (b) run++ else if (run > 0) { out += run; run = 0 }
        }
        if (run > 0) out += run
        return out
    }

    /** Every way to lay [clue] in [n] squares, as masks (index 0 is the first square). */
    fun layouts(clue: List<Int>, n: Int): List<BooleanArray> {
        val out = mutableListOf<BooleanArray>()
        val line = BooleanArray(n)
        fun place(block: Int, from: Int) {
            if (block == clue.size) {
                out += line.copyOf()
                return
            }
            val rest = clue.drop(block + 1).sumOf { it + 1 }
            var start = from
            while (start + clue[block] + rest <= n) {
                for (x in start until start + clue[block]) line[x] = true
                place(block + 1, start + clue[block] + 1)
                for (x in start until start + clue[block]) line[x] = false
                start++
            }
        }
        place(0, 0)
        return out
    }

    /** Every picture (row by row, true = filled) that fits [rows] and [cols], up to [cap]. */
    fun answers(width: Int, height: Int, rows: List<List<Int>>, cols: List<List<Int>>, cap: Int = 100_000): List<BooleanArray> {
        val out = mutableListOf<BooleanArray>()
        val candidates = rows.map { layouts(it, width) }
        val picture = Array(height) { BooleanArray(width) }
        fun walk(row: Int) {
            if (out.size >= cap) return
            if (row == height) {
                if ((0 until width).all { c -> runsOf(List(height) { picture[it][c] }) == cols[c] }) {
                    out += BooleanArray(width * height) { picture[it / width][it % width] }
                }
                return
            }
            for (layout in candidates[row]) {
                picture[row] = layout
                // A column that already shows more than its clue allows can never recover.
                if ((0 until width).all { c ->
                        val so_far = runsOf(List(row + 1) { picture[it][c] })
                        so_far.size <= cols[c].size && so_far.zip(cols[c]).all { (a, b) -> a <= b }
                    }
                ) walk(row + 1)
            }
            picture[row] = BooleanArray(width)
        }
        walk(0)
        return out
    }

    /** How many pictures fit [rows] and [cols], counting no further than [cap]. */
    fun countAnswers(width: Int, height: Int, rows: List<List<Int>>, cols: List<List<Int>>, cap: Int = 2): Int {
        val candidates = rows.map { layouts(it, width) }
        var found = 0
        fun walk(row: Int, done: IntArray, run: IntArray) {
            if (found >= cap) return
            if (row == height) {
                for (c in 0 until width) {
                    var d = done[c]
                    if (run[c] > 0) {
                        if (d >= cols[c].size || cols[c][d] != run[c]) return
                        d++
                    }
                    if (d != cols[c].size) return
                }
                found++
                return
            }
            candidates[row].forEach { layout ->
                val nd = done.copyOf()
                val nr = run.copyOf()
                for (c in 0 until width) {
                    if (layout[c]) {
                        nr[c]++
                        if (nd[c] >= cols[c].size || nr[c] > cols[c][nd[c]]) return@forEach
                    } else if (nr[c] > 0) {
                        if (cols[c][nd[c]] != nr[c]) return@forEach
                        nd[c]++
                        nr[c] = 0
                    }
                }
                walk(row + 1, nd, nr)
            }
        }
        walk(0, IntArray(width), IntArray(width))
        return found
    }
}
