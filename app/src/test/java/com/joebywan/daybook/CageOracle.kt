package com.joebywan.daybook

/**
 * Independent of the generator's prover. The prover works square by square: candidate masks cut down by combination
 * tables, the 45-rule and the placement of forced digits. This one works cage by cage and infers nothing. It writes out
 * every way to fill each cage on its own (different digits, the right total, any given digit kept), then searches
 * for a choice of one filling per cage that no row, column or box objects to: it always takes next the cage with the
 * fewest fillings that still agree with the digits already down, and gives up a branch when some cage has none. Every
 * answer found is counted, to the limit.
 */
object CageOracle {

    /** How many ways [start] (0 = empty) can be completed, counting no further than [limit]. */
    fun count(cageOf: List<Int>, sums: List<Int>, start: List<Int>, limit: Int = 2): Int =
        all(cageOf, sums, start, limit).size

    /** The completions themselves, no more than [limit] of them. */
    fun all(cageOf: List<Int>, sums: List<Int>, start: List<Int>, limit: Int): List<List<Int>> {
        val cages = sums.indices.map { k -> (0 until 81).filter { cageOf[it] == k } }
        fun box(i: Int) = i / 27 * 3 + i % 9 / 3

        // Every filling of each cage on its own.
        val fillings: List<List<IntArray>> = cages.mapIndexed { k, cells ->
            val out = ArrayList<IntArray>()
            val cur = IntArray(cells.size)
            fun fill(j: Int, used: Int, sum: Int) {
                if (j == cells.size) {
                    if (sum == sums[k]) out += cur.copyOf()
                    return
                }
                for (d in 1..9) {
                    if (used and (1 shl d) != 0 || sum + d > sums[k]) continue
                    if (start[cells[j]] != 0 && start[cells[j]] != d) continue
                    cur[j] = d
                    fill(j + 1, used or (1 shl d), sum + d)
                }
            }
            fill(0, 0, 0)
            out
        }

        val rows = IntArray(9)
        val cols = IntArray(9)
        val boxes = IntArray(9)
        for (i in 0 until 81) if (start[i] != 0) {
            val bit = 1 shl start[i]
            // The givens must not already break a rule among themselves.
            if ((rows[i / 9] or cols[i % 9] or boxes[box(i)]) and bit != 0) return emptyList()
            rows[i / 9] = rows[i / 9] or bit
            cols[i % 9] = cols[i % 9] or bit
            boxes[box(i)] = boxes[box(i)] or bit
        }

        fun agrees(k: Int, f: IntArray): Boolean {
            for ((j, i) in cages[k].withIndex()) {
                if (start[i] != 0) continue
                val bit = 1 shl f[j]
                if ((rows[i / 9] or cols[i % 9] or boxes[box(i)]) and bit != 0) return false
            }
            return true
        }

        val placed = BooleanArray(cages.size)
        val cells = IntArray(81) { start[it] }
        val found = ArrayList<List<Int>>()

        fun go() {
            if (found.size >= limit) return
            var best = -1
            var bestLive: List<IntArray> = emptyList()
            for (k in cages.indices) {
                if (placed[k]) continue
                val live = fillings[k].filter { agrees(k, it) }
                if (live.isEmpty()) return
                if (best < 0 || live.size < bestLive.size) {
                    best = k
                    bestLive = live
                }
            }
            if (best < 0) {
                found += cells.toList()
                return
            }
            placed[best] = true
            for (f in bestLive) {
                for ((j, i) in cages[best].withIndex()) {
                    if (start[i] != 0) continue
                    val bit = 1 shl f[j]
                    rows[i / 9] = rows[i / 9] or bit
                    cols[i % 9] = cols[i % 9] or bit
                    boxes[box(i)] = boxes[box(i)] or bit
                    cells[i] = f[j]
                }
                go()
                for ((j, i) in cages[best].withIndex()) {
                    if (start[i] != 0) continue
                    val bit = (1 shl f[j]).inv()
                    rows[i / 9] = rows[i / 9] and bit
                    cols[i % 9] = cols[i % 9] and bit
                    boxes[box(i)] = boxes[box(i)] and bit
                    cells[i] = 0
                }
                if (found.size >= limit) break
            }
            placed[best] = false
        }
        go()
        return found
    }
}
