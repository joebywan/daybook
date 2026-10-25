package com.joebywan.daybook

/**
 * Every mine layout a Mines board's visible facts allow, with no rules of inference at all: each hidden square next to a
 * revealed number is tried as a mine and as safe, in reading order, a branch dropped only once some number can no longer
 * be met; the hidden squares no number touches are counted by binomials against the mines left. Shares nothing with
 * `MinesLogic.nextFact` (no subsets, no what-if, no count rule), so a step the ladder takes is checked by brute force.
 */
internal object MinesOracle {

    /** [layouts] (capped at [Long.MAX_VALUE]); per square, whether some layout has a mine there and whether some has none. */
    class Result(val layouts: Long, val canMine: BooleanArray, val canSafe: BooleanArray)

    private fun around(w: Int, h: Int, i: Int): List<Int> {
        val out = ArrayList<Int>()
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val r = i / w + dr
            val c = i % w + dc
            if (r in 0 until h && c in 0 until w) out += r * w + c
        }
        return out
    }

    private fun choose(n: Int, k: Int): Long {
        if (k < 0 || k > n) return 0
        var r = 1L
        for (j in 0 until k) r = r * (n - j) / (j + 1)
        return r
    }

    /** [num]: a revealed square's number, -1 hidden; [hit]: mines revealed; [total]: the board's mines. */
    fun solve(w: Int, h: Int, num: IntArray, hit: BooleanArray, total: Int): Result {
        val n = w * h
        val hidden = (0 until n).filter { num[it] < 0 && !hit[it] }
        val frontier = hidden.filter { i -> around(w, h, i).any { num[it] >= 0 } }
        val interior = hidden.filter { it !in frontier }
        val left = total - hit.count { it }
        val value = IntArray(n) { if (hit[it]) 1 else 0 } // 1 mine, 0 none, for squares decided so far
        val decided = BooleanArray(n) { num[it] >= 0 || hit[it] }
        val numbers = (0 until n).filter { num[it] >= 0 }
        val canMine = BooleanArray(n)
        val canSafe = BooleanArray(n)
        var layouts = 0L

        fun fine(): Boolean = numbers.all { i ->
            val nb = around(w, h, i)
            val mines = nb.count { decided[it] && value[it] == 1 }
            val open = nb.count { !decided[it] }
            mines <= num[i] && mines + open >= num[i]
        }

        fun go(k: Int, used: Int) {
            if (used > left) return
            if (k == frontier.size) {
                val rest = left - used
                val ways = choose(interior.size, rest)
                if (ways == 0L) return
                layouts = if (layouts > Long.MAX_VALUE - ways) Long.MAX_VALUE else layouts + ways
                for (i in frontier) if (value[i] == 1) canMine[i] = true else canSafe[i] = true
                if (rest > 0) interior.forEach { canMine[it] = true }
                if (rest < interior.size) interior.forEach { canSafe[it] = true }
                return
            }
            val i = frontier[k]
            decided[i] = true
            for (v in 0..1) {
                value[i] = v
                if (fine()) go(k + 1, used + v)
            }
            decided[i] = false
            value[i] = 0
        }
        if (fine()) go(0, 0)
        return Result(layouts, canMine, canSafe)
    }
}
