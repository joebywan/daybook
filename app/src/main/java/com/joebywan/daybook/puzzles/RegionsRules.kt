package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.countMaskSolutions
import com.joebywan.daybook.core.fullMask
import com.joebywan.daybook.core.orthogonalReading

/**
 * A grid cut into regions, as the rules see it: which squares share a region, how big each region
 * is, and which squares touch. Squares are numbered row by row. Everything here is derived from
 * [region] alone and lists are built in index order, so no hash order can reach the `Rng`.
 */
internal class RegionsLayout(val n: Int, val region: List<Int>) {
    val count: Int = (region.maxOrNull() ?: -1) + 1

    /** The squares of each region, in index order. */
    val members: List<List<Int>> = List(count) { r -> region.indices.filter { region[it] == r } }

    /** The size of the region each square is in, which is also the largest digit it may hold. */
    val sizeOf: IntArray = IntArray(n * n) { members[region[it]].size }

    /** The up to eight squares touching each square, edge or corner, in index order. */
    val around: List<IntArray> = List(n * n) { TentsLogic.around(n, it).toIntArray() }

    /** Everything a digit in a square must differ from: its region-mates and the squares touching it. */
    val peers: List<IntArray> = List(n * n) { i ->
        val seen = BooleanArray(n * n)
        for (j in members[region[i]]) seen[j] = true
        for (j in around[i]) seen[j] = true
        seen[i] = false
        (0 until n * n).filter { seen[it] }.toIntArray()
    }

    /** The largest region: the number of digits the pad needs. */
    val digits: Int = members.maxOfOrNull { it.size } ?: 1

    /** Pairs of regions sharing an edge, each once, smaller id first. */
    fun adjacentRegions(): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until n * n) {
            for (j in intArrayOf(if (i % n < n - 1) i + 1 else -1, if (i / n < n - 1) i + n else -1)) {
                if (j < 0 || region[j] == region[i]) continue
                val p = minOf(region[i], region[j]) to maxOf(region[i], region[j])
                if (p !in out) out += p
            }
        }
        return out
    }
}

/**
 * The rules of Regions, and its generator.
 *
 * The grid is cut into connected regions. A region of size k holds each of 1..k exactly once, and
 * two squares that touch, even at a corner, never hold the same digit. The generator cuts a random
 * partition, lays a random legal fill, gives digits until the teacher's own ladder
 * ([RegionsTeacher.solvesByLogic]) finishes the board, then takes every digit back that the rest
 * already implies, in random order. So a board is irredundant and a person can always finish it by
 * the named steps. Logic that finishes a board proves one answer only if it is sound, so the shipped
 * board is proved again by an exhaustive search ([countSolutions]) that shares nothing with it but
 * the rules; `RegionsRulesTest` checks both against a third, naive solver.
 */
internal object RegionsLogic {

    /** Side of the grid by tier. */
    fun sizeFor(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.STANDARD -> 5
        Difficulty.HARD -> 7
        Difficulty.EXPERT -> 9
    }

    const val MAX_REGION = 5

    /** Filled squares breaking a rule: a digit above its region's size, a repeat in a region, or a touching repeat. */
    fun conflicts(layout: RegionsLayout, cells: List<Int>): Set<Int> {
        val bad = mutableSetOf<Int>()
        for (i in cells.indices) {
            val d = cells[i]
            if (d == 0) continue
            if (d > layout.sizeOf[i]) bad += i
            for (j in layout.peers[i]) if (j > i && cells[j] == d) { bad += i; bad += j }
        }
        return bad
    }

    /** The rules and nothing else: every square holds 1..its region's size and nothing conflicts. */
    fun isSolved(layout: RegionsLayout, cells: List<Int>): Boolean =
        cells.size == layout.n * layout.n && cells.indices.all { cells[it] in 1..layout.sizeOf[it] } &&
            conflicts(layout, cells).isEmpty()

    // ---- generator ---------------------------------------------------------------------------------

    /** Retries with `Rng(seed + attempt)`; `FallbackTest` walks a year to see whether one is ever needed. */
    const val ATTEMPTS = 40

    /**
     * Region sizes the layout draws from, as a weighted list (a size repeated is a size likelier).
     * A digit k appears once in every region of size k or more and never twice in a 2x2 window, so
     * the regions cannot be much smaller than this on average: small regions are not even legal.
     */
    private val SIZES = listOf(3, 4, 4, 5, 5, 5, 5)

    /** Squares of a connected set of up to [k] unassigned squares containing [start], grown at random; fewer if boxed in. */
    private fun grow(n: Int, region: IntArray, start: Int, k: Int, rng: Rng): List<Int> {
        val grown = mutableListOf(start)
        while (grown.size < k) {
            val frontier = mutableListOf<Int>()
            for (c in grown) for (o in orthogonalReading(c, n)) if (region[o] == -1 && o !in grown && o !in frontier) frontier += o
            if (frontier.isEmpty()) break
            grown += rng.pick(frontier)
        }
        return grown
    }

    /**
     * A random partition into connected regions of 2 to [MAX_REGION] squares together with a legal
     * fill of it, or null if the search ran out of tries (the attempt is then redrawn).
     *
     * Drawing the regions first and filling after almost never works (a digit may not touch itself
     * and every region holds a 1, so nearly every random partition has no legal fill at all), so the
     * two are built together: take the first free square in reading order (starting from the most boxed-in one instead failed far more often),
     * grow a region of two or more squares from it, and give
     * that region a digit each, 1..k, none touching an equal one already placed. A region the fill
     * cannot take is redrawn; a square no region can take undoes the one before.
     */
    fun randomBoard(n: Int, rng: Rng): Pair<List<Int>, List<Int>>? {
        repeat(RESTARTS) { drawBoard(n, rng)?.let { return it } }
        return null
    }

    /** Fresh draws per call of [randomBoard]: most draws on a big grid dead-end, and each costs well under a millisecond. */
    private const val RESTARTS = 100
    private const val TRIES = 3000

    private fun drawBoard(n: Int, rng: Rng): Pair<List<Int>, List<Int>>? {
        val region = IntArray(n * n) { -1 }
        val digit = IntArray(n * n)
        var tries = 0

        fun place(cells: List<Int>, at: Int, used: Int): Boolean {
            // Give cells[at], cells[at+1], ... the digits not in `used`, none equal to a touching square.
            if (at == cells.size) return true
            val k = cells.size
            val c = cells[at]
            for (d in rng.shuffled((1..k).toList())) {
                if (used and (1 shl d) != 0) continue
                if (TentsLogic.around(n, c).any { digit[it] == d }) continue
                digit[c] = d
                if (place(cells, at + 1, used or (1 shl d))) return true
            }
            digit[c] = 0
            return false
        }

        fun go(id: Int): Boolean {
            val free = (0 until n * n).filter { region[it] == -1 }
            if (free.isEmpty()) return true
            val start = free[0]
            repeat(12) {
                if (++tries > TRIES) return false
                val k = rng.pick(SIZES)
                val cells = grow(n, region, start, k, rng)
                if (cells.size < 2) return@repeat
                if (!place(cells, 0, 0)) return@repeat
                for (c in cells) region[c] = id
                if (go(id + 1)) return true
                for (c in cells) { region[c] = -1; digit[c] = 0 }
            }
            return false
        }
        if (!go(0)) return null
        // Ids by first square, so a layout is the same however the search happened to number it.
        val renumber = IntArray(n * n) { -1 }
        var k = 0
        return region.map { r -> if (renumber[r] < 0) renumber[r] = k++; renumber[r] } to digit.toList()
    }

    /** Strikes every settled digit from its peers, to a fixpoint. False when some square is left with no digit. */
    private fun settle(layout: RegionsLayout, c: IntArray): Boolean {
        do {
            var changed = false
            for (i in c.indices) {
                if (c[i] == 0) return false
                if (c[i].countOneBits() > 1) continue
                for (j in layout.peers[i]) {
                    if (c[j] and c[i] == 0) continue
                    c[j] = c[j] and c[i].inv()
                    if (c[j] == 0) return false
                    changed = true
                }
            }
        } while (changed)
        return true
    }

    /**
     * The ladder a tier may use ([deepest], a rung of [RegionsTeacher.TECHNIQUES]) and how many locked-candidate steps its
     * walk must take ([needs]), as Tally's `Spec.needs`: a board whose walk takes fewer is redrawn.
     */
    class Spec(val deepest: Int, val needs: Int)

    fun specFor(difficulty: Difficulty): Spec = when (difficulty) {
        Difficulty.STANDARD -> Spec(RegionsTeacher.rung(RegionsTeacher.ONLY_PLACE), 0)
        Difficulty.HARD -> Spec(RegionsTeacher.DEEPEST, HARD_LOCKED)
        Difficulty.EXPERT -> Spec(RegionsTeacher.DEEPEST, EXPERT_LOCKED)
    }

    private const val HARD_LOCKED = 2
    private const val EXPERT_LOCKED = 6

    /** Digs of one fill before it is redrawn. */
    private const val DIGS = 4

    /** Null when nothing was proved. Pure in (seed, difficulty); only lists reach the `Rng`. [attempts] is for tests that count redraws. */
    fun generateVerified(seed: Long, difficulty: Difficulty, attempts: Int = ATTEMPTS): RegionsState? =
        (0 until attempts).firstNotNullOfOrNull { draw(seed + it, sizeFor(difficulty), specFor(difficulty)) }

    /** One attempt: a board drawn from `Rng(seed)` that meets [spec] and is proved, or null. */
    fun draw(seed: Long, n: Int, spec: Spec): RegionsState? {
        val rng = Rng(seed)
        val (region, solution) = randomBoard(n, rng) ?: return null
        val layout = RegionsLayout(n, region)
        val cells = MutableList(n * n) { 0 }
        fun finishes() = RegionsTeacher.solvesByLogic(layout, cells, spec.deepest)

        // The fill is the costly part (about 85 ms of a 9x9 draw), so a dig that misses the gate is dug again, from
        // nothing and in a new order, before the fill is thrown away.
        for (dig in 0 until DIGS) {
            cells.fill(0)
            // Digits only until the ladder finishes (all of them at worst).
            if (!finishes()) {
                for (i in rng.shuffled((0 until n * n).toList())) {
                    cells[i] = solution[i]
                    if (finishes()) break
                }
            }
            // Take back whatever the rest already implies, in random order.
            for (i in rng.shuffled((0 until n * n).filter { cells[it] != 0 })) {
                cells[i] = 0
                if (!finishes()) cells[i] = solution[i]
            }
            if (spec.needs == 0 || (RegionsTeacher.lockedSteps(layout, cells, spec.deepest) ?: 0) >= spec.needs) break
            if (dig == DIGS - 1) return null
        }
        if (countSolutions(layout, cells) != 1) return null
        return RegionsState(
            size = n,
            region = region,
            givens = cells.map { it != 0 },
            cells = cells.toList(),
            solution = solution,
        )
    }

    /** The seed [lastResort] draws from. */
    private const val LAST_RESORT_SEED = 20261011L

    /**
     * What ships for a seed [generateVerified] did not prove: the board of one fixed seed, which is
     * itself proved. It is a pure function of the tier, and `RegionsRulesTest` asserts that it is
     * proved on every tier, so this cannot throw once that test is green.
     */
    fun lastResort(difficulty: Difficulty): RegionsState =
        generateVerified(LAST_RESORT_SEED, difficulty) ?: error("the fixed last-resort seed no longer proves a board")

    // ---- proof -------------------------------------------------------------------------------------

    /**
     * How many answers the board has, counted up to [limit]. Exhaustive: there is no node budget, so
     * "1" means one answer and a caller cannot read "gave up" as "unique". Candidate masks, strike a
     * settled digit from every peer, then split on the squarest unsettled square. [given] holds 0 for
     * an empty square.
     */
    fun countSolutions(layout: RegionsLayout, given: List<Int>, limit: Int = 2): Int {
        val total = layout.n * layout.n
        val cand = IntArray(total) { if (given[it] == 0) fullMask(layout.sizeOf[it]) else (1 shl given[it]) and fullMask(layout.sizeOf[it]) }
        return countMaskSolutions(cand, limit) { settle(layout, it) }
    }
}
