package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.PlacementStep
import com.joebywan.daybook.core.bit
import com.joebywan.daybook.core.digitsOf
import com.joebywan.daybook.core.has


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
 * 6. [NAKED_PAIR] — two squares of a line the signs leave the same two digits take both, so the rest of the line
 *    loses them; then (counting the signs again if it takes that) a square is left one digit or a digit one place.
 * 7. [HIDDEN_PAIR] — two digits a line needs fit only the same two squares, so those hold nothing else; then the same.
 *
 * A sign chain `a < b < c` needs no rung of its own: the sign cut runs to a fixpoint, so the bottom of a chain of k
 * already loses the top k - 1 digits under [SIGN_BOUND].
 *
 * The generator keeps a board only while these (up to its tier's rung) finish it ([solvesByLogic]), so a board made here
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
    const val NAKED_PAIR = "naked-pair"
    const val HIDDEN_PAIR = "hidden-pair"
    const val FALLBACK = "fallback"
    const val MISTAKE = "mistake"

    val TECHNIQUES = listOf(LAST, ONLY_DIGIT, ONLY_PLACE, SIGN_BOUND, SIGN_PLACE, NAKED_PAIR, HIDDEN_PAIR, FALLBACK)

    const val MAX_EXPLANATION = 200

    /** Highlight index of digit [d]'s key under the board. */
    fun pad(d: Int): Int = 1000 + d

    // ---- what the player can see ------------------------------------------------------------------


    /** Rows 0..n-1, then columns. */
    private fun lines(n: Int): List<List<Int>> = LINES[n]

    private val LINES = List(10) { n -> List(n) { r -> List(n) { r * n + it } } + List(n) { c -> List(n) { it * n + c } } }

    /** The one empty square of [line] whose mask in [m] has [d]; -1 when there are none or several, or the line holds [d]. */
    private fun lonePlace(line: List<Int>, cells: List<Int>, m: IntArray, d: Int): Int {
        var at = -1
        for (i in line) {
            if (cells[i] == d) return -1
            if (cells[i] == 0 && has(m[i], d)) { if (at >= 0) return -1; at = i }
        }
        return at
    }

    private fun lineName(n: Int, line: Int) = if (line < n) "row ${line + 1}" else "column ${line - n + 1}"

    private class Sight(val n: Int, val cells: List<Int>, val signs: List<Sign>) {
        val base = IntArray(n * n)
        val cand = IntArray(n * n)
        var consistent = true

        init {
            val full = InequalityLogic.fullMask(n)
            val row = IntArray(n)
            val col = IntArray(n)
            for (i in 0 until n * n) if (cells[i] != 0) { row[i / n] = row[i / n] or bit(cells[i]); col[i % n] = col[i % n] or bit(cells[i]) }
            for (i in 0 until n * n) {
                if (cells[i] != 0) {
                    cand[i] = bit(cells[i])
                    continue
                }
                base[i] = full and (row[i / n] or col[i % n]).inv()
                cand[i] = base[i]
            }
            signFix(cand, signs)
            consistent = (0 until n * n).none { cells[it] == 0 && cand[it] == 0 }
        }
    }

    /** Cuts [cand] by every sign to a fixpoint (`a < b`: a below b's highest, b above a's lowest). */
    private fun signFix(cand: IntArray, signs: List<Sign>) {
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
    }

    // ---- finding a step ---------------------------------------------------------------------------

    /**
     * A placement. For the pair rungs: [pair] the two squares, [pairDigits] their two digits as a mask, [pairLine] the
     * line they share, [line] the line of an only-place (-1 for a square left one digit) and [needsSigns] whether the
     * signs had to be counted again after the cut.
     */
    private class Hit(
        val technique: String, val cell: Int, val digit: Int, val line: Int,
        val pair: List<Int> = emptyList(), val pairDigits: Int = 0, val pairLine: Int = -1, val needsSigns: Boolean = false,
    )

    /** The rung each technique sits on, in [TECHNIQUES] order: the depth a walk reports. */
    fun rung(technique: String): Int = TECHNIQUES.indexOf(technique)

    /** The deepest rung any board may use. */
    val DEEPEST = rung(HIDDEN_PAIR)

    /**
     * The first placement that [cut] (the sight with a pair's cut made) allows and the sight alone does not: a square
     * left one digit, else a digit left one place in a line. Null when the cut settles nothing.
     */
    private fun placedBy(n: Int, cells: List<Int>, sight: Sight, cut: IntArray): Triple<Int, Int, Int>? {
        if ((0 until n * n).any { cells[it] == 0 && cut[it] == 0 }) return null
        for (i in 0 until n * n) {
            if (cells[i] == 0 && cut[i].countOneBits() == 1 && sight.cand[i].countOneBits() != 1) return Triple(i, -1, cut[i].countTrailingZeroBits())
        }
        for ((li, line) in lines(n).withIndex()) {
            for (d in 1..n) {
                val at = lonePlace(line, cells, cut, d)
                if (at >= 0 && lonePlace(line, cells, sight.cand, d) < 0) return Triple(at, li, d)
            }
        }
        return null
    }

    /** A pair's cut made on [sight]: [keep] squares lose every digit but [digits] (hidden), or the rest of [line] loses them (naked). */
    private fun pairHit(
        technique: String, n: Int, cells: List<Int>, signs: List<Sign>, sight: Sight,
        li: Int, line: List<Int>, pair: List<Int>, digits: Int,
    ): Hit? {
        val cut = sight.cand.copyOf()
        if (technique == NAKED_PAIR) {
            for (o in line) if (cells[o] == 0 && o !in pair) cut[o] = cut[o] and digits.inv()
        } else {
            for (o in pair) cut[o] = cut[o] and digits
        }
        if (cut.contentEquals(sight.cand)) return null
        val plain = placedBy(n, cells, sight, cut)
        val (cell, at, digit) = plain ?: run {
            signFix(cut, signs)
            placedBy(n, cells, sight, cut)
        } ?: return null
        return Hit(technique, cell, digit, at, pair, digits, li, needsSigns = plain == null)
    }

    private fun findStep(n: Int, cells: List<Int>, signs: List<Sign>, deepest: Int = DEEPEST): Hit? {
        val sight = Sight(n, cells, signs)
        if (!sight.consistent) return null
        val all = lines(n)
        // 1. The last square of a line.
        for ((li, line) in all.withIndex()) {
            val empty = line.filter { cells[it] == 0 }
            if (empty.size == 1) {
                val missing = InequalityLogic.fullMask(n) and line.fold(0) { m, i -> if (cells[i] != 0) m or bit(cells[i]) else m }.inv()
                if (missing.countOneBits() == 1) return Hit(LAST, empty[0], missing.countTrailingZeroBits(), li)
            }
        }
        // 2. A square whose row and column leave one digit.
        for (i in 0 until n * n) {
            if (cells[i] == 0 && sight.base[i].countOneBits() == 1) return Hit(ONLY_DIGIT, i, sight.base[i].countTrailingZeroBits(), -1)
        }
        // 3. A digit with one place left in a line.
        for ((li, line) in all.withIndex()) {
            for (d in 1..n) {
                val at = lonePlace(line, cells, sight.base, d)
                if (at >= 0) return Hit(ONLY_PLACE, at, d, li)
            }
        }
        // 4. A square the signs leave one digit.
        for (i in 0 until n * n) {
            if (cells[i] == 0 && sight.cand[i].countOneBits() == 1) return Hit(SIGN_BOUND, i, sight.cand[i].countTrailingZeroBits(), -1)
        }
        // 5. A digit with one place left once the signs are counted.
        for ((li, line) in all.withIndex()) {
            for (d in 1..n) {
                val at = lonePlace(line, cells, sight.cand, d)
                if (at >= 0) return Hit(SIGN_PLACE, at, d, li)
            }
        }
        if (deepest < rung(NAKED_PAIR)) return null
        // 6. Two squares of a line left the same two digits take both: the rest of the line loses them.
        for ((li, line) in all.withIndex()) {
            val open = line.filter { cells[it] == 0 && sight.cand[it].countOneBits() == 2 }
            for (a in open.indices) for (b in a + 1 until open.size) {
                val m = sight.cand[open[a]]
                if (sight.cand[open[b]] != m) continue
                pairHit(NAKED_PAIR, n, cells, signs, sight, li, line, listOf(open[a], open[b]), m)?.let { return it }
            }
        }
        if (deepest < rung(HIDDEN_PAIR)) return null
        // 7. Two digits a line still needs that fit only the same two squares: those squares hold nothing else.
        for ((li, line) in all.withIndex()) {
            val need = (1..n).filter { d -> line.none { cells[it] == d } }
            val places = need.map { d -> line.filter { cells[it] == 0 && has(sight.cand[it], d) } }
            for (a in need.indices) for (b in a + 1 until need.size) {
                val pa = places[a]
                if (pa.size != 2 || places[b] != pa) continue
                pairHit(HIDDEN_PAIR, n, cells, signs, sight, li, line, pa, bit(need[a]) or bit(need[b]))?.let { return it }
            }
        }
        return null
    }

    /**
     * The deepest rung a walk of the ladder (up to [deepest]) needs to finish this board, null when it cannot: what the
     * generator asks of every board it ships, and the tier gate.
     */
    fun depth(n: Int, given: List<Int>, signs: List<Sign>, deepest: Int = DEEPEST): Int? {
        val cells = given.toMutableList()
        var most = -1
        while (cells.any { it == 0 }) {
            val hit = findStep(n, cells, signs, deepest) ?: return null
            cells[hit.cell] = hit.digit
            most = maxOf(most, rung(hit.technique))
        }
        return if (InequalityLogic.isSolved(n, cells, signs)) most else null
    }

    /** Whether the ladder up to [deepest] finishes this board. */
    fun solvesByLogic(n: Int, given: List<Int>, signs: List<Sign>, deepest: Int = DEEPEST): Boolean =
        pairDepth(n, given, signs, deepest) != null

    /**
     * What [depth] says about the pair rungs, found much faster: null when the ladder up to [deepest] cannot finish the
     * board, else the deepest pair rung the walk needs (-1 for none). The generator's check, run thousands of times a draw.
     *
     * The first five rungs together come to "a square the signs leave one digit, or a digit the signs leave one place in a
     * line", and placing a right digit never takes such a step away (candidates only shrink, the right digit is never
     * struck), so every order of them stops at the same board. So all of them are placed in one pass per [Sight], not one per
     * [findStep], and the walk still meets the same stuck boards, where [findStep] takes the same pair step the hints do.
     * That holds while every digit placed is right, which it is on a board with one answer made from its own solution.
     */
    fun pairDepth(n: Int, given: List<Int>, signs: List<Sign>, deepest: Int = DEEPEST): Int? {
        val cells = given.toMutableList()
        var most = -1
        while (cells.any { it == 0 }) {
            val sight = Sight(n, cells, signs)
            if (!sight.consistent) return null
            var placed = false
            for (i in 0 until n * n) {
                if (cells[i] == 0 && sight.cand[i].countOneBits() == 1) { cells[i] = sight.cand[i].countTrailingZeroBits(); placed = true }
            }
            for (line in lines(n)) for (d in 1..n) {
                val at = lonePlace(line, cells, sight.cand, d)
                if (at >= 0) { cells[at] = d; placed = true }
            }
            if (placed) continue
            val hit = findStep(n, cells, signs, deepest) ?: return null
            cells[hit.cell] = hit.digit
            most = maxOf(most, rung(hit.technique))
        }
        return if (InequalityLogic.isSolved(n, cells, signs)) most else null
    }

    // ---- the whole hint: mistakes, then reasoning, then the honest fallback ------------------------

    fun teach(s: InequalityState): PlacementStep? {
        if (s.solved) return null
        mistake(s)?.let { return it }
        return deduce(s.size, s.cells, s.signs) ?: fallback(s)
    }

    private fun fallback(s: InequalityState): PlacementStep? {
        val i = s.cells.indices.firstOrNull { s.cells[it] == 0 } ?: return null
        val d = s.solution[i]
        return PlacementStep(
            FALLBACK, i, d, setOf(i), emptySet(),
            "Try this square.",
            "No short reason found: the stored answer has a $d here.",
        )
    }

    private fun mistake(s: InequalityState): PlacementStep? {
        val n = s.size
        val wrong = s.cells.indices.firstOrNull { s.cells[it] != 0 && !s.givens[it] && s.cells[it] != s.solution[it] }
            ?: return null
        val d = s.cells[wrong]
        val clear = "Select it and tap $d again to clear it."
        fun step(explanation: String, cited: Set<Int>) =
            PlacementStep(MISTAKE, wrong, 0, setOf(wrong), cited, "Check this $d.", explanation)

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

    fun deduce(n: Int, cells: List<Int>, signs: List<Sign>): PlacementStep? {
        val hit = findStep(n, cells, signs) ?: return null
        val sight = Sight(n, cells, signs)
        val x = hit.cell
        val d = hit.digit
        val all = lines(n)
        return when (hit.technique) {
            LAST -> {
                val name = lineName(n, hit.line)
                PlacementStep(
                    LAST, x, d, all[hit.line].toSet(), all[hit.line].filter { cells[it] != 0 }.toSet(),
                    "Look at $name.",
                    "$name has every digit but $d, so its last empty square is a $d.",
                )
            }
            ONLY_DIGIT -> {
                val seen = (1..n).filter { !has(sight.base[x], it) }
                PlacementStep(
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
                PlacementStep(
                    ONLY_PLACE, x, d, line.toSet(), blockers,
                    "Where can the ${d} go in ${lineName(n, hit.line)}?",
                    "${lineName(n, hit.line).replaceFirstChar { it.uppercase() }} still needs a $d, and every other empty square in it has a $d in its $across. This one is the only place.",
                )
            }
            SIGN_BOUND -> signBound(n, cells, signs, sight, x, d)
            NAKED_PAIR, HIDDEN_PAIR -> pairStep(n, cells, signs, hit)
            else -> {
                val line = all[hit.line]
                val signsHere = signs.indices.filter { signs[it].lo in line || signs[it].hi in line }.map { n * n + it }
                PlacementStep(
                    SIGN_PLACE, x, d, line.toSet(), signsHere.toSet(),
                    "Count the signs along ${lineName(n, hit.line)}.",
                    "${lineName(n, hit.line).replaceFirstChar { it.uppercase() }} still needs a $d. With the signs counted, every other square in it is ruled out, so it goes here.",
                )
            }
        }
    }

    private fun pairStep(n: Int, cells: List<Int>, signs: List<Sign>, hit: Hit): PlacementStep {
        val (a, b) = digitsOf(hit.pairDigits, n)
        val name = lineName(n, hit.pairLine)
        val cut = if (hit.technique == NAKED_PAIR) {
            "Two squares of $name can only be $a or $b, so they take both, and no other square there can."
        } else {
            "${name.replaceFirstChar { it.uppercase() }} needs a $a and a $b, which fit only the same two squares, so those hold nothing else."
        }
        val again = if (hit.needsSigns) "Counting the signs again, that" else "That"
        val r = hit.cell / n + 1
        val c = hit.cell % n + 1
        val close = when {
            hit.line < 0 -> "$again leaves the square at row $r, column $c only ${hit.digit}."
            hit.line < n -> "$again leaves column $c as the only place for the ${hit.digit} in row $r."
            else -> "$again leaves row $r as the only place for the ${hit.digit} in column $c."
        }
        val signsNear = if (hit.needsSigns) signs.indices.filter { signs[it].lo == hit.cell || signs[it].hi == hit.cell }.map { n * n + it } else emptyList()
        return PlacementStep(
            hit.technique, hit.cell, hit.digit, setOf(hit.cell) + hit.pair, hit.pair.toSet() + signsNear,
            if (hit.technique == NAKED_PAIR) "Two squares of $name share the same two digits." else "Where can the $a and the $b go in $name?",
            "$cut $close",
        )
    }

    private fun signBound(n: Int, cells: List<Int>, signs: List<Sign>, sight: Sight, x: Int, d: Int): PlacementStep {
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
        var text = "Its row and column leave ${orList(digitsOf(base, n))}, but it has to be $clause. That leaves only $d."
        if (text.length > MAX_EXPLANATION) text = "The signs leave only $d here: it has to be $clause."
        return PlacementStep(
            SIGN_BOUND, x, d, setOf(x) + chosen.map { n * n + it.k }, cited,
            "What do the signs next to this square allow?", text,
        )
    }
}
