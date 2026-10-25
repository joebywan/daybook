package com.joebywan.daybook

/**
 * Snap's lines, found by a solver that shares nothing with the generator's: depth-first from the
 * square numbered 1 (or from a given start of a line), no node budget, no viability pruning, and the
 * numbers checked only once a full line exists rather than pruned into the walk. The generator's
 * own solver is the opposite on every one of those points, so the two cannot agree by sharing a
 * mistake. The rule checked is the win check's (every number met, in order); "ending on the highest"
 * follows for a board unique under it. The price is speed: an Expert board takes several seconds.
 */
object SnapOracle {

    /** Every finished line on a [width] x [height] board with [waypoints] that begins with [prefix], up to [cap]. */
    fun answers(
        width: Int,
        height: Int,
        waypoints: List<Int>,
        prefix: List<Int> = listOf(waypoints.indexOf(1)),
        cap: Int = Int.MAX_VALUE,
    ): List<List<Int>> {
        val n = width * height
        val numbers = waypoints.count { it > 0 }
        val trail = prefix.toMutableList()
        val seen = BooleanArray(n).also { s -> prefix.forEach { s[it] = true } }
        val found = mutableListOf<List<Int>>()

        fun walk() {
            if (found.size >= cap) return
            if (trail.size == n) {
                val order = trail.mapNotNull { c -> waypoints[c].takeIf { it > 0 } }
                if (order == (1..numbers).toList()) found += trail.toList()
                return
            }
            val cell = trail.last()
            val r = cell / width
            val c = cell % width
            val next = buildList {
                if (r > 0) add(cell - width)
                if (r < height - 1) add(cell + width)
                if (c > 0) add(cell - 1)
                if (c < width - 1) add(cell + 1)
            }
            for (m in next) {
                if (seen[m]) continue
                seen[m] = true
                trail += m
                walk()
                trail.removeAt(trail.lastIndex)
                seen[m] = false
            }
        }

        walk()
        return found
    }
}
