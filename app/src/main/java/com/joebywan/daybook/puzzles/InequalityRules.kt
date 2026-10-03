package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import kotlinx.serialization.Serializable

/** A `<` between two neighbouring squares: the square [lo] holds a smaller digit than [hi]. */
@Serializable
data class Sign(val lo: Int, val hi: Int)

/**
 * The rules of the Inequality square, and its generator.
 *
 * A size-n grid holds 1..n once in every row and column, and every [Sign] between two neighbours is
 * true. Squares are numbered row by row. A digit's bit in a candidate mask is `1 shl digit`.
 *
 * The generator lays down a random Latin square, shows *every* sign, adds given digits until the
 * teacher's own ladder ([InequalityTeacher.solvesByLogic]) finishes the board, then takes back every
 * given and sign the rest does not need, in random order. So a board is irredundant (nothing left to
 * remove) and a person can always finish it by the named steps, never by guessing, which is what
 * keeps the hints from needing their fallback. Logic that finishes a board proves one answer only if
 * the logic is sound, so the shipped board is proved again by an exhaustive search ([countSolutions])
 * that shares nothing with it but the rules, and `InequalityRulesTest` checks both against a third,
 * naive solver.
 */
internal object InequalityLogic {

    fun sizeFor(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.STANDARD -> 4
        Difficulty.HARD -> 5
        Difficulty.EXPERT -> 6
    }

    fun fullMask(n: Int): Int = (1 shl (n + 1)) - 2

    /** Every pair of neighbours: row pairs first, then column pairs, each in index order. */
    fun neighbours(n: Int): List<Pair<Int, Int>> = buildList {
        for (r in 0 until n) for (c in 0 until n - 1) add(r * n + c to r * n + c + 1)
        for (r in 0 until n - 1) for (c in 0 until n) add(r * n + c to (r + 1) * n + c)
    }

    /** Filled squares that repeat a digit in their row or column. */
    fun clashes(n: Int, cells: List<Int>): Set<Int> {
        val bad = mutableSetOf<Int>()
        for (i in cells.indices) {
            if (cells[i] == 0) continue
            for (j in i + 1 until cells.size) {
                if (cells[j] == cells[i] && (i / n == j / n || i % n == j % n)) { bad += i; bad += j }
            }
        }
        return bad
    }

    /** Indices of signs with both ends filled and the order wrong. */
    fun brokenSigns(cells: List<Int>, signs: List<Sign>): Set<Int> =
        signs.indices.filterTo(mutableSetOf()) { k ->
            val lo = cells[signs[k].lo]
            val hi = cells[signs[k].hi]
            lo != 0 && hi != 0 && lo >= hi
        }

    /** The rules and nothing else: a full grid, a Latin square, every sign true. */
    fun isSolved(n: Int, cells: List<Int>, signs: List<Sign>): Boolean =
        cells.size == n * n && cells.all { it in 1..n } && clashes(n, cells).isEmpty() &&
            brokenSigns(cells, signs).isEmpty()

    // ---- generator ---------------------------------------------------------------------------------

    /** Retries with `Rng(seed + attempt)`; none has ever been needed (see `FallbackTest`). */
    const val ATTEMPTS = 8

    fun randomSquare(n: Int, rng: Rng): List<Int> {
        val g = IntArray(n * n)
        fun fill(i: Int): Boolean {
            if (i == n * n) return true
            val r = i / n
            val c = i % n
            for (d in rng.shuffled((1..n).toList())) {
                if ((0 until c).any { g[r * n + it] == d } || (0 until r).any { g[it * n + c] == d }) continue
                g[i] = d
                if (fill(i + 1)) return true
            }
            g[i] = 0
            return false
        }
        fill(0)
        return g.toList()
    }

    /** Null when nothing was proved. Pure in (seed, difficulty); only lists reach the `Rng`. */
    fun generateVerified(seed: Long, difficulty: Difficulty): InequalityState? {
        val n = sizeFor(difficulty)
        for (attempt in 0 until ATTEMPTS) {
            val rng = Rng(seed + attempt)
            val solution = randomSquare(n, rng)
            val all = neighbours(n).map { (a, b) -> if (solution[a] < solution[b]) Sign(a, b) else Sign(b, a) }
            val on = BooleanArray(all.size) { true }
            fun shown() = all.filterIndexed { k, _ -> on[k] }
            val cells = MutableList(n * n) { 0 }
            fun finishes() = InequalityTeacher.solvesByLogic(n, cells, shown())

            // Every sign shown; digits only until the ladder finishes (all of them at worst).
            if (!finishes()) {
                for (i in rng.shuffled((0 until n * n).toList())) {
                    cells[i] = solution[i]
                    if (finishes()) break
                }
            }
            // Take back whatever the rest already implies, digits and signs alike, in random order.
            val items = rng.shuffled((0 until n * n).filter { cells[it] != 0 } + all.indices.map { -1 - it })
            for (item in items) {
                if (item >= 0) {
                    cells[item] = 0
                    if (!finishes()) cells[item] = solution[item]
                } else {
                    on[-1 - item] = false
                    if (!finishes()) on[-1 - item] = true
                }
            }
            val signs = shown()
            if (countSolutions(n, cells, signs) != 1) continue
            return InequalityState(
                size = n,
                givens = cells.map { it != 0 },
                cells = cells.toList(),
                solution = solution,
                signs = signs,
            )
        }
        return null
    }

    /**
     * A fixed board for a seed nothing proved (none has ever reached it): the cyclic square with
     * its first row given and every sign shown. `InequalityRulesTest` proves it has one answer on each size.
     */
    fun lastResort(difficulty: Difficulty): InequalityState {
        val n = sizeFor(difficulty)
        val solution = List(n * n) { (it / n + it % n) % n + 1 }
        return InequalityState(
            size = n,
            givens = List(n * n) { it < n },
            cells = List(n * n) { if (it < n) solution[it] else 0 },
            solution = solution,
            signs = neighbours(n).map { (a, b) -> if (solution[a] < solution[b]) Sign(a, b) else Sign(b, a) },
        )
    }

    // ---- proof -------------------------------------------------------------------------------------

    /**
     * How many answers the grid has, counted up to [limit]. Exhaustive: there is no node budget, so
     * "1" means one answer and a caller cannot read "gave up" as "unique". Candidate masks, strike a
     * settled digit from its row and column, bound each side of a sign, then split on the squarest
     * unsettled square. [given] holds 0 for an empty square.
     */
    fun countSolutions(n: Int, given: List<Int>, signs: List<Sign>, limit: Int = 2): Int {
        val full = fullMask(n)
        val cand = IntArray(n * n) { if (given[it] == 0) full else 1 shl given[it] }
        var found = 0

        fun single(m: Int) = m and (m - 1) == 0

        fun settle(c: IntArray): Boolean {
            do {
                var changed = false
                for (i in c.indices) {
                    if (!single(c[i])) continue
                    for (j in c.indices) {
                        if (j == i || (j / n != i / n && j % n != i % n) || c[j] and c[i] == 0) continue
                        c[j] = c[j] and c[i].inv()
                        if (c[j] == 0) return false
                        changed = true
                    }
                }
                for (s in signs) {
                    // lo must sit below hi's highest digit; hi above lo's lowest.
                    val hiTop = 31 - c[s.hi].countLeadingZeroBits()
                    val loBottom = c[s.lo].countTrailingZeroBits()
                    val newLo = c[s.lo] and ((1 shl hiTop) - 1)
                    val newHi = c[s.hi] and ((1 shl (loBottom + 1)) - 1).inv()
                    if (newLo == 0 || newHi == 0) return false
                    if (newLo != c[s.lo] || newHi != c[s.hi]) { c[s.lo] = newLo; c[s.hi] = newHi; changed = true }
                }
            } while (changed)
            return true
        }

        fun search(c: IntArray) {
            if (!settle(c)) return
            var best = -1
            for (i in c.indices) if (!single(c[i]) && (best < 0 || c[i].countOneBits() < c[best].countOneBits())) best = i
            if (best < 0) { found++; return }
            for (d in 1..n) {
                if (c[best] and (1 shl d) == 0) continue
                val next = c.copyOf()
                next[best] = 1 shl d
                search(next)
                if (found >= limit) return
            }
        }
        search(cand)
        return found
    }
}
