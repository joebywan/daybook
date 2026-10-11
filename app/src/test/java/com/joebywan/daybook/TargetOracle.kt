package com.joebywan.daybook

/**
 * An independent checker for Target, written on a different principle from `TargetLogic.table`: not a depth-first walk
 * of one board with pruning, but a breadth-first sweep of *positions*. A position is the sorted list of tiles on the
 * table; each round takes every position of the round before and every ordered pair of its tiles with every operator
 * (both orders, no merging of equal tiles, no skipping of x1 or /1), and a value's minimum is the first round some
 * position holds it. Positions are remembered, so no position is expanded twice. It shares only the rules as the
 * owner states them (whole numbers, 1 to 9,999, no zero, no negatives, no fractions).
 */
object TargetOracle {

    const val CAP = 9999L

    /** The answers of `a op b` the rules allow (a and b are ordered here: 'a - b' needs a > b, 'a / b' needs b to divide a). */
    private fun results(a: Int, b: Int): List<Int> {
        val out = mutableListOf<Long>()
        out += a.toLong() + b
        out += a.toLong() * b
        if (a > b) out += (a - b).toLong()
        if (b != 0 && a % b == 0) out += (a / b).toLong()
        return out.filter { it in 1..CAP }.map { it.toInt() }
    }

    /** Fewest operations to a tile of each value 1..[maxValue] (index = value), -1 when none; 0 for the starting tiles. */
    fun minOps(start: List<Int>, maxValue: Int = 999): IntArray {
        val best = IntArray(maxValue + 1) { -1 }
        var layer: Set<List<Int>> = setOf(start.sorted())
        val seen = HashSet<List<Int>>(layer)
        var round = 0
        while (layer.isNotEmpty()) {
            for (position in layer) for (v in position) if (v <= maxValue && best[v] == -1) best[v] = round
            val next = HashSet<List<Int>>()
            for (position in layer) {
                if (position.size < 2) continue
                for (i in position.indices) for (j in position.indices) {
                    if (i == j) continue
                    val rest = position.filterIndexed { k, _ -> k != i && k != j }
                    for (r in results(position[i], position[j])) {
                        val p = (rest + r).sorted()
                        if (seen.add(p)) next += p
                    }
                }
            }
            layer = next
            round++
        }
        return best
    }

    /** Whether any sequence of operations from [tiles] makes a tile equal to [target]. */
    fun reachable(tiles: List<Int>, target: Int): Boolean = minOps(tiles, maxOf(target, 1))[target] >= 0
}
