package com.joebywan.daybook.puzzles

/**
 * Sudoku, reasoned the way a person reasons it, one named step at a time.
 *
 * As in [KingsTeacher], the split is the point. [deduce] is handed the digits the player can see and
 * nothing else, so a step it explains cannot lean on the stored answer — a guarantee of the
 * signature. The answer is read only in [teach]: to decide which of the player's digits are
 * mistakes, and for [FALLBACK], which says openly that it is pointing at the answer.
 *
 * Candidates are *derived* from the visible digits on every call (a digit is a candidate for an
 * empty cell when no cell in its row, column or box holds it). There are no pencil marks to read or
 * write, so a step can never end on "cross 7 off here": every step ends in one digit placed. The
 * eliminating techniques therefore carry their consequence with them — "7 is locked to row 2 in this
 * box, and that leaves the glowing cell as the only place for 7 in row 2" — and the move is the
 * placement.
 *
 * Techniques, tried simplest first in the order puzzle raters (Sudoku Explainer's scale) put them,
 * because the hint should be the step a person would have found next:
 * 1. [FULL_HOUSE] — a row, column or box with one empty cell left.
 * 2. [HIDDEN_SINGLE_BOX] — a digit with only one place left in a box.
 * 3. [HIDDEN_SINGLE_LINE] — a digit with only one place left in a row or column.
 * 4. [NAKED_SINGLE] — a cell whose row, column and box leave it one digit.
 * 5. [LOCKED] — pointing (a digit confined to one line within a box) or claiming (confined to one
 *    box within a line), then the single that the elimination makes.
 * 6. [NAKED_PAIR] / [HIDDEN_PAIR] — two cells that between them must hold two digits, then the
 *    single that follows.
 * 7. [WHAT_IF] — a cell with two digits left, or a digit with two places left in a unit: suppose
 *    one, follow at most [MAX_CHAIN] forced singles, reach a cell or unit with nothing left, so it
 *    is the other. As Kings' what-if, bounded so one sentence can carry it.
 *
 * Tried and dropped, measured on 300 Expert boards: two eliminations chained before a single (0
 * boards once its explanation had to fit the panel), X-wing and XY-wing (together 16 boards of
 * the 114 reaching the fallback). Even every elimination above applied to a fixpoint before looking
 * for a single left 95 Expert boards needing the fallback somewhere: the 24-clue Expert boards are
 * dug for uniqueness, not rated, and many need chains no four-line hint can carry. Without pencil
 * marks a step cannot leave eliminations behind for the next one, so every step has to end in a
 * placement, and that is the ceiling here.
 *
 * Orders never come from a hash: every loop walks cells, units or digits in index order, so the same
 * board gets the same hint on every platform (CLAUDE.md, "Hash iteration order").
 */
internal object SudokuTeacher {

    const val FULL_HOUSE = "full-house"
    const val HIDDEN_SINGLE_BOX = "hidden-single-box"
    const val HIDDEN_SINGLE_LINE = "hidden-single-line"
    const val NAKED_SINGLE = "naked-single"
    const val LOCKED = "locked-candidates"
    const val NAKED_PAIR = "naked-pair"
    const val HIDDEN_PAIR = "hidden-pair"
    const val WHAT_IF = "what-if"
    const val FALLBACK = "fallback"
    const val MISTAKE = "mistake"

    /**
     * The hint panel shows four lines. A step whose explanation would not fit is passed over for the
     * next one that does, as on Tower, rather than shown cut off.
     */
    const val MAX_EXPLANATION = 200

    /** How many forced digits a [WHAT_IF] may walk through before it stops being followable. */
    const val MAX_CHAIN = 2

    /** Highlight index of digit [d]'s key under the board; 0..80 are the cells. */
    fun pad(d: Int): Int = 81 + d

    /** Every technique in the order [deduce] tries them, for reports. */
    val TECHNIQUES = listOf(
        FULL_HOUSE, HIDDEN_SINGLE_BOX, HIDDEN_SINGLE_LINE, NAKED_SINGLE,
        LOCKED, NAKED_PAIR, HIDDEN_PAIR, WHAT_IF, FALLBACK,
    )

    /**
     * One step: the digit to place (or, for a mistake, the cell to clear), where to look, and why.
     * [digit] is 0 for a clear.
     */
    class Step(
        val technique: String,
        val cell: Int,
        val digit: Int,
        val focus: Set<Int>,
        val cited: Set<Int>,
        val nudge: String,
        val explanation: String,
    )

    // ---- units ------------------------------------------------------------------------------------

    private enum class Kind { BOX, ROW, COLUMN }

    private class Unit(val kind: Kind, val id: Int, val cells: List<Int>) {
        val name: String
            get() = when (kind) {
                Kind.BOX -> "the ${boxNames[id]} box"
                Kind.ROW -> "row ${id + 1}"
                Kind.COLUMN -> "column ${id + 1}"
            }
    }

    private val boxNames = listOf(
        "top-left", "top-middle", "top-right",
        "middle-left", "centre", "middle-right",
        "bottom-left", "bottom-middle", "bottom-right",
    )

    /** Boxes first: a box is what a player scans first on a Sudoku, and hidden singles in one are easiest. */
    private val units: List<Unit> = buildList {
        for (b in 0 until 9) add(Unit(Kind.BOX, b, List(9) { (b / 3 * 3 + it / 3) * 9 + b % 3 * 3 + it % 3 }))
        for (r in 0 until 9) add(Unit(Kind.ROW, r, List(9) { r * 9 + it }))
        for (c in 0 until 9) add(Unit(Kind.COLUMN, c, List(9) { it * 9 + c }))
    }

    private fun boxOf(cell: Int) = cell / 9 / 3 * 3 + cell % 9 / 3
    private fun unitsOf(cell: Int): List<Unit> =
        listOf(units[boxOf(cell)], units[9 + cell / 9], units[18 + cell % 9])

    private fun bit(d: Int) = 1 shl d
    private fun has(mask: Int, d: Int) = mask and bit(d) != 0
    private fun digitsOf(mask: Int) = (1..9).filter { has(mask, it) }

    // ---- what the player can see -------------------------------------------------------------------

    /**
     * The digits and the candidates they leave, with any eliminations a step has argued for struck
     * off. Candidates are a 1..9 bitmask per empty cell; a filled cell's mask is 0.
     */
    private class Sight(val cells: List<Int>, val cand: IntArray) {
        fun copyWithout(eliminations: List<Pair<Int, Int>>): Sight {
            val next = cand.copyOf()
            for ((cell, d) in eliminations) next[cell] = next[cell] and bit(d).inv()
            return Sight(cells, next)
        }

        fun places(u: Unit, d: Int): List<Int> = u.cells.filter { cells[it] == 0 && has(cand[it], d) }

        fun holds(u: Unit, d: Int): Boolean = u.cells.any { cells[it] == d }
    }

    private fun sightOf(cells: List<Int>): Sight = Sight(cells, IntArray(81) { i ->
        if (cells[i] != 0) 0
        else {
            var mask = 0b11_1111_1110
            for (p in Sudoku.peers(i)) if (cells[p] != 0) mask = mask and bit(cells[p]).inv()
            mask
        }
    })

    // ---- the whole hint: mistakes, then reasoning, then the honest fallback -----------------------

    /**
     * The next thing to show this player, or null on a solved board. Mistakes come first: every step
     * reasoned from a board with a wrong digit on it could be reasoning from a falsehood.
     */
    fun teach(s: SudokuState): Step? {
        if (s.solved) return null
        mistake(s)?.let { return it }
        return deduce(s.cells) ?: fallback(s)
    }

    private fun mistake(s: SudokuState): Step? {
        val wrong = s.cells.indices.firstOrNull { s.cells[it] != 0 && s.cells[it] != s.solution[it] }
            ?: return null
        val d = s.cells[wrong]
        val clear = "Select it and tap $d again to clear it"
        fun step(explanation: String, cited: Set<Int>) = Step(
            MISTAKE, wrong, 0, setOf(wrong), cited, "Check this $d.", explanation,
        )

        // A clash the red digits already show.
        for (u in unitsOf(wrong)) {
            val twin = u.cells.firstOrNull { it != wrong && s.cells[it] == d } ?: continue
            return step("This $d clashes with the $d in ${u.name}. $clear.", setOf(twin))
        }

        // Otherwise, reason from the correct digits plus this one: does it leave somewhere empty?
        val seen = s.cells.mapIndexed { i, v -> if (v != 0 && v != s.solution[i] && i != wrong) 0 else v }
        val sight = sightOf(seen)
        for (p in Sudoku.peers(wrong)) {
            if (seen[p] == 0 && sight.cand[p] == 0) {
                val shared = unitsOf(p).first { wrong in it.cells }
                return step(
                    "With a $d here, the marked cell in ${shared.name} would have no digit left: its " +
                        "row, column and box would hold all nine between them. $clear.",
                    setOf(p),
                )
            }
        }
        for (u in units) {
            if (u.cells.none { it in Sudoku.peers(wrong) }) continue
            for (e in 1..9) {
                if (sight.holds(u, e) || sight.places(u, e).isNotEmpty()) continue
                return step(
                    "With a $d here, ${u.name} would have nowhere left for a $e. $clear.",
                    u.cells.toSet(),
                )
            }
        }
        return step("This $d isn't part of the answer. $clear, and look again.", emptySet())
    }

    /**
     * Nothing short enough to explain applies, so point at a digit from the answer — in the empty
     * cell with the fewest candidates, which is where the player was most likely stuck. Says plainly
     * that it is doing this.
     */
    private fun fallback(s: SudokuState): Step? {
        val sight = sightOf(s.cells)
        val cell = s.cells.indices
            .filter { s.cells[it] == 0 }
            .minByOrNull { sight.cand[it].countOneBits() }
            ?: return null
        val d = s.solution[cell]
        return Step(
            FALLBACK, cell, d,
            focus = setOf(cell),
            cited = emptySet(),
            nudge = "This one takes a longer chain. Look at the glowing cell.",
            explanation = "This one needs a longer chain than a hint can walk through, so here's a " +
                "digit from the answer: the glowing cell is $d.",
        )
    }

    // ---- reasoning from what the player can see ----------------------------------------------------

    /**
     * The simplest step visible on this board, or null when none of the techniques applies.
     *
     * Takes no answer on purpose — see the class comment. [cells] is 81 digits, 0 for empty; the
     * caller has already dealt with any that are mistakes.
     */
    fun deduce(cells: List<Int>): Step? {
        val sight = sightOf(cells)
        return fullHouse(sight)
            ?: hiddenSingle(sight, Kind.BOX)
            ?: hiddenSingle(sight, null)
            ?: nakedSingle(sight)
            ?: withElimination(sight)
            ?: whatIf(sight)
    }

    private fun fullHouse(s: Sight): Step? {
        for (u in units) {
            val empty = u.cells.filter { s.cells[it] == 0 }
            if (empty.size != 1) continue
            val d = (1..9).first { !s.holds(u, it) }
            return Step(
                FULL_HOUSE, empty[0], d,
                focus = u.cells.toSet(),
                cited = u.cells.toSet(),
                nudge = "Look at ${u.name}.",
                explanation = "${u.name.cap()} has one empty cell left, and $d is the only digit it's " +
                    "missing, so $d goes there.",
            )
        }
        return null
    }

    /** With [kind] BOX, boxes only; with null, rows and columns. */
    private fun hiddenSingle(s: Sight, kind: Kind?): Step? {
        for (u in units) {
            if ((kind == Kind.BOX) != (u.kind == Kind.BOX)) continue
            for (d in 1..9) {
                if (s.holds(u, d)) continue
                val places = s.places(u, d)
                if (places.size != 1) continue
                val cell = places[0]
                return Step(
                    if (u.kind == Kind.BOX) HIDDEN_SINGLE_BOX else HIDDEN_SINGLE_LINE, cell, d,
                    focus = u.cells.toSet(),
                    cited = u.cells.toSet() + blockers(s, u, d, cell),
                    nudge = "Look at ${u.name}.",
                    explanation = "Where can $d go in ${u.name}? Every other empty cell there already " +
                        "sees a $d in its row, column or box, so the $d must go in the glowing cell.",
                )
            }
        }
        return null
    }

    /** The visible [d]s that rule [d] out of the other empty cells of [u], one per cell, lowest first. */
    private fun blockers(s: Sight, u: Unit, d: Int, except: Int): Set<Int> =
        u.cells.filter { it != except && s.cells[it] == 0 }
            .mapNotNull { c -> Sudoku.peers(c).firstOrNull { s.cells[it] == d } }
            .sorted()
            .toSet()

    private fun nakedSingle(s: Sight): Step? {
        for (cell in 0 until 81) {
            if (s.cells[cell] != 0 || s.cand[cell].countOneBits() != 1) continue
            val d = digitsOf(s.cand[cell])[0]
            return Step(
                NAKED_SINGLE, cell, d,
                focus = setOf(cell),
                cited = Sudoku.peers(cell).filter { s.cells[it] != 0 }.toSet(),
                nudge = "What can go in the glowing cell?",
                explanation = "Its row, column and box already hold every digit except $d, so $d is " +
                    "the only one left for it.",
            )
        }
        return null
    }

    // ---- eliminations, each carried through to the single it makes ---------------------------------

    /** Some candidates struck off, and the sentence that justifies it. */
    private class Elimination(
        val technique: String,
        val removed: List<Pair<Int, Int>>,
        val pattern: Set<Int>,
        val where: Set<Int>,
        val nudge: String,
        val reason: String,
    )

    private fun eliminations(s: Sight): Sequence<Elimination> = sequence {
        yieldAll(locked(s))
        yieldAll(nakedPairs(s))
        yieldAll(hiddenPairs(s))
    }

    /** Pointing, then claiming. */
    private fun locked(s: Sight): Sequence<Elimination> = sequence {
        for (box in units.filter { it.kind == Kind.BOX }) {
            for (d in 1..9) {
                val places = s.places(box, d)
                if (places.size < 2) continue
                for (lineKind in listOf(Kind.ROW, Kind.COLUMN)) {
                    val lines = places.map { lineOf(lineKind, it) }.distinct()
                    if (lines.size != 1) continue
                    val line = units[(if (lineKind == Kind.ROW) 9 else 18) + lines[0]]
                    val removed = s.places(line, d).filter { it !in box.cells }.map { it to d }
                    if (removed.isEmpty()) continue
                    yield(
                        Elimination(
                            LOCKED, removed, places.toSet(), box.cells.toSet() + line.cells,
                            nudge = "Look at ${box.name}, then ${line.name}.",
                            reason = "In ${box.name}, $d fits only in ${line.name}, so the rest of " +
                                "${line.name} can't be $d.",
                        )
                    )
                }
            }
        }
        for (line in units.filter { it.kind != Kind.BOX }) {
            for (d in 1..9) {
                val places = s.places(line, d)
                if (places.size < 2) continue
                val boxes = places.map(::boxOf).distinct()
                if (boxes.size != 1) continue
                val box = units[boxes[0]]
                val removed = s.places(box, d).filter { it !in line.cells }.map { it to d }
                if (removed.isEmpty()) continue
                yield(
                    Elimination(
                        LOCKED, removed, places.toSet(), line.cells.toSet() + box.cells,
                        nudge = "Look at ${line.name}, then ${box.name}.",
                        reason = "In ${line.name}, $d fits only in ${box.name}, so the rest of that " +
                            "box can't be $d.",
                    )
                )
            }
        }
    }

    private fun nakedPairs(s: Sight): Sequence<Elimination> = sequence {
        for (u in units) {
            val empty = u.cells.filter { s.cells[it] == 0 }
            for (i in empty.indices) for (j in i + 1 until empty.size) {
                val a = empty[i]
                val b = empty[j]
                val mask = s.cand[a]
                if (mask.countOneBits() != 2 || s.cand[b] != mask) continue
                val (x, y) = digitsOf(mask)
                val removed = empty.filter { it != a && it != b }
                    .flatMap { c -> listOf(x, y).filter { has(s.cand[c], it) }.map { c to it } }
                if (removed.isEmpty()) continue
                yield(
                    Elimination(
                        NAKED_PAIR, removed, setOf(a, b), u.cells.toSet(),
                        nudge = "Look at ${u.name}.",
                        reason = "Two cells of ${u.name} can only be $x or $y, so they take both, and " +
                            "no other cell there can be $x or $y.",
                    )
                )
            }
        }
    }

    private fun hiddenPairs(s: Sight): Sequence<Elimination> = sequence {
        for (u in units) {
            val placesOf = (1..9).associateWith { d -> if (s.holds(u, d)) emptyList() else s.places(u, d) }
            for (x in 1..9) for (y in x + 1..9) {
                val px = placesOf.getValue(x)
                if (px.size != 2 || placesOf.getValue(y) != px) continue
                val removed = px.flatMap { c ->
                    digitsOf(s.cand[c]).filter { it != x && it != y }.map { c to it }
                }
                if (removed.isEmpty()) continue
                yield(
                    Elimination(
                        HIDDEN_PAIR, removed, px.toSet(), u.cells.toSet(),
                        nudge = "Look at ${u.name}.",
                        reason = "In ${u.name}, $x and $y fit only in the same two cells, so those two " +
                            "can't hold anything else.",
                    )
                )
            }
        }
    }

    /**
     * A single that exists only because of [removed]: a cell those eliminations leave with one digit,
     * or a digit they leave with one place in a unit. Returned as the step's closing sentence.
     */
    private class Single(val cell: Int, val digit: Int, val unit: Unit?) {
        fun sentence(): String =
            if (unit == null) "That leaves the glowing cell only one digit: $digit."
            else "That leaves the glowing cell as the only place for $digit in ${unit.name}."
    }

    private fun singleAfter(after: Sight, removed: List<Pair<Int, Int>>): Single? {
        // Hidden singles first: the digit just struck off is the one the player is following.
        for ((cell, d) in removed) {
            for (u in unitsOf(cell)) {
                if (after.holds(u, d)) continue
                val places = after.places(u, d)
                if (places.size == 1) return Single(places[0], d, u)
            }
        }
        for (cell in removed.map { it.first }.distinct().sorted()) {
            if (after.cand[cell].countOneBits() == 1) return Single(cell, digitsOf(after.cand[cell])[0], null)
        }
        return null
    }

    private fun withElimination(s: Sight): Step? {
        for (e in eliminations(s)) {
            val after = s.copyWithout(e.removed)
            val single = singleAfter(after, e.removed) ?: continue
            val text = "${e.reason} ${single.sentence()}"
            if (text.length > MAX_EXPLANATION) continue
            return Step(
                e.technique, single.cell, single.digit,
                focus = e.where,
                cited = e.pattern + (single.unit?.cells.orEmpty()),
                nudge = e.nudge,
                explanation = text,
            )
        }
        return null
    }

    // ---- what if: a short chain of forced digits that ends in a contradiction ----------------------

    /** How a supposed digit breaks the board: a cell with no digit left, or a unit with no place for one. */
    private class Contradiction(val cell: Int?, val unit: Unit?, val digit: Int) {
        fun phrase(afterForced: Boolean): String =
            if (cell != null) "${if (afterForced) "another" else "the"} marked cell would have no digit left"
            else "${unit!!.name} would have nowhere left for a $digit"

        fun cells(): Set<Int> = if (cell != null) setOf(cell) else unit!!.cells.toSet()
    }

    /** The digits a supposition forces, in order, and where it breaks. */
    private class Chain(val forced: List<Pair<Int, Int>>, val broken: Contradiction)

    private fun contradiction(s: Sight): Contradiction? {
        for (c in 0 until 81) if (s.cells[c] == 0 && s.cand[c] == 0) return Contradiction(c, null, 0)
        for (u in units) for (d in 1..9) {
            if (!s.holds(u, d) && s.places(u, d).isEmpty()) return Contradiction(null, u, d)
        }
        return null
    }

    /** A single on [s]: hidden in a box, hidden in a line, then naked. The same order [deduce] uses. */
    private fun anySingle(s: Sight): Pair<Int, Int>? {
        for (boxFirst in listOf(true, false)) for (u in units) {
            if ((u.kind == Kind.BOX) != boxFirst) continue
            for (d in 1..9) {
                if (s.holds(u, d)) continue
                val places = s.places(u, d)
                if (places.size == 1) return places[0] to d
            }
        }
        for (c in 0 until 81) if (s.cells[c] == 0 && s.cand[c].countOneBits() == 1) return c to digitsOf(s.cand[c])[0]
        return null
    }

    /** With [cell] supposed to be [digit], the forced singles up to [MAX_CHAIN] and where they break. */
    private fun chainFrom(s: Sight, cell: Int, digit: Int): Chain? {
        var cells = s.cells.toMutableList().also { it[cell] = digit }
        val forced = mutableListOf<Pair<Int, Int>>()
        while (true) {
            val cur = sightOf(cells)
            contradiction(cur)?.let { return Chain(forced.toList(), it) }
            if (forced.size == MAX_CHAIN) return null
            val (c, d) = anySingle(cur) ?: return null
            forced += c to d
            cells = cells.toMutableList().also { it[c] = d }
        }
    }

    private fun whatIf(s: Sight): Step? {
        var best: Step? = null
        var bestLength = Int.MAX_VALUE
        fun consider(target: Int, digit: Int, supposed: Int, supposedDigit: Int, lead: String, close: String) {
            val chain = chainFrom(s, supposed, supposedDigit) ?: return
            if (chain.forced.size >= bestLength) return
            val digits = chain.forced.mapIndexed { i, (_, d) ->
                if (chain.forced.take(i).any { it.second == d }) "another $d" else "a $d"
            }
            val middle = when (digits.size) {
                0 -> ""
                1 -> "that would force ${digits[0]} into a marked cell, and then "
                else -> "that would force ${digits.joinToString(" and then ")} into marked cells, and then "
            }
            val text = "$lead, $middle${chain.broken.phrase(digits.isNotEmpty())}. $close"
            if (text.length > MAX_EXPLANATION) return
            bestLength = chain.forced.size
            best = Step(
                WHAT_IF, target, digit,
                focus = setOf(target, supposed),
                cited = chain.forced.map { it.first }.toSet() + chain.broken.cells() + supposed,
                nudge = if (supposed == target) "What if the glowing cell were $supposedDigit?"
                else "What if this $digit went in the other place?",
                explanation = text,
            )
        }
        // A cell with two digits left: suppose one, and it breaks, so it's the other.
        for (c in 0 until 81) {
            if (s.cells[c] != 0 || s.cand[c].countOneBits() != 2) continue
            val (a, b) = digitsOf(s.cand[c])
            consider(c, b, c, a, "If the glowing cell were $a", "So it can't be $a, and must be $b.")
            consider(c, a, c, b, "If the glowing cell were $b", "So it can't be $b, and must be $a.")
        }
        // A digit with two places left in a unit: suppose one, and it breaks, so it's the other.
        for (u in units) for (d in 1..9) {
            if (s.holds(u, d)) continue
            val places = s.places(u, d)
            if (places.size != 2) continue
            val (p, q) = places
            consider(p, d, q, d, "${u.name.cap()} has two places for $d. If it went in the other one", "So $d goes in the glowing cell.")
            consider(q, d, p, d, "${u.name.cap()} has two places for $d. If it went in the other one", "So $d goes in the glowing cell.")
        }
        return best
    }

    // ---- small helpers -----------------------------------------------------------------------------

    private fun lineOf(kind: Kind, cell: Int) = if (kind == Kind.ROW) cell / 9 else cell % 9

    private fun String.cap() = replaceFirstChar { it.uppercaseChar() }
    private fun String.decap() = replaceFirstChar { it.lowercaseChar() }
}
