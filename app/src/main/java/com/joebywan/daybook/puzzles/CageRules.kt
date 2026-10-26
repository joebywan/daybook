package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.Rng
import com.joebywan.daybook.core.Verdict
import com.joebywan.daybook.core.orthogonalReading

/**
 * The rules of Cage Sudoku, its logic, and its generator.
 *
 * A Sudoku grid (squares numbered row by row, a digit's bit in a candidate mask being `1 shl digit`) plus
 * *cages*: groups of squares drawn with a dashed outline and a total. The digits of a cage add up to its
 * total and no digit repeats within it. Cage `k` is the k-th group when cages are numbered by their
 * first square in reading order (`cageOf[square] = k`, `sums[k]` = its total).
 *
 * ## The logic, and why the generator and the teacher are the same thing
 *
 * [Sight] is what a player can work out from the digits on the board and the cages, and nothing else:
 * every empty square's candidates are the digits no row, column, box or cage-mate holds, cut down to a
 * fixpoint by the cage totals ([Group]s: each cage, and the "innies" of each row, column and box, which
 * the 45-rule makes a cage of their own) and by the 45-rule's single "outie" squares. A *placement* is
 * a square with one candidate left or a digit with one place left in a row, column or box. The
 * generator keeps a board only if placements alone finish it ([settle]), and [CageTeacher] explains
 * those same placements, so a generated board never needs the teacher's fallback. Placements are
 * sound, so finishing proves one answer; [solutions] proves it again by exhaustive search anyway, and
 * `CageRulesTest` checks both against a naive solver that shares only the rules.
 *
 * Nothing here reads a hash container's order: every loop walks squares, units and digits in index order.
 */
internal object CageLogic {

    const val ALL = 0b11_1111_1110

    /** Retries with `Rng(seed + attempt)`; the year walk in `FallbackTest` says how many are used. */
    const val ATTEMPTS = 40

    /** Units in the order the teacher names them: nine boxes, nine rows, nine columns. */
    val units: Array<IntArray> = Array(27) { u ->
        when {
            u < 9 -> IntArray(9) { (u / 3 * 3 + it / 3) * 9 + u % 3 * 3 + it % 3 }
            u < 18 -> IntArray(9) { (u - 9) * 9 + it }
            else -> IntArray(9) { it * 9 + (u - 18) }
        }
    }

    /** The three units (box, row, column) holding [cell]. */
    fun unitsOf(cell: Int): IntArray = intArrayOf(cell / 27 * 3 + cell % 9 / 3, 9 + cell / 9, 18 + cell % 9)

    /** Masks of `k` distinct digits that add up to `sum`: `combos[k][sum]`. */
    val combos: Array<Array<IntArray>> by lazy {
        val out = Array(10) { Array(46) { ArrayList<Int>() } }
        for (m in 1 until 512) {
            val mask = m shl 1
            var sum = 0
            for (d in 1..9) if (mask and (1 shl d) != 0) sum += d
            out[mask.countOneBits()][sum] += mask
        }
        Array(10) { k -> Array(46) { s -> out[k][s].toIntArray() } }
    }

    fun sumsOf(cageOf: List<Int>, digits: List<Int>): List<Int> {
        val sums = IntArray(cageOf.max() + 1)
        for (i in cageOf.indices) sums[cageOf[i]] += digits[i]
        return sums.toList()
    }

    /**
     * The rules and nothing else: a finished Sudoku grid in which every cage's digits are all different
     * and add up to its total. Never the stored answer.
     */
    fun isSolved(cells: List<Int>, cageOf: List<Int>, sums: List<Int>): Boolean {
        if (!Sudoku.isSolved(cells)) return false
        val total = IntArray(sums.size)
        val seen = IntArray(sums.size)
        for (i in cells.indices) {
            val k = cageOf[i]
            val bit = 1 shl cells[i]
            if (seen[k] and bit != 0) return false
            seen[k] = seen[k] or bit
            total[k] += cells[i]
        }
        return sums.indices.all { total[it] == sums[it] }
    }

    /**
     * Filled squares the cages already show to be wrong, without any answer: a digit repeated in a cage, a
     * cage over its total, or a full cage that misses it. (Rows, columns and boxes are
     * [SudokuState.conflicts].)
     */
    fun cageConflicts(cells: List<Int>, cageOf: List<Int>, sums: List<Int>): Set<Int> {
        val bad = mutableSetOf<Int>()
        val members = cageCells(cageOf)
        for (k in members.indices) {
            var total = 0
            var open = 0
            var seen = 0
            var repeated = false
            for (i in members[k]) {
                val v = cells[i]
                if (v == 0) { open++; continue }
                total += v
                if (seen and (1 shl v) != 0) repeated = true
                seen = seen or (1 shl v)
            }
            if (repeated) {
                for (i in members[k]) if (cells[i] != 0 && members[k].count { cells[it] == cells[i] } > 1) bad += i
            }
            if (total > sums[k] || (open == 0 && total != sums[k])) for (i in members[k]) bad += i
        }
        return bad
    }

    /** Each cage's squares in reading order. */
    fun cageCells(cageOf: List<Int>): Array<IntArray> =
        Array(cageOf.max() + 1) { k -> cageOf.indices.filter { cageOf[it] == k }.toIntArray() }

    // ---- the board the logic works on --------------------------------------------------------------------

    /** Whether a [Group] is a real cage, the squares a scope's cages leave over, or the squares its cages spill into. */
    enum class Kind { CAGE, INNIE, OUTIE }

    /**
     * A set of squares whose digits add up to [sum]. A real cage ([kind] CAGE, [cage] its number), or one of the
     * 45-rule's: a scope of whole rows, columns or boxes holds 45 a unit, so the squares of it that no cage lying
     * wholly inside covers (INNIE) total 45 a unit less those cages, and the squares outside it that the cages
     * touching it reach into (OUTIE) total those cages less 45 a unit. [scope] indexes [Board.scopes], -1 for a cage.
     * [distinct] says the digits are all different: a cage, or squares sharing one row, column or box.
     */
    class Group(
        val cells: IntArray,
        val sum: Int,
        val kind: Kind,
        val cage: Int,
        val scope: Int,
        val distinct: Boolean,
    )

    /** Rows, columns or boxes taken together: [name] is how the teacher says it; [units] 45 a unit. */
    class Scope(val name: String, val cells: IntArray, val unitCount: Int)

    /** The most squares an innie or outie group may have before it stops being worth naming. */
    const val MAX_INNIES = 5
    const val MAX_OUTIES = 3

    private fun shareUnit(cells: IntArray): Boolean = units.any { u -> cells.all { it in u } }

    /** Every scope the 45-rule is tried on: 1-4 neighbouring rows, 1-4 neighbouring columns, a box, two neighbouring boxes. */
    val scopes: List<Scope> by lazy {
        val out = ArrayList<Scope>()
        for (len in 1..4) for (start in 0..9 - len) {
            val rows = (start until start + len)
            out += Scope(
                if (len == 1) "row ${start + 1}" else "rows ${start + 1}-${start + len}",
                rows.flatMap { r -> (0 until 9).map { r * 9 + it } }.toIntArray(), len,
            )
        }
        for (len in 1..4) for (start in 0..9 - len) {
            val cols = (start until start + len)
            out += Scope(
                if (len == 1) "column ${start + 1}" else "columns ${start + 1}-${start + len}",
                cols.flatMap { c -> (0 until 9).map { it * 9 + c } }.sorted().toIntArray(), len,
            )
        }
        for (b in 0 until 9) out += Scope("the ${BOX_NAMES[b]} box", units[b], 1)
        for (b in 0 until 9) if (b % 3 < 2) out += Scope("the ${BOX_NAMES[b]} and ${BOX_NAMES[b + 1]} boxes", (units[b] + units[b + 1]).sortedArray(), 2)
        for (b in 0 until 6) out += Scope("the ${BOX_NAMES[b]} and ${BOX_NAMES[b + 3]} boxes", (units[b] + units[b + 3]).sortedArray(), 2)
        out
    }

    val BOX_NAMES = listOf(
        "top-left", "top-middle", "top-right",
        "middle-left", "centre", "middle-right",
        "bottom-left", "bottom-middle", "bottom-right",
    )

    class Board(val cageOf: List<Int>, val sums: List<Int>) {
        val cages: Array<IntArray> = cageCells(cageOf)
        val groups: List<Group>

        /** The other squares of each square's cage. */
        val mates: Array<IntArray> = Array(81) { i -> cages[cageOf[i]].filter { it != i }.toIntArray() }

        init {
            val g = ArrayList<Group>()
            for (k in cages.indices) g += Group(cages[k], sums[k], Kind.CAGE, k, -1, true)
            for ((s, scope) in scopes.withIndex()) {
                val inScope = BooleanArray(81)
                for (c in scope.cells) inScope[c] = true
                val covered = BooleanArray(81)
                var insideSum = 0
                var touchingSum = 0
                val outside = ArrayList<Int>()
                for (k in cages.indices) {
                    if (cages[k].none { inScope[it] }) continue
                    touchingSum += sums[k]
                    if (cages[k].all { inScope[it] }) {
                        for (c in cages[k]) covered[c] = true
                        insideSum += sums[k]
                    } else {
                        for (c in cages[k]) if (!inScope[c]) outside += c
                    }
                }
                val rest = scope.cells.filter { !covered[it] }.toIntArray()
                if (rest.isNotEmpty() && rest.size <= MAX_INNIES &&
                    g.none { it.cells.contentEquals(rest) && it.sum == 45 * scope.unitCount - insideSum }
                ) {
                    g += Group(rest, 45 * scope.unitCount - insideSum, Kind.INNIE, -1, s, shareUnit(rest))
                }
                outside.sort()
                val out = outside.toIntArray()
                if (out.isNotEmpty() && out.size <= MAX_OUTIES &&
                    g.none { it.cells.contentEquals(out) && it.sum == touchingSum - 45 * scope.unitCount }
                ) {
                    g += Group(out, touchingSum - 45 * scope.unitCount, Kind.OUTIE, -1, s, shareUnit(out))
                }
            }
            groups = g
        }
    }

    /**
     * What the digits [cells] (0 = empty) leave possible: [cand] per square, a 1..9 bitmask, 0 for a filled
     * square. [broken] means the digits cannot be completed (a square or group with nothing left). With
     * [prune] off, only the rows, columns, boxes and cages' own repeats are taken off: the plain Sudoku view.
     */
    class Sight(val board: Board, val cells: IntArray, prune: Boolean = true) {
        val cand = IntArray(81)
        var broken = false
            private set

        init {
            for (i in 0 until 81) {
                if (cells[i] != 0) continue
                var m = ALL
                for (p in Sudoku.peers(i)) if (cells[p] != 0) m = m and (1 shl cells[p]).inv()
                for (p in board.mates[i]) if (cells[p] != 0) m = m and (1 shl cells[p]).inv()
                cand[i] = m
            }
            var changed = prune
            while (changed && !broken) {
                changed = false
                for (g in board.groups) if (prune(g)) changed = true
            }
            if (!broken) for (i in 0 until 81) if (cells[i] == 0 && cand[i] == 0) broken = true
        }

        /** The empty squares of [g]. */
        fun open(g: Group): IntArray = g.cells.filter { cells[it] == 0 }.toIntArray()

        /** What [g] still has to add up to, once its filled squares are taken off. */
        fun remaining(g: Group): Int = g.sum - g.cells.sumOf { cells[it] }

        /**
         * The digits [g]'s squares may still take, per square, as the bitmask each must lie within; null when [g]
         * has no empty square. Distinct groups: the squares of the combinations of that many different digits
         * that total [remaining] and fit the candidates. Others: any digit whose complement the rest can reach.
         */
        fun allowed(g: Group): IntArray? {
            val open = open(g)
            if (open.isEmpty()) return null
            val r = remaining(g)
            val out = IntArray(open.size)
            if (r < 0 || r > 45) return out
            if (g.distinct) {
                var union = 0
                var seen = 0
                for (c in open) seen = seen or cand[c]
                for (m in combos[open.size][r]) {
                    if (m and seen != m) continue
                    var fits = true
                    for (c in open) if (cand[c] and m == 0) { fits = false; break }
                    if (fits) union = union or m
                }
                out.fill(union)
            }
            // Sums alone (also tightens a distinct group's squares one by one).
            for (a in open.indices) {
                var reach = 1L
                for (b in open.indices) {
                    if (b == a) continue
                    var next = 0L
                    for (d in 1..9) if (cand[open[b]] and (1 shl d) != 0) next = next or (reach shl d)
                    reach = next
                }
                var ok = 0
                for (d in 1..9) {
                    if (cand[open[a]] and (1 shl d) == 0 || d > r) continue
                    if ((reach shr (r - d)) and 1L != 0L) ok = ok or (1 shl d)
                }
                out[a] = if (g.distinct) out[a] and ok else ok
            }
            return out
        }

        /** The combinations of different digits that [g]'s empty squares could still be, as digit masks. Distinct groups only. */
        fun fitting(g: Group): List<Int> {
            val open = open(g)
            val r = remaining(g)
            if (open.isEmpty() || r !in 0..45) return emptyList()
            var seen = 0
            for (c in open) seen = seen or cand[c]
            return combos[open.size][r].filter { m ->
                m and seen == m && open.all { cand[it] and m != 0 }
            }
        }

        private fun prune(g: Group): Boolean {
            val open = open(g)
            if (open.isEmpty()) {
                if (remaining(g) != 0) broken = true
                return false
            }
            val allow = allowed(g)!!
            var changed = false
            for (a in open.indices) {
                val next = cand[open[a]] and allow[a]
                if (next == 0) { broken = true; return false }
                if (next != cand[open[a]]) { cand[open[a]] = next; changed = true }
            }
            return changed
        }

        /** The squares of [unit] that are empty and may still be [digit]. */
        fun places(unit: Int, digit: Int): List<Int> =
            units[unit].filter { cells[it] == 0 && cand[it] and (1 shl digit) != 0 }

        fun holds(unit: Int, digit: Int): Boolean = units[unit].any { cells[it] == digit }
    }

    /**
     * Places every square the digits force, in rounds, until none is left or the digits break. Returns
     * false when they broke. Placements are all true at once and stay true as more digits go down, so
     * the order and the batching cannot change where this ends.
     */
    fun settle(board: Board, cells: IntArray): Boolean {
        while (true) {
            val sight = Sight(board, cells)
            if (sight.broken) return false
            var placed = 0
            for (i in 0 until 81) {
                if (cells[i] != 0 || sight.cand[i].countOneBits() != 1) continue
                cells[i] = sight.cand[i].countTrailingZeroBits()
                placed++
            }
            for (u in 0 until 27) for (d in 1..9) {
                if (units[u].any { cells[it] == d }) continue
                var only = -1
                var count = 0
                for (c in units[u]) if (cells[c] == 0 && sight.cand[c] and (1 shl d) != 0) { only = c; count++ }
                if (count == 1) { cells[only] = d; placed++ }
            }
            if (placed == 0) return true
        }
    }

    // ---- the proof ---------------------------------------------------------------------------------------


    /** Exhaustive: settle, then branch on the emptiest square, until a second answer turns up or the tree is gone. */
    fun solutions(board: Board, start: List<Int>): Verdict {
        var first: List<Int>? = null
        var count = 0
        fun walk(cells: IntArray) {
            if (count >= 2) return
            if (!settle(board, cells)) return
            val empties = (0 until 81).filter { cells[it] == 0 }
            if (empties.isEmpty()) {
                val done = cells.toList()
                if (isSolved(done, board.cageOf, board.sums)) {
                    if (count == 0) first = done
                    count++
                }
                return
            }
            val sight = Sight(board, cells)
            val pick = empties.minByOrNull { sight.cand[it].countOneBits() }!!
            for (d in 1..9) {
                if (sight.cand[pick] and (1 shl d) == 0) continue
                val next = cells.copyOf()
                next[pick] = d
                walk(next)
            }
        }
        walk(start.toIntArray())
        return Verdict.of(count) { first!! }
    }

    // ---- the generator -----------------------------------------------------------------------------------

    /** What a tier asks of a board. See [specFor]. */
    class Spec(
        /** Cage sizes drawn at random (with repeats, so the mix is the odds). */
        val sizes: List<Int>,
        /** No cage grows past this, by growing or by merging. */
        val cap: Int,
        /** Merge attempts after the first finish: bigger cages while the ladder still finishes. */
        val merges: Int,
        /** Most givens the tier may keep; 0 means the cages must do it alone. */
        val maxGivens: Int,
        /** Givens put back after taking back, so the tier never shows fewer. */
        val floorGivens: Int,
    )

    fun specFor(difficulty: Difficulty): Spec = when (difficulty) {
        Difficulty.STANDARD -> Spec(listOf(2, 2, 3, 3, 4), cap = 4, merges = 0, maxGivens = 40, floorGivens = 14)
        Difficulty.HARD -> Spec(listOf(2, 2, 3, 3, 4), cap = 5, merges = 60, maxGivens = 14, floorGivens = 8)
        Difficulty.EXPERT -> Spec(listOf(2, 2, 2, 2, 3), cap = 7, merges = 250, maxGivens = 0, floorGivens = 0)
    }

    /** Four-way neighbours of [cell], in a fixed order. */
    /**
     * Cages grown at random over [solution]: each from a random free square by random neighbours, to a size
     * drawn from the tier's mix, never taking a digit the cage already holds. A lone square is folded into
     * a neighbouring cage when its digit and the cap allow; otherwise it stays a cage of one.
     */
    fun partition(rng: Rng, solution: List<Int>, spec: Spec): List<Int> {
        val owner = IntArray(81) { -1 }
        val cages = ArrayList<MutableList<Int>>()
        for (start in rng.shuffled((0 until 81).toList())) {
            if (owner[start] >= 0) continue
            val cage = mutableListOf(start)
            var seen = 1 shl solution[start]
            owner[start] = cages.size
            val target = rng.pick(spec.sizes)
            while (cage.size < target) {
                val options = cage.flatMap { orthogonalReading(it, 9) }.filter {
                    owner[it] < 0 && seen and (1 shl solution[it]) == 0
                }.distinct()
                if (options.isEmpty()) break
                val next = rng.pick(options)
                cage += next
                owner[next] = cages.size
                seen = seen or (1 shl solution[next])
            }
            cages += cage
        }
        for (k in cages.indices) {
            if (cages[k].size != 1) continue
            val cell = cages[k][0]
            val host = orthogonalReading(cell, 9).map { owner[it] }.distinct().firstOrNull { h ->
                h != k && cages[h].size < spec.cap && cages[h].none { solution[it] == solution[cell] }
            } ?: continue
            cages[host] += cell
            owner[cell] = host
            cages[k].clear()
        }
        return canonical(owner.toList())
    }

    /** Cage numbers by first square in reading order, with unused numbers closed up. */
    fun canonical(owner: List<Int>): List<Int> {
        val renamed = IntArray(owner.max() + 1) { -1 }
        var next = 0
        return owner.map { k ->
            if (renamed[k] < 0) renamed[k] = next++
            renamed[k]
        }
    }

    /** Whether cages [a] and [b] touch edge to edge. */
    private fun touching(cages: Array<IntArray>, a: Int, b: Int): Boolean =
        cages[a].any { c -> orthogonalReading(c, 9).any { it in cages[b] } }

    /** The givens-and-cages a ladder-finishing board ended with, before it is proved. */
    private class Draft(val cageOf: List<Int>, val givens: BooleanArray)

    private fun finishes(board: Board, solution: List<Int>, givens: BooleanArray): Boolean {
        val cells = IntArray(81) { if (givens[it]) solution[it] else 0 }
        return settle(board, cells) && cells.none { it == 0 }
    }

    private fun draft(rng: Rng, solution: List<Int>, spec: Spec): Draft? {
        var cageOf = partition(rng, solution, spec)
        var board = Board(cageOf, sumsOf(cageOf, solution))
        val givens = BooleanArray(81)

        // 1. Givens only where the ladder is stuck: the emptiest square, the one a player would be staring at.
        val cells = IntArray(81)
        var added = 0
        while (true) {
            if (!settle(board, cells)) return null
            if (cells.none { it == 0 }) break
            if (added >= spec.maxGivens) return null
            val sight = Sight(board, cells)
            val least = (0 until 81).filter { cells[it] == 0 }.minOf { sight.cand[it].countOneBits() }
            val pick = rng.pick((0 until 81).filter { cells[it] == 0 && sight.cand[it].countOneBits() == least })
            cells[pick] = solution[pick]
            givens[pick] = true
            added++
        }

        // 2. Bigger cages while the ladder still finishes.
        repeat(spec.merges) {
            val cages = board.cages
            if (cages.size < 2) return@repeat
            val a = rng.nextInt(cages.size)
            val near = cages.indices.filter { it != a && touching(cages, a, it) }
            if (near.isEmpty()) return@repeat
            val b = rng.pick(near)
            if (cages[a].size + cages[b].size > spec.cap) return@repeat
            val seen = cages[a].fold(0) { m, c -> m or (1 shl solution[c]) }
            if (cages[b].any { seen and (1 shl solution[it]) != 0 }) return@repeat
            val merged = canonical(cageOf.map { if (it == b) a else it })
            val trial = Board(merged, sumsOf(merged, solution))
            if (finishes(trial, solution, givens)) {
                cageOf = merged
                board = trial
            }
        }

        // 3. Take back every given the rest implies, in random order.
        for (i in rng.shuffled((0 until 81).filter { givens[it] })) {
            givens[i] = false
            if (!finishes(board, solution, givens)) givens[i] = true
        }

        // 4. A tier never shows fewer than its floor: put some back, at random.
        var shown = givens.count { it }
        for (i in rng.shuffled((0 until 81).filter { !givens[it] })) {
            if (shown >= spec.floorGivens) break
            givens[i] = true
            shown++
        }
        return Draft(cageOf, givens)
    }

    /** One try at a board for [seed]: null when this attempt's cages and givens could not be made to finish, or proved nothing. */
    fun attempt(seed: Long, difficulty: Difficulty, attempt: Int): CageState? {
        val spec = specFor(difficulty)
        val rng = Rng(seed + attempt)
        val solution = Sudoku.fullGrid(rng)
        val d = draft(rng, solution, spec) ?: return null
        val board = Board(d.cageOf, sumsOf(d.cageOf, solution))
        val start = List(81) { if (d.givens[it]) solution[it] else 0 }
        val verdict = solutions(board, start)
        if (verdict !is Verdict.ExactlyOne || verdict.value != solution) return null
        return CageState(
            grid = SudokuState(givens = d.givens.toList(), cells = start, solution = solution),
            cageOf = d.cageOf,
            sums = board.sums,
        )
    }

    /** Null when nothing was proved. Pure in (seed, difficulty). */
    fun generateVerified(seed: Long, difficulty: Difficulty): CageState? {
        for (attempt in 0 until ATTEMPTS) attempt(seed, difficulty, attempt)?.let { return it }
        return null
    }

    /**
     * Dominoes: each row's squares paired from the left, and the last column's from the top, the odd one out a cage of
     * its own. Not a board's cages (a seed never gets these): the walkthrough's and the last resort's.
     */
    fun dominoes(): List<Int> = canonical(List(81) { i ->
        if (i % 9 < 8) i / 9 * 5 + i % 9 / 2 else 100 + i / 18
    })

    /**
     * A fixed board for a seed that proves nothing (none ever has: see `FallbackTest`): the tutorial grid,
     * [dominoes] for cages, and every digit given but those of the first row, which the columns then force. [CageRulesTest] proves it has one answer.
     */
    fun lastResort(): CageState {
        val solution = Cages.TUTORIAL_SOLUTION
        val cageOf = dominoes()
        val open = (0 until 9).toSet()
        val givens = List(81) { it !in open }
        return CageState(
            grid = SudokuState(
                givens = givens,
                cells = solution.mapIndexed { i, v -> if (givens[i]) v else 0 },
                solution = solution,
            ),
            cageOf = cageOf,
            sums = sumsOf(cageOf, solution),
        )
    }
}
