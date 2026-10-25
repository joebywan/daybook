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

    /**
     * The roads to [target] in exactly [steps] operations, counted on another principle from `TargetLogic.Roads`: every tile
     * carries the sum that made it, written out (`(3+4)*5`, the two sides of + and x, and of 5 / 5, in text order, so swapping them,
     * reordering independent steps or using the other of two equal tiles writes the same text), and every ordered pair of every
     * position is tried round by round. The roads are the different texts of a tile equal to [target] after [steps] rounds.
     */
    fun roads(start: List<Int>, target: Int, steps: Int): Int {
        var layer: Set<List<Pair<Int, String>>> = setOf(start.map { it to "$it" }.sortedBy { it.second })
        val found = HashSet<String>()
        for (round in 1..steps) {
            val next = HashSet<List<Pair<Int, String>>>()
            for (position in layer) for (i in position.indices) for (j in position.indices) {
                if (i == j) continue
                val (a, ea) = position[i]
                val (b, eb) = position[j]
                val rest = position.filterIndexed { k, _ -> k != i && k != j }
                for ((r, sym) in listOf(a.toLong() + b to "+", a.toLong() * b to "*", (a - b).toLong() to "-", (if (b != 0 && a % b == 0) (a / b).toLong() else 0L) to "/")) {
                    if (r !in 1..CAP) continue
                    val text = if (sym == "+" || sym == "*" || a == b) listOf(ea, eb).sorted().joinToString(sym, "(", ")") else "($ea$sym$eb)"
                    if (round == steps) { if (r.toInt() == target) found += text } else next += (rest + (r.toInt() to text)).sortedBy { it.second }
                }
            }
            layer = next
        }
        return found.size
    }

    /** Whether any sequence of operations from [tiles] makes a tile equal to [target]. */
    fun reachable(tiles: List<Int>, target: Int): Boolean = minOps(tiles, maxOf(target, 1))[target] >= 0
}
