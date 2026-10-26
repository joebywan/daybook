package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.Verdict
import kotlinx.serialization.Serializable

/** One domino of the double-six set: [a] <= [b], each 0..6. */
@Serializable
data class Domino(val a: Int, val b: Int) {
    val isDouble: Boolean get() = a == b
}

@Serializable
enum class RuleKind { SUM, LESS, MORE, EQUAL, DIFFERENT }

/** A region's rule. [n] is the total for [RuleKind.SUM], the bound for [RuleKind.LESS] / [RuleKind.MORE], unused otherwise. */
@Serializable
data class RegionRule(val kind: RuleKind, val n: Int = 0)

/** Where a domino lies: [first] shows the domino's `a`, [second] its `b`. Squares are `row * width + col`. */
@Serializable
data class Placement(val first: Int, val second: Int)

/**
 * A Dominoes board in progress (`docs/DOMINO_SPEC.md`). The shape, the regions and their rules,
 * the tray (sorted; its slots never move), and where the player has laid each domino. [answer] is
 * the proved unique answer, per tray index; it is read for mistakes, the fallback and tests only.
 * Field names and meanings are fixed once shipped (saves).
 */
@Serializable
data class DominoState(
    val width: Int,
    val height: Int,
    val open: List<Boolean>,
    val regionOf: List<Int>,
    val rules: List<RegionRule>,
    val tray: List<Domino>,
    val placed: List<Placement?>,
    val answer: List<Placement>,
    override val moves: Int = 0,
) : PuzzleState {

    /** Every domino down and every region's rule kept: the rules, never [answer]. */
    override val solved: Boolean get() = DominoLogic.isSolved(this)

    /** What the player can see: everything but [answer]. */
    internal val layout: DominoLogic.Layout get() = DominoLogic.Layout(width, height, open, regionOf, rules, tray)

    /** Square [q] is on the board and no domino covers it. */
    internal fun isFree(q: Int): Boolean = open[q] && DominoLogic.dominoAt(this, q) < 0
}

/**
 * The rules, the exhaustive count and the generator. Pure Kotlin: no platform, no hash container
 * whose order could reach the [Rng] or any choice (every loop walks squares, regions or the tray in
 * index order), so a seed makes the same board on the phone and in the browser.
 *
 * ## Generation
 * Shape and tiling grow together, a domino at a time; distinct dominoes from the double-six set go
 * on the tiles, either way round; the shape is cut into regions and each region's rule is read off
 * the answer. Then the board is tightened (an unruled square becomes a one-square sum, or a square
 * is split off a region) until the teacher's ladder, capped at the tier's deepest step, places every
 * domino, and loosened again in random order (a sum to a bound or to all-different, a rule dropped,
 * two regions merged) while the ladder still finishes it. A board ships only if it needs exactly its
 * tier's deepest step and [solutions] proves one answer.
 */
internal object DominoLogic {

    /** The visible board: everything a solver may see. The answer is not in it. */
    class Layout(
        val width: Int,
        val height: Int,
        val open: List<Boolean>,
        val regionOf: List<Int>,
        val rules: List<RegionRule>,
        val tray: List<Domino>,
    ) {
        val size: Int get() = width * height

        /** Squares of each region, ascending. */
        val regionSquares: List<IntArray> by lazy {
            List(rules.size) { r -> (0 until size).filter { regionOf[it] == r }.toIntArray() }
        }

        /** Open neighbours of each square, ascending. */
        val neighbours: List<IntArray> by lazy { List(size) { neighboursOf(width, height, open, it) } }
    }

    const val MAX_PIPS = 6

    /** The double-six set, sorted. */
    val SET: List<Domino> = (0..MAX_PIPS).flatMap { a -> (a..MAX_PIPS).map { b -> Domino(a, b) } }

    fun neighboursOf(width: Int, height: Int, open: List<Boolean>, i: Int): IntArray {
        val r = i / width
        val c = i % width
        val out = ArrayList<Int>(4)
        if (r > 0 && open[i - width]) out.add(i - width)
        if (c > 0 && open[i - 1]) out.add(i - 1)
        if (c < width - 1 && open[i + 1]) out.add(i + 1)
        if (r < height - 1 && open[i + width]) out.add(i + width)
        return out.toIntArray()
    }

    fun adjacent(width: Int, x: Int, y: Int): Boolean =
        (x / width == y / width && (x - y == 1 || y - x == 1)) || x - y == width || y - x == width

    // ---- the board -------------------------------------------------------------------------------

    /** Pips on each square, -1 where nothing lies. */
    fun pipsOf(s: DominoState): IntArray = pipsOf(s.width * s.height, s.tray, s.placed)

    fun pipsOf(size: Int, tray: List<Domino>, placed: List<Placement?>): IntArray {
        val out = IntArray(size) { -1 }
        for (i in tray.indices) {
            val p = placed[i] ?: continue
            out[p.first] = tray[i].a
            out[p.second] = tray[i].b
        }
        return out
    }

    /** Whether tray domino [i] may go at [p]: still in the tray, two open, empty, neighbouring squares. */
    fun canPlace(s: DominoState, i: Int, p: Placement): Boolean {
        if (i !in s.tray.indices || s.placed[i] != null) return false
        val n = s.width * s.height
        if (p.first !in 0 until n || p.second !in 0 until n || p.first == p.second) return false
        if (!s.open[p.first] || !s.open[p.second] || !adjacent(s.width, p.first, p.second)) return false
        val pips = pipsOf(s)
        return pips[p.first] < 0 && pips[p.second] < 0
    }

    /** Lays tray domino [i] at [p] (one move), whatever the rules say; the same state if [canPlace] refuses. */
    fun place(s: DominoState, i: Int, p: Placement): DominoState {
        if (!canPlace(s, i, p)) return s
        return s.copy(placed = s.placed.toMutableList().also { it[i] = p }, moves = s.moves + 1)
    }

    /** Takes tray domino [i] back to its slot (one move); the same state if it is not on the board. */
    fun lift(s: DominoState, i: Int): DominoState {
        if (i !in s.tray.indices || s.placed[i] == null) return s
        return s.copy(placed = s.placed.toMutableList().also { it[i] = null }, moves = s.moves + 1)
    }

    /** Turns placed domino [i] round where it lies (one move); the same state for a double or a domino in the tray. */
    fun flip(s: DominoState, i: Int): DominoState {
        val p = s.placed.getOrNull(i) ?: return s
        if (s.tray[i].isDouble) return s
        return s.copy(placed = s.placed.toMutableList().also { it[i] = Placement(p.second, p.first) }, moves = s.moves + 1)
    }

    /** The tray index of the domino covering [square], or -1. */
    fun dominoAt(s: DominoState, square: Int): Int =
        s.placed.indexOfFirst { it != null && (it.first == square || it.second == square) }

    // ---- the rules -------------------------------------------------------------------------------

    /** Whether a fully covered region showing [pips] keeps [rule]. */
    fun ruleHolds(rule: RegionRule, pips: List<Int>): Boolean = when (rule.kind) {
        RuleKind.SUM -> pips.sum() == rule.n
        RuleKind.LESS -> pips.sum() < rule.n
        RuleKind.MORE -> pips.sum() > rule.n
        RuleKind.EQUAL -> pips.all { it == pips[0] }
        RuleKind.DIFFERENT -> allDifferent(pips)
    }

    /** No value shown twice (a bit mask, not a set: nothing hashed). */
    fun allDifferent(pips: List<Int>): Boolean {
        var seen = 0
        for (v in pips) {
            if (seen shr v and 1 == 1) return false
            seen = seen or (1 shl v)
        }
        return true
    }

    /**
     * Whether no way of filling the region's [open] uncovered squares with 0..6 can keep [rule], given
     * the [pips] already on its covered squares. Ignores the tray: cheap and honest, the red feedback.
     */
    fun ruleBroken(rule: RegionRule, pips: List<Int>, open: Int): Boolean = ruleBroken(rule, pips.toIntArray(), pips.size, open)

    /** [ruleBroken] on the first [count] of [pips]: the ladder's allocation-free form. */
    fun ruleBroken(rule: RegionRule, pips: IntArray, count: Int, open: Int): Boolean {
        var sum = 0
        var seen = 0
        var repeat = false
        for (i in 0 until count) {
            sum += pips[i]
            if (seen shr pips[i] and 1 == 1) repeat = true
            seen = seen or (1 shl pips[i])
        }
        return when (rule.kind) {
            RuleKind.SUM -> sum > rule.n || sum + MAX_PIPS * open < rule.n
            RuleKind.LESS -> sum >= rule.n
            RuleKind.MORE -> sum + MAX_PIPS * open <= rule.n
            RuleKind.EQUAL -> seen and (seen - 1) != 0
            RuleKind.DIFFERENT -> repeat || count + open > MAX_PIPS + 1
        }
    }

    /** The covered pips of a region's [squares] and its uncovered square count, on [pips]. */
    private fun regionView(squares: IntArray, pips: IntArray): Pair<List<Int>, Int> {
        val covered = squares.filter { pips[it] >= 0 }.map { pips[it] }
        return covered to squares.size - covered.size
    }

    /** Region [r] is fully covered and keeps its rule (the green badge). */
    fun regionHolds(layout: Layout, pips: IntArray, r: Int): Boolean {
        val (covered, open) = regionView(layout.regionSquares[r], pips)
        return open == 0 && ruleHolds(layout.rules[r], covered)
    }

    /** Region [r] can no longer keep its rule whatever goes on its empty squares (the red badge). */
    fun regionBroken(layout: Layout, pips: IntArray, r: Int): Boolean {
        val (covered, open) = regionView(layout.regionSquares[r], pips)
        return ruleBroken(layout.rules[r], covered, open)
    }

    /** Every domino down on two neighbouring open squares, none overlapping, and every region's rule kept. */
    fun isSolved(s: DominoState): Boolean {
        val n = s.width * s.height
        val seen = BooleanArray(n)
        for (p in s.placed) {
            if (p == null) return false
            for (q in intArrayOf(p.first, p.second)) {
                if (q !in 0 until n || !s.open[q] || seen[q]) return false
                seen[q] = true
            }
            if (!adjacent(s.width, p.first, p.second)) return false
        }
        val pips = pipsOf(s)
        return s.layout.regionSquares.indices.all { r -> ruleHolds(s.rules[r], s.layout.regionSquares[r].map { pips[it] }) }
    }

    // ---- the exhaustive count ---------------------------------------------------------------------


    /**
     * Every way to lay the whole tray on [layout] keeping every rule, counted up to [limit], with no
     * node budget. Branches on the empty square with the fewest options (partner, domino, way round),
     * pruning each option by every region it touches: covered sums against SUM / LESS / MORE with the
     * smallest and largest values the unused dominoes hold, EQUAL and DIFFERENT on what is covered.
     * A double counts once, not once each way round.
     */
    fun solutions(layout: Layout, limit: Int = 2): Verdict {
        val n = layout.size
        val tray = layout.tray
        val d = tray.size
        val openCount = (0 until n).count { layout.open[it] }
        if (openCount != 2 * d) return Verdict.None
        val pips = IntArray(n) { -1 }
        val used = BooleanArray(d)
        val at = arrayOfNulls<Placement>(d)
        val regSum = IntArray(layout.rules.size)
        val regOpen = IntArray(layout.rules.size) { layout.regionSquares[it].size }
        val regCount = Array(layout.rules.size) { IntArray(MAX_PIPS + 1) }
        var found = 0
        var answer: List<Placement>? = null

        fun regionOk(r: Int, lo: Int, hi: Int): Boolean {
            val rule = layout.rules[r]
            val sum = regSum[r]
            val open = regOpen[r]
            val counts = regCount[r]
            return when (rule.kind) {
                RuleKind.SUM -> sum + lo * open <= rule.n && sum + hi * open >= rule.n
                RuleKind.LESS -> sum + lo * open < rule.n
                RuleKind.MORE -> sum + hi * open > rule.n
                RuleKind.EQUAL -> counts.count { it > 0 } <= 1
                RuleKind.DIFFERENT -> counts.all { it <= 1 }
            }
        }

        fun put(q: Int, v: Int) {
            pips[q] = v
            val r = layout.regionOf[q]
            if (r >= 0) { regSum[r] += v; regOpen[r]--; regCount[r][v]++ }
        }

        fun take(q: Int) {
            val v = pips[q]
            pips[q] = -1
            val r = layout.regionOf[q]
            if (r >= 0) { regSum[r] -= v; regOpen[r]++; regCount[r][v]-- }
        }

        // An option is (partner, tray index, value on s, value on t).
        fun optionsFor(s: Int, lo: Int, hi: Int, out: ArrayList<IntArray>?): Int {
            var count = 0
            for (t in layout.neighbours[s]) {
                if (pips[t] >= 0) continue
                for (k in 0 until d) {
                    if (used[k]) continue
                    val dom = tray[k]
                    val ways = if (dom.isDouble) 1 else 2
                    for (w in 0 until ways) {
                        val vs = if (w == 0) dom.a else dom.b
                        val vt = if (w == 0) dom.b else dom.a
                        put(s, vs); put(t, vt)
                        val rs = layout.regionOf[s]
                        val rt = layout.regionOf[t]
                        val ok = (rs < 0 || regionOk(rs, lo, hi)) && (rt < 0 || rt == rs || regionOk(rt, lo, hi))
                        take(t); take(s)
                        if (ok) {
                            count++
                            out?.add(intArrayOf(t, k, vs, vt))
                        }
                    }
                }
            }
            return count
        }

        fun walk(left: Int) {
            if (found >= limit) return
            if (left == 0) {
                val holds = layout.regionSquares.indices.all { r -> ruleHolds(layout.rules[r], layout.regionSquares[r].map { pips[it] }) }
                if (holds) {
                    found++
                    if (found == 1) answer = List(d) { at[it]!! }
                }
                return
            }
            var lo = MAX_PIPS
            var hi = 0
            for (k in 0 until d) if (!used[k]) {
                lo = minOf(lo, tray[k].a); hi = maxOf(hi, tray[k].b)
            }
            var best = -1
            var bestCount = Int.MAX_VALUE
            for (s in 0 until n) {
                if (!layout.open[s] || pips[s] >= 0) continue
                val c = optionsFor(s, lo, hi, null)
                if (c < bestCount) { best = s; bestCount = c }
                if (c == 0) return
            }
            val options = ArrayList<IntArray>()
            optionsFor(best, lo, hi, options)
            for (o in options) {
                val t = o[0]; val k = o[1]
                put(best, o[2]); put(t, o[3])
                used[k] = true
                at[k] = if (tray[k].isDouble) Placement(minOf(best, t), maxOf(best, t))
                else if (o[2] == tray[k].a) Placement(best, t) else Placement(t, best)
                walk(left - 1)
                used[k] = false
                at[k] = null
                take(t); take(best)
                if (found >= limit) return
            }
        }
        walk(d)
        return Verdict.of(found) { answer!! }
    }

    fun solutions(s: DominoState, limit: Int = 2): Verdict = solutions(s.layout, limit)

    // ---- generation -------------------------------------------------------------------------------

    /**
     * A tier: the box, how many dominoes, how big a region may be drawn, whether all-different may appear,
     * and the ladder level a board must need: exactly [needs] (one needing less is redrawn, and loosening
     * never goes past it).
     */
    class Spec(
        val width: Int,
        val height: Int,
        val minDominoes: Int,
        val maxDominoes: Int,
        val maxRegion: Int,
        val different: Boolean,
        val needs: Int,
    )

    fun specFor(difficulty: Difficulty): Spec = when (difficulty) {
        Difficulty.STANDARD -> Spec(5, 5, 6, 7, 3, different = false, needs = DominoTeacher.LEVEL_REGION_VALUE)
        Difficulty.HARD -> Spec(6, 6, 9, 10, 4, different = true, needs = DominoTeacher.LEVEL_COUNTING)
        Difficulty.EXPERT -> Spec(7, 7, 12, 14, 4, different = true, needs = DominoTeacher.LEVEL_WHAT_IF)
    }

    /** The smallest region the cut draws, on every tier. */
    private const val MIN_REGION = 1

    /** The share of squares, in thousandths, the cut leaves in no region, on every tier. */
    private const val UNRULED_PERMILLE = 150

    /** Draws tried per seed before [generateVerified] gives up. */
    const val ATTEMPTS = 200

    /**
     * A board for [seed] that the tier's ladder finishes needing exactly its deepest step, and that
     * [solutions] proves has one answer; null when none of [ATTEMPTS] draws was. Draw `a` uses
     * `Rng(seed + a)`, so a board that needed three draws still depends on its seed alone.
     */
    fun generateVerified(seed: Long, difficulty: Difficulty): DominoState? {
        for (a in 0 until ATTEMPTS) attempt(seed, difficulty, a)?.let { return it }
        return null
    }

    /** The seed [lastResort] draws from. */
    private const val LAST_RESORT_SEED = 20261022L

    /**
     * What ships for a seed [generateVerified] did not prove: the board of one fixed seed, itself
     * proved (`DominoRulesTest`), so this cannot throw once that test is green.
     */
    fun lastResort(difficulty: Difficulty): DominoState =
        generateVerified(LAST_RESORT_SEED, difficulty) ?: error("the fixed last-resort seed no longer proves a board")

    /** Draw number [a] for [seed], if it is accepted. Public to tests so they can count the redraws. */
    fun attempt(seed: Long, difficulty: Difficulty, a: Int): DominoState? {
        val spec = specFor(difficulty)
        val rng = Rng(seed + a)
        val w = spec.width
        val h = spec.height
        val count = rng.nextInt(spec.minDominoes, spec.maxDominoes)
        val tiles = growTiling(rng, w, h, count) ?: return null
        val n = w * h
        val open = BooleanArray(n)
        tiles.forEach { open[it.first] = true; open[it.second] = true }
        val dominoes = rng.shuffled(SET).take(count)
        val values = IntArray(n) { -1 }
        val tilePlacement = arrayOfNulls<Placement>(count)
        for ((k, tile) in tiles.withIndex()) {
            val dom = dominoes[k]
            val p = if (dom.isDouble || rng.nextBoolean()) tile else Placement(tile.second, tile.first)
            values[p.first] = dom.a
            values[p.second] = dom.b
            tilePlacement[k] = p
        }
        val openList = open.toList()
        val tray = dominoes.sortedWith(compareBy({ it.a }, { it.b }))
        val answer = tray.map { dom ->
            val p = tilePlacement[dominoes.indexOf(dom)]!!
            if (dom.isDouble && p.first > p.second) Placement(p.second, p.first) else p
        }

        val cut = Cut(w, h, openList, values, spec)
        // With every square's value given the ladder must still finish, or no tightening ever will (two
        // tilings that both use only tray dominoes): one check instead of tightening to the end to find out.
        if (!cut.finishesAllGiven(tray)) return null
        cut.carve(rng)
        // One fact at a time. (Doubling batches was measured: the overshoot left Hard boards too easy for their tier.)
        while (!cut.finishes(tray, minOf(spec.needs, DominoTeacher.LEVEL_COUNTING))) {
            if (!cut.tighten(rng)) return null
        }
        cut.loosen(rng, tray)
        val layout = cut.layout(tray)
        val level = DominoTeacher.solveLevel(layout, spec.needs) ?: return null
        if (level < spec.needs) return null
        val verdict = solutions(layout)
        if (verdict !is Verdict.ExactlyOne || verdict.value != answer) return null
        return DominoState(w, h, openList, layout.regionOf, layout.rules, tray, List(tray.size) { null }, answer)
    }

    /**
     * Grows a 4-connected shape a domino at a time inside the box: the first domino anywhere, each
     * later one on two empty neighbouring squares at least one of which touches the shape. The tiles
     * in the order they went down; null if the shape boxed itself in.
     */
    private fun growTiling(rng: Rng, w: Int, h: Int, count: Int): List<Placement>? {
        val n = w * h
        val taken = BooleanArray(n)
        val tiles = ArrayList<Placement>()
        val all = List(n) { it }
        fun pairsFrom(cells: List<Int>): List<Placement> {
            val out = ArrayList<Placement>()
            for (x in cells) {
                if (taken[x]) continue
                if (x % w < w - 1 && !taken[x + 1]) out.add(Placement(x, x + 1))
                if (x / w < h - 1 && !taken[x + w]) out.add(Placement(x, x + w))
            }
            return out
        }
        val first = rng.pick(pairsFrom(all))
        tiles.add(first); taken[first.first] = true; taken[first.second] = true
        while (tiles.size < count) {
            fun touches(q: Int) = (q / w > 0 && taken[q - w]) || (q % w > 0 && taken[q - 1]) ||
                (q % w < w - 1 && taken[q + 1]) || (q / w < h - 1 && taken[q + w])
            val options = pairsFrom(all).filter { touches(it.first) || touches(it.second) }
            if (options.isEmpty()) return null
            val p = rng.pick(options)
            tiles.add(p); taken[p.first] = true; taken[p.second] = true
        }
        return tiles
    }

    /** The regions being cut for one draw, and the rules read off its answer [values]. */
    private class Cut(val w: Int, val h: Int, val open: List<Boolean>, val values: IntArray, val spec: Spec) {
        val n = w * h

        /** Region id per square, -1 for none. Ids are not compact while cutting; [layout] renumbers. */
        val regionOf = IntArray(n) { -1 }
        val rules = ArrayList<RegionRule?>()

        fun members(id: Int): List<Int> = (0 until n).filter { regionOf[it] == id }

        fun neighbours(q: Int) = neighboursOf(w, h, open, q)

        /** The strongest rule [squares] keep: EQUAL when all show one value (and there are two or more), else their SUM. */
        fun readRule(squares: List<Int>): RegionRule {
            val v = squares.map { values[it] }
            return when {
                v.size > 1 && v.all { it == v[0] } -> RegionRule(RuleKind.EQUAL)
                spec.different && v.size > 1 && allDifferent(v) -> RegionRule(RuleKind.DIFFERENT)
                else -> RegionRule(RuleKind.SUM, v.sum())
            }
        }

        fun newRegion(squares: List<Int>): Int {
            val id = rules.size
            squares.forEach { regionOf[it] = id }
            rules.add(readRule(squares))
            return id
        }

        /** From the first uncut square in reading order: no region (a tier share of the time) or one grown to a drawn size. */
        fun carve(rng: Rng) {
            val cut = BooleanArray(n)
            for (i in 0 until n) {
                if (!open[i] || cut[i]) continue
                cut[i] = true
                if (rng.nextInt(1000) < UNRULED_PERMILLE) continue
                val size = rng.nextInt(MIN_REGION, spec.maxRegion)
                val grown = arrayListOf(i)
                while (grown.size < size) {
                    val frontier = grown.flatMap { neighbours(it).toList() }.filter { !cut[it] }.distinct().sorted()
                    if (frontier.isEmpty()) break
                    val q = rng.pick(frontier)
                    cut[q] = true
                    grown.add(q)
                }
                newRegion(grown)
            }
        }

        fun connected(squares: List<Int>): Boolean {
            if (squares.isEmpty()) return true
            val inSet = BooleanArray(n).also { b -> squares.forEach { b[it] = true } }
            val seen = BooleanArray(n)
            val stack = arrayListOf(squares[0])
            seen[squares[0]] = true
            var count = 0
            while (stack.isNotEmpty()) {
                val q = stack.removeAt(stack.lastIndex)
                count++
                for (t in neighbours(q)) if (inSet[t] && !seen[t]) { seen[t] = true; stack.add(t) }
            }
            return count == squares.size
        }

        /**
         * One more fact: an unruled square becomes a one-square sum, or (when every square is ruled) a
         * square is split off a region whose rest stays connected. False when nothing is left to split.
         */
        fun tighten(rng: Rng): Boolean {
            val unruled = (0 until n).filter { open[it] && regionOf[it] < 0 }
            if (unruled.isNotEmpty()) {
                newRegion(listOf(rng.pick(unruled)))
                return true
            }
            val splittable = (0 until n).filter { q ->
                val id = regionOf[q]
                id >= 0 && members(id).let { m -> m.size > 1 && connected(m - q) }
            }
            if (splittable.isEmpty()) return false
            val q = rng.pick(splittable)
            val id = regionOf[q]
            regionOf[q] = -1
            rules[id] = readRule(members(id))
            newRegion(listOf(q))
            return true
        }

        /** The loosenings of region [id], strongest first: drop it, merge it with a neighbour (random order), then its bounds (random order). */
        private fun loosenings(rng: Rng, id: Int): List<IntArray> {
            val rule = rules[id] ?: return emptyList()
            val merges = rules.indices.filter { it != id && rules[it] != null }.map { intArrayOf(MERGE, id, it) }
            val bounds = ArrayList<IntArray>()
            if (rule.kind == RuleKind.SUM) {
                bounds.add(intArrayOf(TO_LESS, id))
                bounds.add(intArrayOf(TO_MORE, id))
                if (spec.different) bounds.add(intArrayOf(TO_DIFFERENT, id))
            }
            return listOf(intArrayOf(DROP, id)) + rng.shuffled(merges) + rng.shuffled(bounds)
        }

        /** Applies loosening [op] if it still makes sense on the board as it stands; the undo, or null. */
        private fun apply(op: IntArray): (() -> Unit)? {
            val id = op[1]
            val rule = rules.getOrNull(id) ?: return null
            val squares = members(id)
            when (op[0]) {
                DROP -> {
                    squares.forEach { regionOf[it] = -1 }
                    rules[id] = null
                    return { squares.forEach { regionOf[it] = id }; rules[id] = rule }
                }
                TO_LESS, TO_MORE, TO_DIFFERENT -> {
                    if (rule.kind != RuleKind.SUM) return null
                    val next = when (op[0]) {
                        TO_LESS -> if (rule.n + 1 > MAX_PIPS * squares.size) null else RegionRule(RuleKind.LESS, rule.n + 1)
                        TO_MORE -> if (rule.n - 1 < 0) null else RegionRule(RuleKind.MORE, rule.n - 1)
                        else -> {
                            val v = squares.map { values[it] }
                            if (v.size > 1 && allDifferent(v)) RegionRule(RuleKind.DIFFERENT) else null
                        }
                    } ?: return null
                    rules[id] = next
                    return { rules[id] = rule }
                }
                else -> {
                    val other = op[2]
                    val otherRule = rules.getOrNull(other) ?: return null
                    val theirs = members(other)
                    if (squares.size + theirs.size > MAX_REGION) return null
                    if (squares.none { q -> neighbours(q).any { regionOf[it] == other } }) return null
                    theirs.forEach { regionOf[it] = id }
                    rules[other] = null
                    rules[id] = readRule(squares + theirs)
                    return { theirs.forEach { regionOf[it] = other }; rules[other] = otherRule; rules[id] = rule }
                }
            }
        }

        /**
         * Each region once, in random order: the strongest loosening of it that the tier's ladder still
         * finishes, if any (a merge's region is not visited again).
         */
        fun loosen(rng: Rng, tray: List<Domino>) {
            for (id in rng.shuffled(rules.indices.toList())) {
                for (op in loosenings(rng, id)) {
                    val undo = apply(op) ?: continue
                    if (finishes(tray, spec.needs)) break
                    undo()
                }
            }
        }

        /** Whether the ladder finishes the board when every square is a one-square sum. */
        fun finishesAllGiven(tray: List<Domino>): Boolean {
            val squares = (0 until n).filter { open[it] }
            val all = Layout(w, h, open, List(n) { squares.indexOf(it) }, squares.map { RegionRule(RuleKind.SUM, values[it]) }, tray)
            return DominoTeacher.solveLevel(all, spec.needs, candidates(all)) != null
        }

        private fun candidates(l: Layout) = candidates ?: DominoCandidates(l, List(l.tray.size) { null }).also { candidates = it }

        /** The candidates depend on the shape and the tray alone, so one table serves every rule tried. */
        private var candidates: DominoCandidates? = null

        fun finishes(tray: List<Domino>, maxLevel: Int): Boolean {
            val l = layout(tray)
            return DominoTeacher.solveLevel(l, maxLevel, candidates(l)) != null
        }

        /** The board as it stands, regions renumbered by their first square in reading order. */
        fun layout(tray: List<Domino>): Layout {
            val renumber = IntArray(rules.size) { -1 }
            val outRules = ArrayList<RegionRule>()
            val outRegion = MutableList(n) { -1 }
            for (q in 0 until n) {
                val id = regionOf[q]
                if (id < 0) continue
                if (renumber[id] < 0) { renumber[id] = outRules.size; outRules.add(rules[id]!!) }
                outRegion[q] = renumber[id]
            }
            return Layout(w, h, open, outRegion, outRules, tray)
        }

        companion object {
            const val DROP = 0
            const val TO_LESS = 1
            const val TO_MORE = 2
            const val TO_DIFFERENT = 3
            const val MERGE = 4
        }
    }

    /** The largest region a merge may make. */
    const val MAX_REGION = 6
}
