package com.joebywan.daybook

/**
 * An independent Lamps solver for tests, sharing nothing with `LampsLogic`: no sight lists, no candidates, no
 * propagation. It walks the horizontal runs of white squares in reading order, giving each run no lamp or a lamp on one
 * of its squares (a run never holds two: they would light each other), refuses a lamp with another above it in its
 * column, and checks a number once every row around it is chosen and a square once both of its runs are.
 */
object LampsOracle {

    /** Every lamp set obeying the rules, as `'1'`/`'0'` strings, stopping at [limit]. */
    fun solutions(n: Int, walls: String, limit: Int = Int.MAX_VALUE): List<String> {
        fun white(r: Int, c: Int) = r in 0 until n && c in 0 until n && walls[r * n + c] == '.'
        val runs = ArrayList<IntArray>()
        for (r in 0 until n) {
            var c = 0
            while (c < n) {
                if (!white(r, c)) { c++; continue }
                val start = c
                while (white(r, c)) c++
                runs += IntArray(c - start) { r * n + start + it }
            }
        }
        // The last row of each square's vertical run: once that row is chosen, the square's light is settled.
        val bottom = IntArray(n * n) { i -> var r = i / n; while (white(r + 1, i % n)) r++; r }
        val lamp = BooleanArray(n * n)
        val out = ArrayList<String>()

        fun lampAt(r: Int, c: Int) = r in 0 until n && c in 0 until n && lamp[r * n + c]
        fun lit(i: Int): Boolean {
            val r = i / n
            val c = i % n
            for ((dr, dc) in listOf(0 to 0, 0 to 1, 0 to -1, 1 to 0, -1 to 0)) {
                var rr = r
                var cc = c
                while (white(rr, cc)) {
                    if (lamp[rr * n + cc]) return true
                    if (dr == 0 && dc == 0) break
                    rr += dr
                    cc += dc
                }
            }
            return false
        }
        fun numberOk(i: Int): Boolean {
            val ch = walls[i]
            if (ch !in '0'..'4') return true
            val r = i / n
            val c = i % n
            val count = listOf(lampAt(r - 1, c), lampAt(r + 1, c), lampAt(r, c - 1), lampAt(r, c + 1)).count { it }
            return count == ch - '0'
        }
        /** Row [r] is chosen: the numbers of row r - 1 and the squares whose column run ends in row [r] are settled. */
        fun finish(r: Int): Boolean {
            if (r >= 1) for (c in 0 until n) if (!numberOk((r - 1) * n + c)) return false
            if (r < n) for (i in 0 until n * n) if (walls[i] == '.' && bottom[i] == r && !lit(i)) return false
            return true
        }
        fun go(k: Int, doneRows: Int) {
            if (out.size >= limit) return
            val row = if (k < runs.size) runs[k][0] / n else n + 1
            var done = doneRows
            while (done < row) {
                if (!finish(done)) return
                done++
            }
            if (k == runs.size) {
                out += CharArray(n * n) { if (lamp[it]) '1' else '0' }.concatToString()
                return
            }
            go(k + 1, done)
            for (i in runs[k]) {
                var r = i / n - 1
                var clear = true
                while (white(r, i % n)) { if (lamp[r * n + i % n]) { clear = false; break }; r-- }
                if (!clear) continue
                lamp[i] = true
                go(k + 1, done)
                lamp[i] = false
            }
        }
        go(0, 0)
        return out
    }
}
