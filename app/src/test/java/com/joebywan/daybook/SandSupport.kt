package com.joebywan.daybook

/**
 * Sand's rules written again for the tests, from the spec and not from `SandLogic`, plus a search that shares nothing
 * with `SandLogic.solve`: breadth first over raw positions (tubes in their places, no canonical sort, no pruning).
 * Tubes are bottom to top. Used to check witnesses (a path replays to sorted) and claims of "unsortable".
 */
object SandSupport {

    fun sorted(t: List<List<Int>>, h: Int) = t.all { it.isEmpty() || (it.size == h && it.toSet().size == 1) }

    fun legal(t: List<List<Int>>, h: Int, a: Int, b: Int) =
        a != b && t[a].isNotEmpty() && t[b].size < h && (t[b].isEmpty() || t[b].last() == t[a].last())

    /** Pours one layer at a time until the top changes or the target fills: the definition, not a formula. */
    fun pour(t: List<List<Int>>, h: Int, a: Int, b: Int): List<List<Int>> {
        require(legal(t, h, a, b))
        val x = t[a].toMutableList()
        val y = t[b].toMutableList()
        val c = x.last()
        while (x.isNotEmpty() && x.last() == c && y.size < h) y += x.removeAt(x.size - 1)
        return t.mapIndexed { i, v -> when (i) { a -> x.toList(); b -> y.toList(); else -> v } }
    }

    fun pours(t: List<List<Int>>, h: Int) = t.indices.flatMap { a -> t.indices.filter { b -> legal(t, h, a, b) }.map { a to it } }

    /** Whether [path] is legal pour by pour from [t] and ends sorted. */
    fun replays(t: List<List<Int>>, h: Int, path: List<Pair<Int, Int>>): Boolean {
        var cur = t
        for ((a, b) in path) {
            if (!legal(cur, h, a, b)) return false
            cur = pour(cur, h, a, b)
        }
        return sorted(cur, h)
    }

    /** True when some sequence of pours sorts [t], false when none does; null past [limit] positions (unknown). */
    fun sortable(t: List<List<Int>>, h: Int, limit: Int = 2_000_000): Boolean? {
        if (sorted(t, h)) return true
        val seen = HashSet<List<List<Int>>>()
        seen += t
        var frontier = listOf(t)
        while (frontier.isNotEmpty()) {
            val next = ArrayList<List<List<Int>>>()
            for (p in frontier) for ((a, b) in pours(p, h)) {
                val q = pour(p, h, a, b)
                if (!seen.add(q)) continue
                if (sorted(q, h)) return true
                if (seen.size > limit) return null
                next += q
            }
            frontier = next
        }
        return false
    }
}
