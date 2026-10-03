package com.joebywan.daybook.puzzles

/**
 * The Inequality square, reasoned a step at a time.
 *
 * As in [SudokuTeacher], the split is the point: [deduce] and [findStep] are handed the digits and
 * signs the player can see and nothing else, so a step they explain cannot lean on the stored
 * answer. The answer is read only in [teach]: to decide which digits are mistakes (sound, because a
 * shipped board has one answer), and for [FALLBACK], which says openly that it points at the answer.
 *
 * Two layers of candidates, both derived from the visible board on every call:
 * - *base*: an empty square's digits that no filled square in its row or column holds;
 * - *sight*: base, cut down by the signs, to a fixpoint. `a < b` leaves `a` only digits below the
 *   highest `b` can still be, and `b` only digits above the lowest `a` can be. A filled neighbour is
 *   just a candidate set of one.
 *
 * Techniques, simplest first. Each ends in one digit placed:
 * 1. [LAST] — a row or column with one empty square.
 * 2. [ONLY_DIGIT] — a square whose row and column leave one digit.
 * 3. [ONLY_PLACE] — a digit with one square left in a row or column.
 * 4. [SIGN_BOUND] — a square the signs leave one digit.
 * 5. [SIGN_PLACE] — a digit with one square left in a row or column once the signs are counted.
 *
 * The generator keeps a board only while these finish it ([solvesByLogic]), so a board made here
 * never reaches [FALLBACK]; it exists for a board that did not come from the generator and is
 * measured by `InequalityTeachingTest`. Every loop walks squares and lines in index order, so a
 * board gets the same hint on every platform (CLAUDE.md, "Hash iteration order").
 *
 * Highlight indices: `0 until n*n` are squares, `n*n + k` is the k-th sign, [pad] a digit key.
 */
internal object InequalityTeacher {

    const val LAST = "last-square"
    const val ONLY_DIGIT = "only-digit"
    const val ONLY_PLACE = "only-place"
    const val SIGN_BOUND = "sign-bound"
    const val SIGN_PLACE = "sign-place"
    const val FALLBACK = "fallback"
    const val MISTAKE = "mistake"

    val TECHNIQUES = listOf(LAST, ONLY_DIGIT, ONLY_PLACE, SIGN_BOUND, SIGN_PLACE, FALLBACK)

    const val MAX_EXPLANATION = 200

    /** Highlight index of digit [d]'s key under the board. */
    fun pad(d: Int): Int = 1000 + d

    class Step(
        val technique: String,
        val cell: Int,
        /** 0 for a clear (a mistake). */
        val digit: Int,
        val focus: Set<Int>,
        val cited: Set<Int>,
        val nudge: String,
        val explanation: String,
    )

    // ---- what the player can see ------------------------------------------------------------------

    private fun bit(d: Int) = 1 shl d
    private fun has(mask: Int, d: Int) = mask and bit(d) != 0
    private fun digitsOf(n: Int, mask: Int) = (1..n).filter { has(mask, it) }
    private fun single(mask: Int) = mask != 0 && mask and (mask - 1) == 0

    /** Rows 0..n-1, then columns. */
    private fun lines(n: Int): List<List<Int>> =
        List(n) { r -> List(n) { r * n + it } } + List(n) { c -> List(n) { it * n + c } }

    private fun lineName(n: Int, line: Int) = if (line < n) "row ${line + 1}" else "column ${line - n + 1}"

    private class Sight(val n: Int, val cells: List<Int>, val signs: List<Sign>) {
        val base = IntArray(n * n)
        val cand = IntArray(n * n)
        var consistent = true

        init {
            val full = InequalityLogic.fullMask(n)
            for (i in 0 until n * n) {
                if (cells[i] != 0) {
                    cand[i] = bit(cells[i])
                    continue
                }
                var m = full
                for (j in 0 until n * n) {
                    if (cells[j] != 0 && (j / n == i / n || j % n == i % n)) m = m and bit(cells[j]).inv()
                }
                base[i] = m
                cand[i] = m
            }
            do {
                var changed = false
                for (s in signs) {
                    val hiTop = 31 - cand[s.hi].countLeadingZeroBits()
                    val loBottom = cand[s.lo].countTrailingZeroBits()
                    val newLo = cand[s.lo] and (bit(hiTop) - 1)
                    val newHi = cand[s.hi] and (bit(loBottom + 1) - 1).inv()
                    if (newLo != cand[s.lo] || newHi != cand[s.hi]) {
                        cand[s.lo] = newLo
                        cand[s.hi] = newHi
                        changed = true
                    }
                }
            } while (changed)
            consistent = (0 until n * n).none { cells[it] == 0 && cand[it] == 0 }
        }
    }

    // ---- finding a step ---------------------------------------------------------------------------

    private class Hit(val technique: String, val cell: Int, val digit: Int, val line: Int)

    private fun findStep(n: Int, cells: List<Int>, signs: List<Sign>): Hit? {
        val sight = Sight(n, cells, signs)
        if (!sight.consistent) return null
        val all = lines(n)
        // 1. The last square of a line.
        for ((li, line) in all.withIndex()) {
            val empty = line.filter { cells[it] == 0 }
            if (empty.size == 1) {
                val missing = InequalityLogic.fullMask(n) and line.fold(0) { m, i -> if (cells[i] != 0) m or bit(cells[i]) else m }.inv()
                if (single(missing)) return Hit(LAST, empty[0], missing.countTrailingZeroBits(), li)
            }
        }
        // 2. A square whose row and column leave one digit.
        for (i in 0 until n * n) {
            if (cells[i] == 0 && single(sight.base[i])) return Hit(ONLY_DIGIT, i, sight.base[i].countTrailingZeroBits(), -1)
        }
        // 3. A digit with one place left in a line.
        for ((li, line) in all.withIndex()) {
            for (d in 1..n) {
                if (line.any { cells[it] == d }) continue
                val places = line.filter { cells[it] == 0 && has(sight.base[it], d) }
                if (places.size == 1) return Hit(ONLY_PLACE, places[0], d, li)
            }
        }
        // 4. A square the signs leave one digit.
        for (i in 0 until n * n) {
            if (cells[i] == 0 && single(sight.cand[i])) return Hit(SIGN_BOUND, i, sight.cand[i].countTrailingZeroBits(), -1)
        }
        // 5. A digit with one place left once the signs are counted.
        for ((li, line) in all.withIndex()) {
            for (d in 1..n) {
                if (line.any { cells[it] == d }) continue
                val places = line.filter { cells[it] == 0 && has(sight.cand[it], d) }
                if (places.size == 1) return Hit(SIGN_PLACE, places[0], d, li)
            }
        }
        return null
    }

    /** Whether the five techniques alone finish this board: what the generator asks of every board it ships. */
    fun solvesByLogic(n: Int, given: List<Int>, signs: List<Sign>): Boolean {
        val cells = given.toMutableList()
        while (cells.any { it == 0 }) {
            val hit = findStep(n, cells, signs) ?: return false
            cells[hit.cell] = hit.digit
        }
        return InequalityLogic.isSolved(n, cells, signs)
    }

    // ---- the whole hint: mistakes, then reasoning, then the honest fallback ------------------------

    fun teach(s: InequalityState): Step? {
        if (s.solved) return null
        mistake(s)?.let { return it }
        return deduce(s.size, s.cells, s.signs) ?: fallback(s)
    }

    private fun fallback(s: InequalityState): Step? {
        val i = s.cells.indices.firstOrNull { s.cells[it] == 0 } ?: return null
        val d = s.solution[i]
        return Step(
            FALLBACK, i, d, setOf(i), emptySet(),
            "Try this square.",
            "No short reason found: the stored answer has a $d here.",
        )
    }

    private fun mistake(s: InequalityState): Step? {
        val n = s.size
        val wrong = s.cells.indices.firstOrNull { s.cells[it] != 0 && !s.givens[it] && s.cells[it] != s.solution[it] }
            ?: return null
        val d = s.cells[wrong]
        val clear = "Select it and tap $d again to clear it."
        fun step(explanation: String, cited: Set<Int>) =
            Step(MISTAKE, wrong, 0, setOf(wrong), cited, "Check this $d.", explanation)

        for (line in lines(n)) {
            if (wrong !in line) continue
            val twin = line.firstOrNull { it != wrong && s.cells[it] == d } ?: continue
            return step("This $d repeats the $d in ${lineName(n, lines(n).indexOf(line))}. $clear", setOf(twin))
        }
        for ((k, sign) in s.signs.withIndex()) {
            if (wrong != sign.lo && wrong != sign.hi) continue
            val other = if (wrong == sign.lo) sign.hi else sign.lo
            val o = s.cells[other]
            if (o == 0) continue
            val ok = if (wrong == sign.lo) d < o else d > o
            if (ok) continue
            val way = if (wrong == sign.lo) "smaller" else "bigger"
            return step("This $d breaks a sign: it has to be $way than the $o beside it. $clear", setOf(n * n + k, other))
        }
        return step("No valid way to finish the board keeps this $d. $clear", emptySet())
    }

    // ---- explaining ---------------------------------------------------------------------------------

    private fun orList(ds: List<Int>) = if (ds.size < 2) ds.joinToString("") else ds.dropLast(1).joinToString(", ") + " or " + ds.last()
    private fun andList(ds: List<Int>) = if (ds.size < 2) ds.joinToString("") else ds.dropLast(1).joinToString(", ") + " and " + ds.last()

    /** Where [other] sits relative to [x]. */
    private fun where(n: Int, x: Int, other: Int) = when (other - x) {
        1 -> "on its right"
        -1 -> "on its left"
        n -> "below it"
        else -> "above it"
    }

    /** Placed cells in [x]'s row and column, one per digit in [digits], as the cited squares. */
    private fun holders(n: Int, cells: List<Int>, x: Int, digits: List<Int>): Set<Int> =
        digits.mapNotNull { d -> (0 until n * n).firstOrNull { cells[it] == d && (it / n == x / n || it % n == x % n) } }.toSet()

    fun deduce(n: Int, cells: List<Int>, signs: List<Sign>): Step? {
        val hit = findStep(n, cells, signs) ?: return null
        val sight = Sight(n, cells, signs)
        val x = hit.cell
        val d = hit.digit
        val all = lines(n)
        return when (hit.technique) {
            LAST -> {
                val name = lineName(n, hit.line)
                Step(
                    LAST, x, d, all[hit.line].toSet(), all[hit.line].filter { cells[it] != 0 }.toSet(),
                    "Look at $name.",
                    "$name has every digit but $d, so its last empty square is a $d.",
                )
            }
            ONLY_DIGIT -> {
                val seen = (1..n).filter { !has(sight.base[x], it) }
                Step(
                    ONLY_DIGIT, x, d, setOf(x), holders(n, cells, x, seen),
                    "Which digits can this square still take?",
                    "Row ${x / n + 1} and column ${x % n + 1} already hold ${andList(seen)}, so only $d is left here.",
                )
            }
            ONLY_PLACE -> {
                val line = all[hit.line]
                val others = line.filter { cells[it] == 0 && it != x }
                val blockers = others.mapNotNull { o ->
                    (0 until n * n).firstOrNull { cells[it] == d && (it / n == o / n || it % n == o % n) }
                }.toSet()
                val across = if (hit.line < n) "column" else "row"
                Step(
                    ONLY_PLACE, x, d, line.toSet(), blockers,
                    "Where can the ${d} go in ${lineName(n, hit.line)}?",
                    "${lineName(n, hit.line).replaceFirstChar { it.uppercase() }} still needs a $d, and every other empty square in it has a $d in its $across. This one is the only place.",
                )
            }
            SIGN_BOUND -> signBound(n, cells, signs, sight, x, d)
            else -> {
                val line = all[hit.line]
                val signsHere = signs.indices.filter { signs[it].lo in line || signs[it].hi in line }.map { n * n + it }
                Step(
                    SIGN_PLACE, x, d, line.toSet(), signsHere.toSet(),
                    "Count the signs along ${lineName(n, hit.line)}.",
                    "${lineName(n, hit.line).replaceFirstChar { it.uppercase() }} still needs a $d. With the signs counted, every other square in it is ruled out, so it goes here.",
                )
            }
        }
    }

    private fun signBound(n: Int, cells: List<Int>, signs: List<Sign>, sight: Sight, x: Int, d: Int): Step {
        val base = sight.base[x]
        val removed = base and bit(d).inv()
        // The signs at x, each with the digits it alone strikes from base given the neighbour's sight.
        class Clause(val k: Int, val other: Int, val strikes: Int, val text: String)
        val clauses = signs.indices.mapNotNull { k ->
            val s = signs[k]
            if (s.lo != x && s.hi != x) return@mapNotNull null
            val other = if (s.lo == x) s.hi else s.lo
            val oc = sight.cand[other]
            val placed = cells[other] != 0
            val m: Int
            val allowed: Int
            val text: String
            if (s.lo == x) {
                m = 31 - oc.countLeadingZeroBits()
                allowed = bit(m) - 1
                text = if (placed) "smaller than the $m ${where(n, x, other)}" else "smaller than the square ${where(n, x, other)} (at most $m)"
            } else {
                m = oc.countTrailingZeroBits()
                allowed = (bit(m + 1) - 1).inv()
                text = if (placed) "bigger than the $m ${where(n, x, other)}" else "bigger than the square ${where(n, x, other)} (at least $m)"
            }
            Clause(k, other, base and allowed.inv(), text)
        }
        val chosen = mutableListOf<Clause>()
        var covered = 0
        for (c in clauses) {
            if ((c.strikes and removed and covered.inv()) != 0) {
                chosen += c
                covered = covered or c.strikes
            }
            if ((covered and removed) == removed) break
        }
        val cited = chosen.flatMap { listOf(n * n + it.k, it.other) }.toSet() + holders(n, cells, x, (1..n).filter { !has(base, it) })
        val clause = chosen.joinToString(" and ") { it.text }
        var text = "Its row and column leave ${orList(digitsOf(n, base))}, but it has to be $clause. That leaves only $d."
        if (text.length > MAX_EXPLANATION) text = "The signs leave only $d here: it has to be $clause."
        return Step(
            SIGN_BOUND, x, d, setOf(x) + chosen.map { n * n + it.k }, cited,
            "What do the signs next to this square allow?", text,
        )
    }
}
