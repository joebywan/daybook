package com.joebywan.daybook

/**
 * An independent count of a Loop board's answers, sharing nothing with `LoopLogic` or `LoopEdges`: no edges, no dots,
 * no propagation, no loop rule. It works on squares instead. A simple closed loop along the squares' sides is exactly
 * the boundary of a set of squares (the inside) that is one piece, whose outside is one piece reaching the board's
 * edge, and that never touches itself only at a corner; a square's number is how many of its four neighbours (beyond
 * the board counts as outside) are on the other side. So it colours every square in or out, in reading order, checks a
 * number once its square's neighbours are all coloured and a corner-touch once its 2x2 is, and checks the two pieces
 * at the end. No budget: it walks until it has found [limit] answers or tried every colouring.
 *
 * Numbers are `0`-`3` or `.`, row by row. Returns the answers found, each as its inside squares (`1`/`0`).
 */
object LoopOracle {

    fun answers(n: Int, clues: String, limit: Int = 2): List<String> {
        val inside = IntArray(n * n)
        val out = ArrayList<String>()

        fun at(r: Int, c: Int) = if (r in 0 until n && c in 0 until n) inside[r * n + c] else 0

        fun keeps(r: Int, c: Int): Boolean {
            val k = clues[r * n + c]
            if (k == '.') return true
            val me = at(r, c)
            val differ = listOf(at(r - 1, c), at(r + 1, c), at(r, c - 1), at(r, c + 1)).count { it != me }
            return differ == k - '0'
        }

        fun onePiece(colour: Int, mustReachEdge: Boolean): Boolean {
            val seen = BooleanArray(n * n)
            val queue = ArrayDeque<Int>()
            if (mustReachEdge) {
                for (i in 0 until n * n) {
                    val r = i / n
                    val c = i % n
                    if (inside[i] == colour && (r == 0 || c == 0 || r == n - 1 || c == n - 1)) { seen[i] = true; queue.add(i) }
                }
            } else {
                val first = (0 until n * n).firstOrNull { inside[it] == colour } ?: return false
                seen[first] = true
                queue.add(first)
            }
            while (queue.isNotEmpty()) {
                val i = queue.removeFirst()
                val r = i / n
                val c = i % n
                for ((dr, dc) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                    val rr = r + dr
                    val cc = c + dc
                    if (rr !in 0 until n || cc !in 0 until n) continue
                    val j = rr * n + cc
                    if (!seen[j] && inside[j] == colour) { seen[j] = true; queue.add(j) }
                }
            }
            return (0 until n * n).all { inside[it] != colour || seen[it] }
        }

        fun walk(k: Int) {
            if (out.size >= limit) return
            if (k == n * n) {
                for (c in 0 until n) if (!keeps(n - 1, c)) return
                if (!onePiece(1, mustReachEdge = false) || !onePiece(0, mustReachEdge = true)) return
                out.add(inside.joinToString(""))
                return
            }
            val r = k / n
            val c = k % n
            for (v in 0..1) {
                inside[k] = v
                // Touching only at a corner: a 2x2 coloured like a chessboard.
                val pinch = r > 0 && c > 0 && at(r - 1, c - 1) == v && at(r - 1, c) != v && at(r, c - 1) != v
                // The square above now has every neighbour coloured, and so does the one before it on the last row.
                val ok = !pinch && (r == 0 || keeps(r - 1, c)) && (r < n - 1 || c == 0 || keeps(r, c - 1))
                if (ok) walk(k + 1)
                if (out.size >= limit) break
            }
            inside[k] = 0
        }

        walk(0)
        return out
    }

    /** The loop an answer's inside squares make, as a `0`/`1` edge string in the board's own edge order. */
    fun loopOf(n: Int, inside: String): String {
        fun at(r: Int, c: Int) = if (r in 0 until n && c in 0 until n) inside[r * n + c] else '0'
        val m = n + 1
        val sb = StringBuilder()
        // Horizontal edges: row r of dots, between columns c and c + 1: the squares above and below it.
        for (r in 0..n) for (c in 0 until n) sb.append(if (at(r - 1, c) != at(r, c)) '1' else '0')
        // Vertical edges: between rows r and r + 1 of dots, column c: the squares left and right of it.
        for (r in 0 until n) for (c in 0 until m) sb.append(if (at(r, c - 1) != at(r, c)) '1' else '0')
        return sb.toString()
    }
}
