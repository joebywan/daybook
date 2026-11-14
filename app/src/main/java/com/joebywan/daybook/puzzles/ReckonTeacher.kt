package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.PlacementStep
import com.joebywan.daybook.core.answerFallback
import com.joebywan.daybook.core.bit
import com.joebywan.daybook.core.cap
import com.joebywan.daybook.core.digitsOf
import com.joebywan.daybook.core.fit
import com.joebywan.daybook.core.fullMask
import com.joebywan.daybook.core.has
import com.joebywan.daybook.core.lineName
import com.joebywan.daybook.core.listed
import com.joebywan.daybook.core.lonePlace
import com.joebywan.daybook.core.squareName

/**
 * Reckon reasoned a step at a time, and the ladder the generator gates on.
 *
 * [deduce] and [findStep] take the digits the player can see and the cages, nothing else, so a step cannot lean on
 * the stored answer. The answer is read only in [teach]: for mistakes (sound, a shipped board has one answer) and for
 * [FALLBACK], which says so. Candidates are derived from the placed digits on every call, never stored, so every step
 * ends in a placement and a hint never asks the player to keep an elimination it has not turned into a digit:
 * - *base*: an open square's digits its row and column do not hold;
 * - *cand*: base cut by the square's own cage, to the digits some way of the cage (a filling that makes its target,
 *   repeating a digit only in squares that share no line) still puts there. Cages are disjoint, so one pass is a fixpoint.
 *
 * Rungs, simplest first ([TECHNIQUES] order is [rung] order):
 * 1. [LAST], [ONLY_DIGIT], [ONLY_PLACE]: the singles, on base.
 * 2. [CAGE_DIGIT], [CAGE_PLACE]: the same on cand (a cage's ways leave a square one digit, or a line one place for one).
 * 3. [CAGE_LINE]: every way of a cage puts some digit in its part of a line, so the line's other squares lose it.
 * 4. [NAKED_PAIR]: two squares of a line left the same two digits take both.
 *    Rungs 3 and 4 make a cut, then must place a digit (counting the cages again if it takes that).
 * 5. [WHAT_IF]: a square left two digits; supposing one, at most [MAX_LINKS] steps of rungs 1-4 leave a square, a cage
 *    or a line with nothing, so it is the other.
 *
 * Each rung is sound for every answer, so a board the ladder finishes has one. Every loop walks squares, cages and lines
 * in index order (CLAUDE.md, "Hash iteration order"). Highlight indices: squares `0 until n*n`, digit key [pad], the
 * pencil [pencil].
 */
internal object ReckonTeacher {

    const val LAST = "last-square"
    const val ONLY_DIGIT = "only-digit"
    const val ONLY_PLACE = "only-place"
    const val CAGE_DIGIT = "cage-combination"
    const val CAGE_PLACE = "cage-place"
    const val CAGE_LINE = "cage-in-line"
    const val NAKED_PAIR = "naked-pair"
    const val WHAT_IF = "what-if"
    const val FALLBACK = "fallback"
    const val MISTAKE = "mistake"

    val TECHNIQUES = listOf(LAST, ONLY_DIGIT, ONLY_PLACE, CAGE_DIGIT, CAGE_PLACE, CAGE_LINE, NAKED_PAIR, WHAT_IF, FALLBACK)

    /** The most steps a what-if follows before it must reach its contradiction. */
    const val MAX_LINKS = 3

    /** Longest explanation (the popover scrolls past its room; Cipher's 260 is the precedent). */
    const val MAX_EXPLANATION = 240

    /** Highlight index of digit [d]'s key. */
    fun pad(n: Int, d: Int): Int = n * n + d

    /** Highlight index of the pencil (43 on 6x6). */
    fun pencil(n: Int): Int = n * n + 7

    fun rung(technique: String): Int = TECHNIQUES.indexOf(technique)

    // ---- what the player can see -----------------------------------------------------------------------------

    private class Board(val n: Int, val cageOf: List<Int>, val ops: String, val targets: List<Int>) {
        val cages = ReckonLogic.cages(cageOf)
        val lines: List<List<Int>> = List(n) { r -> List(n) { r * n + it } } + List(n) { c -> List(n) { it * n + c } }
        fun label(c: Int) = ReckonLogic.label(ops[c], targets[c])

        /** A memo of each cage's ways by its squares' masks (7 bits each, at most 6 squares): looked up, never walked. */
        private val memo = HashMap<Long, List<IntArray>>()

        fun ways(c: Int, masks: IntArray): List<IntArray> {
            val sq = cages[c]
            var key = c.toLong() shl 42
            for (p in sq.indices) key = key or (masks[sq[p]].toLong() shl (7 * p))
            return memo.getOrPut(key) { ReckonLogic.ways(n, sq, ops[c], targets[c], IntArray(sq.size) { masks[sq[it]] }) }
        }
    }

    private class Sight(val b: Board, val cells: List<Int>, from: IntArray? = null) {
        val n = b.n
        val base = IntArray(n * n)
        val cand: IntArray
        val ways: List<List<IntArray>>

        init {
            val row = IntArray(n)
            val col = IntArray(n)
            for (i in 0 until n * n) if (cells[i] != 0) { row[i / n] = row[i / n] or bit(cells[i]); col[i % n] = col[i % n] or bit(cells[i]) }
            for (i in 0 until n * n) base[i] = if (cells[i] != 0) bit(cells[i]) else fullMask(n) and (row[i / n] or col[i % n]).inv()
            val start = from ?: base
            cand = start.copyOf()
            ways = b.cages.mapIndexed { c, sq ->
                val ws = b.ways(c, start)
                for (p in sq.indices) cand[sq[p]] = ws.fold(0) { m, w -> m or bit(w[p]) }
                ws
            }
        }

        /** What leaves the board with nothing, if anything: a square, a cage, or a line with no place for a digit it needs. */
        fun contradiction(): Contra? {
            for (i in 0 until n * n) if (cells[i] == 0 && cand[i] == 0) return Contra.Empty(i)
            for (c in ways.indices) if (ways[c].isEmpty()) return Contra.NoWay(c)
            for ((li, line) in b.lines.withIndex()) for (d in 1..n) {
                if (line.none { cells[it] == d } && line.none { cells[it] == 0 && has(cand[it], d) }) return Contra.NoPlace(li, d)
            }
            return null
        }
    }

    private sealed interface Contra {
        data class Empty(val square: Int) : Contra
        data class NoWay(val cage: Int) : Contra
        data class NoPlace(val line: Int, val digit: Int) : Contra
    }

    /** A placement and what explains it: the [line] of a lone place (-1 for a square left one digit), the [cage] a cage rung used. */
    private class Hit(
        val technique: String, val cell: Int, val digit: Int, val line: Int = -1, val cage: Int = -1,
        val pair: List<Int> = emptyList(), val pairDigits: Int = 0, val cutLine: Int = -1, val cutDigit: Int = 0,
        val needsCages: Boolean = false,
        val supposed: Int = 0, val links: List<Hit> = emptyList(), val contra: Contra? = null,
    )

    // ---- finding a step --------------------------------------------------------------------------------------

    /** The first placement [cut] allows that [s]'s candidates alone do not: a square left one digit, else a lone place. */
    private fun placedBy(s: Sight, cut: IntArray): Triple<Int, Int, Int>? {
        val n = s.n
        if ((0 until n * n).any { s.cells[it] == 0 && cut[it] == 0 }) return null
        for (i in 0 until n * n) {
            if (s.cells[i] == 0 && cut[i].countOneBits() == 1 && s.cand[i].countOneBits() != 1) return Triple(i, -1, cut[i].countTrailingZeroBits())
        }
        for ((li, line) in s.b.lines.withIndex()) for (d in 1..n) {
            val at = lonePlace(line, s.cells, cut, d)
            if (at >= 0 && lonePlace(line, s.cells, s.cand, d) < 0) return Triple(at, li, d)
        }
        return null
    }

    /** A cut made on [s]'s candidates, then a placement from it, with the cages counted again if that is what it takes. */
    private fun cutHit(s: Sight, cut: IntArray, make: (Triple<Int, Int, Int>, Boolean) -> Hit): Hit? {
        if (cut.contentEquals(s.cand)) return null
        placedBy(s, cut)?.let { return make(it, false) }
        val again = Sight(s.b, s.cells, cut)
        if (again.contradiction() != null) return null
        return placedBy(s, again.cand)?.let { make(it, true) }
    }

    private fun findStep(b: Board, cells: List<Int>, deepest: Int, s: Sight = Sight(b, cells)): Hit? {
        if (s.contradiction() != null) return null
        val n = b.n
        for ((li, line) in b.lines.withIndex()) {
            val empty = line.filter { cells[it] == 0 }
            if (empty.size == 1) {
                val missing = fullMask(n) and line.fold(0) { m, i -> if (cells[i] != 0) m or bit(cells[i]) else m }.inv()
                if (missing.countOneBits() == 1) return Hit(LAST, empty[0], missing.countTrailingZeroBits(), li)
            }
        }
        for (i in 0 until n * n) if (cells[i] == 0 && s.base[i].countOneBits() == 1) return Hit(ONLY_DIGIT, i, s.base[i].countTrailingZeroBits())
        for ((li, line) in b.lines.withIndex()) for (d in 1..n) {
            val at = lonePlace(line, cells, s.base, d)
            if (at >= 0) return Hit(ONLY_PLACE, at, d, li)
        }
        for (i in 0 until n * n) {
            if (cells[i] == 0 && s.cand[i].countOneBits() == 1) return Hit(CAGE_DIGIT, i, s.cand[i].countTrailingZeroBits(), cage = b.cageOf[i])
        }
        for ((li, line) in b.lines.withIndex()) for (d in 1..n) {
            val at = lonePlace(line, cells, s.cand, d)
            if (at >= 0) return Hit(CAGE_PLACE, at, d, li)
        }
        if (deepest < rung(CAGE_LINE)) return null
        for ((c, sq) in b.cages.withIndex()) {
            if (sq.all { cells[it] != 0 }) continue
            for ((li, line) in b.lines.withIndex()) {
                val inLine = sq.indices.filter { sq[it] in line }
                if (inLine.isEmpty()) continue
                val others = line.filter { cells[it] == 0 && b.cageOf[it] != c }
                for (d in 1..n) {
                    if (others.none { has(s.cand[it], d) }) continue
                    if (!s.ways[c].all { w -> inLine.any { w[it] == d } }) continue
                    val cut = s.cand.copyOf()
                    for (o in others) cut[o] = cut[o] and bit(d).inv()
                    cutHit(s, cut) { (cell, at, digit), again ->
                        Hit(CAGE_LINE, cell, digit, at, c, cutLine = li, cutDigit = d, needsCages = again)
                    }?.let { return it }
                }
            }
        }
        if (deepest < rung(NAKED_PAIR)) return null
        for ((li, line) in b.lines.withIndex()) {
            val open = line.filter { cells[it] == 0 && s.cand[it].countOneBits() == 2 }
            for (x in open.indices) for (y in x + 1 until open.size) {
                val m = s.cand[open[x]]
                if (s.cand[open[y]] != m) continue
                val pair = listOf(open[x], open[y])
                val cut = s.cand.copyOf()
                for (o in line) if (cells[o] == 0 && o !in pair) cut[o] = cut[o] and m.inv()
                cutHit(s, cut) { (cell, at, digit), again ->
                    Hit(NAKED_PAIR, cell, digit, at, pair = pair, pairDigits = m, cutLine = li, needsCages = again)
                }?.let { return it }
            }
        }
        if (deepest < rung(WHAT_IF)) return null
        return whatIf(b, cells, s)
    }

    /** The shortest what-if: a square left two digits where supposing one breaks the board within [MAX_LINKS] steps. */
    private fun whatIf(b: Board, cells: List<Int>, s: Sight): Hit? {
        var best: Hit? = null
        for (x in cells.indices) {
            if (cells[x] != 0 || s.cand[x].countOneBits() != 2) continue
            val (p, q) = digitsOf(s.cand[x], b.n)
            for ((a, other) in listOf(p to q, q to p)) {
                val cur = cells.toMutableList().also { it[x] = a }
                val links = mutableListOf<Hit>()
                var contra: Contra? = null
                while (true) {
                    val t = Sight(b, cur)
                    contra = t.contradiction()
                    if (contra != null || links.size == MAX_LINKS || (best != null && links.size >= best.links.size)) break
                    val step = findStep(b, cur, rung(NAKED_PAIR), t) ?: break
                    cur[step.cell] = step.digit
                    links += step
                }
                if (contra != null && (best == null || links.size < best.links.size)) {
                    best = Hit(WHAT_IF, x, other, supposed = a, links = links, contra = contra)
                    if (links.isEmpty()) return best
                }
            }
        }
        return best
    }

    /**
     * The deepest rung past the cage rungs a walk of the ladder (up to [deepest]) needs to finish this board, -1 for
     * none, null when it cannot. Every single the cage rungs allow is placed in one pass (they are order-free while every
     * digit is right: candidates only shrink), and [findStep] is asked only where they stall, where it takes the step
     * the hints would. `ReckonRulesTest` checks this against the hints' own walk.
     */
    fun depth(n: Int, given: List<Int>, cageOf: List<Int>, ops: String, targets: List<Int>, deepest: Int = rung(WHAT_IF)): Int? {
        val b = Board(n, cageOf, ops, targets)
        val cells = given.toMutableList()
        var most = -1
        while (cells.any { it == 0 }) {
            val s = Sight(b, cells)
            if (s.contradiction() != null) return null
            var placed = false
            for (i in 0 until n * n) if (cells[i] == 0 && s.cand[i].countOneBits() == 1) { cells[i] = s.cand[i].countTrailingZeroBits(); placed = true }
            for (line in b.lines) for (d in 1..n) {
                val at = lonePlace(line, cells, s.cand, d)
                if (at >= 0) { cells[at] = d; placed = true }
            }
            if (placed) continue
            val hit = findStep(b, cells, deepest, s) ?: return null
            cells[hit.cell] = hit.digit
            most = maxOf(most, rung(hit.technique))
        }
        return if (ReckonLogic.isSolved(n, cells, cageOf, ops, targets)) most else null
    }

    // ---- the whole hint: mistakes, then reasoning, then the honest fallback ----------------------------------

    fun teach(s: ReckonState): PlacementStep? {
        if (s.solved) return null
        mistake(s)?.let { return it }
        return deduce(s.size, s.cells, s.cageOf, s.ops, s.targets) ?: answerFallback(FALLBACK, s.cells, s.solution)
    }

    private fun mistake(s: ReckonState): PlacementStep? {
        val n = s.size
        val wrong = s.cells.indices.firstOrNull { s.cells[it] != 0 && !s.isGiven(it) && s.cells[it] != s.solution[it] } ?: return null
        val d = s.cells[wrong]
        val clear = "Select it and tap $d again to clear it."
        fun step(explanation: String, cited: Set<Int>) = PlacementStep(MISTAKE, wrong, 0, setOf(wrong), cited, "Check this $d.", explanation)
        val b = Board(n, s.cageOf, s.ops, s.targets)
        for ((li, line) in b.lines.withIndex()) {
            if (wrong !in line) continue
            val twin = line.firstOrNull { it != wrong && s.cells[it] == d } ?: continue
            return step("This $d repeats the $d in ${lineName(n, li)}. $clear", setOf(twin))
        }
        val c = s.cageOf[wrong]
        if (wrong in ReckonLogic.cageConflicts(n, s.cells, s.cageOf, s.ops, s.targets)) {
            return step("With this $d, the ${b.label(c)} cage can no longer make its target. $clear", b.cages[c].toSet())
        }
        return step("No valid way to finish the board keeps this $d. $clear", emptySet())
    }

    // ---- explaining ------------------------------------------------------------------------------------------

    /** The distinct digit sets of [ways], as "1·2·4", in order. */
    private fun combos(ways: List<IntArray>): List<String> =
        ways.map { w -> w.sorted().joinToString("·") }.distinct().sorted()

    /** Digit sets that make the cage's target but cannot sit in its shape (a repeat in one line). */
    private fun shapeOut(b: Board, c: Int, fit: List<String>): List<String> {
        val k = b.cages[c].size
        val out = mutableListOf<String>()
        fun go(from: Int, acc: List<Int>) {
            if (acc.size == k) {
                if (ReckonLogic.makes(b.ops[c], b.targets[c], acc)) acc.joinToString("·").let { if (it !in fit) out += it }
                return
            }
            for (d in from..b.n) go(d, acc + d)
        }
        go(1, emptyList())
        return out
    }

    private fun shape(b: Board, c: Int): String {
        val sq = b.cages[c]
        val rows = sq.map { it / b.n }.distinct().size
        val cols = sq.map { it % b.n }.distinct().size
        return when {
            sq.size == 2 -> "pair"
            rows == 1 || cols == 1 -> "line"
            sq.size == 3 -> "L"
            sq.size == 4 && rows == 2 && cols == 2 -> "square"
            else -> "bent shape"
        }
    }

    /** "The 8× cage can hold 1·2·4 or 2·2·3 (2·2·2 is out: an L repeats a digit only away from its corner)." */
    private fun cageWays(b: Board, c: Int, s: Sight): Pair<String, String> {
        val all = ReckonLogic.ways(b.n, b.cages[c], b.ops[c], b.targets[c], IntArray(b.cages[c].size) { fullMask(b.n) })
        val fits = combos(all)
        val out = shapeOut(b, c, fits)
        val now = combos(s.ways[c])
        val head = "The ${b.label(c)} cage can hold ${listed(fits, "or")}"
        val why = if (out.isEmpty()) "" else " (${listed(out, "or")} would repeat a digit in one line of its ${shape(b, c)})"
        val left = if (now.size < fits.size) " Its lines leave ${listed(now, "or")}." else ""
        return "$head$why.$left" to "$head.$left"
    }

    private fun close(n: Int, hit: Hit, again: String): String {
        val r = hit.cell / n + 1
        val c = hit.cell % n + 1
        return when {
            hit.line < 0 -> "$again leaves the glowing square only ${hit.digit}."
            hit.line < n -> "$again leaves the glowing square as the only place for the ${hit.digit} in row $r."
            else -> "$again leaves the glowing square as the only place for the ${hit.digit} in column $c."
        }
    }

    private fun contraText(n: Int, b: Board, c: Contra): String = when (c) {
        is Contra.Empty -> "${squareName(n, c.square)} would have no digit left"
        is Contra.NoWay -> "the ${b.label(c.cage)} cage would have no way to make its target"
        is Contra.NoPlace -> "${lineName(n, c.line)} would have nowhere for its ${c.digit}"
    }

    fun deduce(n: Int, cells: List<Int>, cageOf: List<Int>, ops: String, targets: List<Int>): PlacementStep? {
        val b = Board(n, cageOf, ops, targets)
        val s = Sight(b, cells)
        val hit = findStep(b, cells, rung(WHAT_IF), s) ?: return null
        val x = hit.cell
        val d = hit.digit
        val holders = { digits: List<Int> ->
            digits.mapNotNull { v -> (0 until n * n).firstOrNull { cells[it] == v && (it / n == x / n || it % n == x % n) } }.toSet()
        }
        return when (hit.technique) {
            LAST -> {
                val name = lineName(n, hit.line)
                PlacementStep(LAST, x, d, b.lines[hit.line].toSet(), b.lines[hit.line].filter { cells[it] != 0 }.toSet(),
                    "Look at $name.", "${name.cap()} has every digit but $d, so its last empty square is a $d.")
            }
            ONLY_DIGIT -> {
                val seen = (1..n).filter { !has(s.base[x], it) }
                PlacementStep(ONLY_DIGIT, x, d, setOf(x), holders(seen),
                    "Which digits can this square still take?", "Its row and column already hold ${listed(seen)}, so only $d is left here.")
            }
            ONLY_PLACE -> {
                val name = lineName(n, hit.line)
                val across = if (hit.line < n) "column" else "row"
                PlacementStep(ONLY_PLACE, x, d, b.lines[hit.line].toSet(), emptySet(),
                    "Where can the $d go in $name?",
                    "${name.cap()} still needs a $d, and every other empty square in it has a $d in its $across. This one is the only place.")
            }
            CAGE_DIGIT -> {
                val c = hit.cage
                val (long, short) = cageWays(b, c, s)
                val end = " That leaves this square only $d."
                PlacementStep(CAGE_DIGIT, x, d, b.cages[c].toSet(), b.cages[c].toSet(),
                    "What can the ${b.label(c)} cage hold?",
                    fit(listOf(long + end, short + end, "The ${b.label(c)} cage's ways, against its rows and columns, leave this square only $d."), MAX_EXPLANATION))
            }
            CAGE_PLACE -> {
                val name = lineName(n, hit.line)
                PlacementStep(CAGE_PLACE, x, d, b.lines[hit.line].toSet(), emptySet(),
                    "Where can the $d go in $name, counting the cages?",
                    "${name.cap()} still needs a $d. Every other open square there sees a $d in its row or column, or sits in a cage " +
                        "that cannot take one, so it goes here.")
            }
            CAGE_LINE -> {
                val c = hit.cage
                val name = lineName(n, hit.cutLine)
                val ways = combos(s.ways[c])
                val again = if (hit.needsCages) "Counting the cages again, that" else "That"
                val head = "However the ${b.label(c)} cage is filled"
                val tail = ", a ${hit.cutDigit} lands in its part of $name, so no other square of $name can be ${hit.cutDigit}. ${close(n, hit, again)}"
                PlacementStep(CAGE_LINE, x, d, b.cages[c].toSet() + x, b.cages[c].filter { it in b.lines[hit.cutLine] }.toSet(),
                    "Where must the ${b.label(c)} cage put a ${hit.cutDigit}?",
                    fit(listOf("$head (${listed(ways, "or")})$tail", head + tail), MAX_EXPLANATION))
            }
            NAKED_PAIR -> {
                val (p, q) = digitsOf(hit.pairDigits, n)
                val name = lineName(n, hit.cutLine)
                val again = if (hit.needsCages) "Counting the cages again, that" else "That"
                PlacementStep(NAKED_PAIR, x, d, setOf(x) + hit.pair, hit.pair.toSet(),
                    "Two squares of $name share the same two digits.",
                    "Two squares of $name can only be $p or $q, so they take both, and no other square there can. ${close(n, hit, again)}")
            }
            else -> {
                val chain = hit.links.joinToString(", then ") { "${squareName(n, it.cell)} would be ${it.digit}" }
                val then = if (chain.isEmpty()) "" else "$chain, and "
                val contra = contraText(n, b, hit.contra!!)
                PlacementStep(WHAT_IF, x, d, setOf(x), hit.links.map { it.cell }.toSet(),
                    "This square can only be ${listed(listOf(hit.supposed, d).sorted(), "or")}. Try one.",
                    fit(listOf(
                        "Suppose this square were ${hit.supposed}. Then ${then}$contra. So it is $d.",
                        "Supposing this square were ${hit.supposed} leads, in ${hit.links.size + 1} steps, to a dead end: $contra. So it is $d.",
                    ), MAX_EXPLANATION))
            }
        }
    }
}
