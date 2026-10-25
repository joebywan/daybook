package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import kotlinx.serialization.Serializable

/** The four operators. The order is part of the saves (an enum is written by name, so it is the names that must not change). */
enum class TargetOp(val symbol: String) {
    Add("+"), Sub("−"), Mul("×"), Div("÷"),
}

/**
 * One finished operation: tile [a] and tile [b] (slots) held [va] and [vb], the answer [result] now sits in slot [a]
 * and slot [b] is empty. Enough to take it back exactly (see [TargetState.withoutLast]).
 */
@Serializable
data class TargetMove(val a: Int, val b: Int, val va: Int, val vb: Int, val op: TargetOp, val result: Int) {
    /** The sum as it reads: the bigger tile first for - and /. */
    fun text(): String {
        val flip = (op == TargetOp.Sub || op == TargetOp.Div) && vb > va
        return "${if (flip) vb else va} ${op.symbol} ${if (flip) va else vb} = $result"
    }
}

/**
 * Six numbers and a target. Combine two tiles with + - x / and the answer replaces them; reach the target with any
 * tile. Every board has an answer ([par] is the fewest operations any answer needs, proved by [TargetLogic]),
 * and usually several: `solved` reads the arithmetic, there is no stored solution.
 *
 * [slots] are six places holding a tile each, 0 once the tile was used. An answer goes where the first tile of its
 * operation was. [log] is the operations made, oldest first; it only exists to show them and to take a mistake back.
 * Highlight indices (see [Target]): tiles are slots `0..5`, the operators [Target.OP0] + ordinal, the target [Target.TARGET].
 */
@Serializable
data class TargetState(
    val start: List<Int>,
    val target: Int,
    val par: Int,
    val slots: List<Int> = start,
    val log: List<TargetMove> = emptyList(),
    override val moves: Int = 0,
) : PuzzleState {

    /** The rules: some tile is the target. */
    override val solved: Boolean get() = slots.any { it == target }

    /** The tiles still on the table, in slot order. */
    val tiles: List<Int> get() = slots.filter { it > 0 }

    /** The board after combining slots [a] then [b] with [op] (bigger first for - and /), or null when the rules refuse it. */
    fun combine(a: Int, op: TargetOp, b: Int): TargetState? {
        if (a == b || slots[a] == 0 || slots[b] == 0) return null
        val r = TargetLogic.combine(slots[a], slots[b], op) ?: return null
        val move = TargetMove(a, b, slots[a], slots[b], op, r)
        return copy(
            slots = slots.toMutableList().also { it[a] = r; it[b] = 0 },
            log = log + move,
            moves = moves + 1,
        )
    }

    /** The board with the operation at [index] of the log taken back, and everything made after it (which could not stand without it). */
    fun withoutFrom(index: Int): TargetState {
        var s = copy(slots = start, log = emptyList())
        for (m in log.take(index)) s = s.combine(m.a, m.op, m.b) ?: error("the log does not replay")
        return s.copy(moves = moves + 1)
    }
}

/**
 * Target's rules, and the exhaustive search that proves a board.
 *
 * The search has no budget: it walks every sequence of operations to the end, so "reachable" and "not reachable" are both
 * proofs, and a minimum is a minimum. (There is no truncated case to confuse with them.) Tiles stay whole numbers from 1
 * to [CAP]: a result above it is refused for the player and skipped by the search alike, so the two agree.
 */
internal object TargetLogic {

    /** No tile may go above this (four digits fit a tile). */
    const val CAP = 9999
    const val MIN_TARGET = 100
    const val MAX_TARGET = 999

    /** Table entry for a value no sequence of operations makes. */
    const val NO = -1

    private val LARGE = listOf(25, 50, 75, 100)
    private val SMALL = (1..10).flatMap { listOf(it, it) }
    private const val ATTEMPTS = 24

    class Spec(val large: Int, val parLo: Int, val parHi: Int, val maxRoads: Int? = null)

    /**
     * How many large numbers, the fewest and most operations the target may need, and the most shortest answers it may
     * have ([Roads]; null for any). Measured over a year before the cap: Expert's targets had a median of 13 roads (a
     * tenth over 40); a target with 40 ways to it is a much easier five steps than one with 2. Hard is not capped: 12
     * roads there turned 78% of its targets four steps away into 87%, trading depth for fewer roads.
     */
    fun specFor(difficulty: Difficulty): Spec = when (difficulty) {
        Difficulty.STANDARD -> Spec(3, 3, 4)
        Difficulty.HARD -> Spec(2, 4, 5)
        Difficulty.EXPERT -> Spec(1, 5, 5, maxRoads = 5)
    }

    /** The answer of [op] on two tiles (the bigger first for - and /), or null when it is not a whole number from 1 to [CAP]. */
    fun combine(x: Int, y: Int, op: TargetOp): Int? {
        val hi = maxOf(x, y)
        val lo = minOf(x, y)
        val r = when (op) {
            TargetOp.Add -> x + y
            TargetOp.Mul -> x * y
            TargetOp.Sub -> hi - lo
            TargetOp.Div -> if (hi % lo == 0) hi / lo else 0
        }
        return if (r in 1..CAP) r else null
    }

    /** Why [combine] says no, in a few words for the caption. */
    fun refusal(x: Int, y: Int, op: TargetOp): String {
        val hi = maxOf(x, y)
        val lo = minOf(x, y)
        return when (op) {
            TargetOp.Sub -> "$x − $y would be zero"
            TargetOp.Div -> "$hi ÷ $lo is not whole"
            else -> "Over $CAP"
        }
    }

    /**
     * For every value 1..[MAX_TARGET]: the fewest operations that put a tile of that value on the table from [tiles]
     * (0 when it is already there), or [NO]. Exhaustive over every order of every operation.
     */
    fun table(tiles: List<Int>): IntArray {
        val best = IntArray(MAX_TARGET + 1) { NO }
        for (t in tiles) if (t in 1..MAX_TARGET) best[t] = 0
        walk(tiles.toIntArray(), 0, best, HashSet())
        return best
    }

    /** The fewest operations to a tile equal to [target] from [tiles], or [NO]. */
    fun steps(tiles: List<Int>, target: Int): Int = table(tiles)[target]

    // Same-valued tiles are interchangeable, so only the first of each value is tried in either place (the same
    // set of answers with fewer branches); an operation by 1 (x1, /1) changes nothing, so it is not tried.
    // What a walk adds depends only on its tiles (in any order) and its depth, and within one table the depth follows from
    // how many tiles are left, so a set of tiles already walked is skipped ([seen]: membership only, no order is read).
    // Two tiles are cheaper to walk than to look up. Measured: the same tables, 2.5-3x faster.
    private fun walk(v: IntArray, depth: Int, best: IntArray, seen: HashSet<List<Int>>) {
        val n = v.size
        if (n >= 3 && !seen.add(v.sorted())) return
        for (i in 0 until n) {
            if (repeated(v, i, -1)) continue
            for (j in i + 1 until n) {
                if (repeated(v, j, i)) continue
                for (op in TargetOp.entries) {
                    if ((op == TargetOp.Mul || op == TargetOp.Div) && minOf(v[i], v[j]) == 1) continue
                    val r = combine(v[i], v[j], op) ?: continue
                    if (r <= MAX_TARGET && (best[r] == NO || best[r] > depth + 1)) best[r] = depth + 1
                    if (n > 2) {
                        val w = IntArray(n - 1)
                        var c = 0
                        for (k in 0 until n) if (k != i && k != j) w[c++] = v[k]
                        w[c] = r
                        walk(w, depth + 1, best, seen)
                    }
                }
            }
        }
    }

    /**
     * How many different shortest answers make a value from one board's tiles: its *roads*. An answer is counted as the tree
     * of its operations, so the order of independent steps, the order of a step's two tiles and which of two equal tiles was
     * used do not make a new road; `(3 + 4) + 5` and `3 + (4 + 5)` put different tiles on the table and are two roads. A
     * shortest answer of `p` steps uses exactly `p + 1` tiles and every step feeds the target (one that did not could be
     * dropped), so the roads of a value `p` steps away are the trees over its `(p + 1)`-tile groups that make it.
     * Counts only: no hash container's order is read.
     */
    class Roads(tiles: List<Int>) {
        private val memo = HashMap<List<Int>, HashMap<Int, Int>>()
        private val groups: List<List<Int>> = tiles.sorted().let { s ->
            (1 until (1 shl s.size)).map { mask -> s.filterIndexed { i, _ -> mask shr i and 1 == 1 } }.distinct()
        }

        /** The roads to [target], which [steps] (its [table] entry) away. */
        fun to(target: Int, steps: Int): Int = groups.sumOf { if (it.size == steps + 1) trees(it)[target] ?: 0 else 0 }

        /** Value to the number of trees over every tile of [group] (sorted) that make it. */
        private fun trees(group: List<Int>): HashMap<Int, Int> = memo.getOrPut(group) {
            val out = HashMap<Int, Int>()
            if (group.size == 1) out[group[0]] = 1
            for ((a, b) in splits(group)) {
                val ta = trees(a)
                val tb = trees(b)
                for ((x, cx) in ta) for ((y, cy) in tb) {
                    val n = when {
                        a != b -> cx * cy
                        x < y -> cx * cy
                        x == y -> cx * (cx + 1) / 2
                        else -> 0
                    }
                    if (n > 0) for (op in TargetOp.entries) combine(x, y, op)?.let { out[it] = (out[it] ?: 0) + n }
                }
            }
            out
        }

        /** Every way to cut [group] in two, as value lists, each way once. */
        private fun splits(group: List<Int>): List<Pair<List<Int>, List<Int>>> =
            (1 until (1 shl group.size) - 1).map { mask ->
                val a = group.filterIndexed { i, _ -> mask shr i and 1 == 1 }
                val b = group.filterIndexed { i, _ -> mask shr i and 1 == 0 }
                if (a.joinToString() <= b.joinToString()) a to b else b to a
            }.distinct()
    }


    /** Whether an earlier tile (not [skip]) has the value of tile [i]. */
    private fun repeated(v: IntArray, i: Int, skip: Int): Boolean {
        for (k in 0 until i) if (k != skip && v[k] == v[i]) return true
        return false
    }

    // ---- generation ---------------------------------------------------------------------------

    /**
     * The board when every number is exact: the target is a value no fewer than [Spec.parLo] and no more than [Spec.parHi]
     * operations make, by the table above, and (Expert) one with no more than [Spec.maxRoads] roads: candidates are
     * tried in a shuffled order until one has few enough, else the next attempt draws new numbers. Null when no attempt
     * landed (measured: never, over a year per tier). No hash container's order reaches the [Rng]: candidates are walked in
     * ascending order before the shuffle.
     */
    fun generateVerified(seed: Long, difficulty: Difficulty): TargetState? {
        val spec = specFor(difficulty)
        for (attempt in 0 until ATTEMPTS) {
            val rng = Rng(seed + attempt)
            val tiles = (rng.shuffled(LARGE).take(spec.large) + rng.shuffled(SMALL).take(6 - spec.large)).sortedDescending()
            val table = table(tiles)
            val candidates = (MIN_TARGET..MAX_TARGET).filter { table[it] in spec.parLo..spec.parHi }
            if (candidates.isEmpty()) continue
            val max = spec.maxRoads
            val target = if (max == null) rng.pick(candidates) else {
                val roads = Roads(tiles)
                rng.shuffled(candidates).firstOrNull { roads.to(it, table[it]) <= max } ?: continue
            }
            return TargetState(tiles, target, table[target])
        }
        return null
    }

    /** A board for when nothing was proved: fixed, and itself proved by the same table (a test pins it). */
    fun lastResort(difficulty: Difficulty): TargetState = when (difficulty) {
        Difficulty.STANDARD -> TargetState(listOf(100, 75, 50, 8, 5, 3), 107, 3)
        Difficulty.HARD -> TargetState(listOf(100, 75, 6, 5, 4, 2), 226, 4)
        Difficulty.EXPERT -> TargetState(listOf(75, 10, 9, 7, 4, 3), 922, 5)
    }
}
