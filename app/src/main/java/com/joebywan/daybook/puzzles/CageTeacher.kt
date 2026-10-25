package com.joebywan.daybook.puzzles

import com.joebywan.daybook.puzzles.CageLogic.Board
import com.joebywan.daybook.puzzles.CageLogic.Group
import com.joebywan.daybook.puzzles.CageLogic.Kind
import com.joebywan.daybook.puzzles.CageLogic.Sight

/**
 * Cage Sudoku, reasoned the way a person reasons it, one named step at a time.
 *
 * As in [SudokuTeacher] the split is the point: [deduce] is handed the digits the player can see and the
 * cages (which are printed on the board), and nothing else, so a step it explains cannot lean on the
 * stored answer. The answer is read only in [teach], to decide which of the player's digits are mistakes
 * and for [FALLBACK], which says openly that it is pointing at the answer. The player's pencil marks are
 * never read or written, so every step ends in one digit placed.
 *
 * Techniques, simplest first. The first four see only the plain Sudoku view (a square's candidates are the
 * digits its row, column, box and cage do not hold):
 * 1. [FULL_HOUSE], 2. [CAGE_LAST] (a cage with one square left: its total less the rest), 3. [HIDDEN_SINGLE_BOX]
 * and [HIDDEN_SINGLE_LINE], 4. [NAKED_SINGLE].
 * Then the totals come in, one group at a time, each carried through to the single it makes:
 * 5. [CAGE_COMBO] — the open squares of one cage must total what is left, which only certain digit
 *    combinations make ("a 2-cage totalling 3 is 1 and 2"); 6. [FORTY_FIVE] — a row, column or box holds 45, so
 *    the squares left over when whole cages are taken off (innies) or the squares the cages spill into
 *    (outies) have a known total, the same reasoning on that group.
 * 7. [CAGE_CHAIN] — what is left once every group is worked through together. This is the generator's own
 *    ladder ([CageLogic.settle]), which is why a board it ships always has a step; it cannot name a single
 *    group, so it says so, and it is counted per tier in `docs/TEACHING.md`.
 *
 * Orders never come from a hash: every loop walks squares, units, cages and digits in index order.
 */
internal object CageTeacher {

    const val FULL_HOUSE = "full-house"
    const val CAGE_LAST = "cage-last"
    const val HIDDEN_SINGLE_BOX = "hidden-single-box"
    const val HIDDEN_SINGLE_LINE = "hidden-single-line"
    const val NAKED_SINGLE = "naked-single"
    const val CAGE_COMBO = "cage-combination"
    const val FORTY_FIVE = "forty-five"
    const val CAGE_CHAIN = "cage-chain"
    const val FALLBACK = "fallback"
    const val MISTAKE = "mistake"

    /** The hint panel shows four lines. A step that would not fit is passed over for the next that does. */
    const val MAX_EXPLANATION = 200

    val TECHNIQUES = listOf(
        FULL_HOUSE, CAGE_LAST, HIDDEN_SINGLE_BOX, HIDDEN_SINGLE_LINE, NAKED_SINGLE,
        CAGE_COMBO, FORTY_FIVE, CAGE_CHAIN, FALLBACK,
    )

    /** One step: the digit to place (for a mistake, the cell to clear: [digit] is 0), where to look, and why. */
    class Step(
        val technique: String,
        val cell: Int,
        val digit: Int,
        val focus: Set<Int>,
        val cited: Set<Int>,
        val nudge: String,
        val explanation: String,
    )

    private fun unitName(u: Int) = when {
        u < 9 -> "the ${CageLogic.BOX_NAMES[u]} box"
        u < 18 -> "row ${u - 8}"
        else -> "column ${u - 17}"
    }

    private fun String.cap() = replaceFirstChar { it.uppercaseChar() }

    private fun digitsOf(mask: Int) = (1..9).filter { mask and (1 shl it) != 0 }

    /** "1", "1 and 2", "1, 2 and 4". */
    private fun listed(digits: List<Int>) =
        if (digits.size == 1) "${digits[0]}" else digits.dropLast(1).joinToString(", ") + " and " + digits.last()

    // ---- the whole hint: mistakes, then reasoning, then the honest fallback -----------------------

    fun teach(s: CageState): Step? {
        if (s.solved) return null
        val board = Board(s.cageOf, s.sums)
        mistake(s, board)?.let { return it }
        return deduce(board, s.cells) ?: fallback(s, board)
    }

    private fun mistake(s: CageState, board: Board): Step? {
        val cells = s.cells
        val solution = s.grid.solution
        val wrong = cells.indices.firstOrNull { cells[it] != 0 && cells[it] != solution[it] } ?: return null
        val d = cells[wrong]
        val clear = "Select it and tap $d again to clear it"
        fun step(explanation: String, cited: Set<Int>) = Step(
            MISTAKE, wrong, 0, setOf(wrong), cited, "Check this $d.", explanation,
        )

        for (u in CageLogic.unitsOf(wrong)) {
            val twin = CageLogic.units[u].firstOrNull { it != wrong && cells[it] == d } ?: continue
            return step("This $d clashes with the $d in ${unitName(u)}. $clear.", setOf(twin))
        }
        val cage = board.cages[s.cageOf[wrong]]
        val total = s.sums[s.cageOf[wrong]]
        cage.firstOrNull { it != wrong && cells[it] == d }?.let {
            return step("This $d repeats a $d in its cage, and a cage never repeats a digit. $clear.", setOf(it))
        }
        val filled = cage.sumOf { cells[it] }
        if (filled > total) {
            return step("This cage totals $total, and its digits already add up to $filled. $clear.", cage.toSet())
        }
        if (cage.none { cells[it] == 0 } && filled != total) {
            return step("This cage totals $total, but its digits add up to $filled. $clear.", cage.toSet())
        }

        // Otherwise, reason from the correct digits plus this one: does it leave something impossible?
        val seen = IntArray(81) { i -> if (cells[i] != 0 && cells[i] != solution[i] && i != wrong) 0 else cells[i] }
        val plain = Sight(board, seen, prune = false)
        for (i in 0 until 81) {
            if (seen[i] == 0 && plain.cand[i] == 0) {
                return step("With a $d here, the marked square would have no digit left. $clear.", setOf(i))
            }
        }
        val full = Sight(board, seen)
        if (full.broken) {
            for (g in board.groups) {
                val open = full.open(g)
                if (open.isEmpty() || (full.allowed(g)?.any { it == 0 } != true)) continue
                val what = if (g.kind == Kind.CAGE) "the cage totalling ${g.sum} could not be made"
                else "the ${g.cells.size} marked squares could not total ${g.sum}, as the whole rows, columns or boxes need"
                return step("With a $d here, $what. $clear.", g.cells.toSet())
            }
        }
        return step("This $d isn't part of the answer. $clear, and look again.", emptySet())
    }

    /** Nothing short enough to explain applied: point at a digit from the answer, and say so plainly. */
    private fun fallback(s: CageState, board: Board): Step? {
        val sight = Sight(board, s.cells.toIntArray())
        val cell = s.cells.indices.filter { s.cells[it] == 0 }
            .minByOrNull { sight.cand[it].countOneBits() } ?: return null
        val d = s.grid.solution[cell]
        return Step(
            FALLBACK, cell, d, focus = setOf(cell), cited = emptySet(),
            nudge = "This one takes a longer chain. Look at the glowing cell.",
            explanation = "This one needs a longer chain than a hint can walk through, so here's a " +
                "digit from the answer: the glowing cell is $d.",
        )
    }

    // ---- reasoning from what the player can see ----------------------------------------------------

    /** The simplest step visible on this board, or null when none of the techniques applies. */
    fun deduce(board: Board, cells: List<Int>): Step? {
        val arr = cells.toIntArray()
        val plain = Sight(board, arr, prune = false)
        return fullHouse(plain)
            ?: cageLast(board, arr)
            ?: hiddenSingle(plain, boxes = true)
            ?: hiddenSingle(plain, boxes = false)
            ?: nakedSingle(plain)
            ?: group(board, plain, Kind.CAGE)
            ?: group(board, plain, Kind.INNIE)
            ?: chain(board, arr)
    }

    private fun fullHouse(s: Sight): Step? {
        for (u in 0 until 27) {
            val empty = CageLogic.units[u].filter { s.cells[it] == 0 }
            if (empty.size != 1) continue
            val d = (1..9).first { !s.holds(u, it) }
            val all = CageLogic.units[u].toSet()
            return Step(
                FULL_HOUSE, empty[0], d, focus = all, cited = all,
                nudge = "Look at ${unitName(u)}.",
                explanation = "${unitName(u).cap()} has one empty cell left, and $d is the only digit it's " +
                    "missing, so $d goes there.",
            )
        }
        return null
    }

    private fun cageLast(board: Board, cells: IntArray): Step? {
        for (k in board.cages.indices) {
            val cage = board.cages[k]
            val empty = cage.filter { cells[it] == 0 }
            if (empty.size != 1) continue
            val d = board.sums[k] - cage.sumOf { cells[it] }
            if (d !in 1..9) continue
            val explanation = if (cage.size == 1) "This cage is a single square, so its total, ${board.sums[k]}, is the digit."
            else "This cage totals ${board.sums[k]} and its other squares add up to ${board.sums[k] - d}, so " +
                "the glowing square is $d."
            return Step(
                CAGE_LAST, empty[0], d, focus = cage.toSet(), cited = cage.toSet(),
                nudge = "Look at the cage totalling ${board.sums[k]}.",
                explanation = explanation,
            )
        }
        return null
    }

    private fun hiddenSingle(s: Sight, boxes: Boolean): Step? {
        for (u in 0 until 27) {
            if ((u < 9) != boxes) continue
            for (d in 1..9) {
                if (s.holds(u, d)) continue
                val places = s.places(u, d)
                if (places.size != 1) continue
                val cell = places[0]
                val blockers = CageLogic.units[u].filter { it != cell && s.cells[it] == 0 }
                    .mapNotNull { c ->
                        (Sudoku.peers(c).toList() + s.board.mates[c].toList()).sorted().firstOrNull { s.cells[it] == d }
                    }.toSet()
                return Step(
                    if (boxes) HIDDEN_SINGLE_BOX else HIDDEN_SINGLE_LINE, cell, d,
                    focus = CageLogic.units[u].toSet(),
                    cited = CageLogic.units[u].toSet() + blockers,
                    nudge = "Look at ${unitName(u)}.",
                    explanation = "Every other empty cell in ${unitName(u)} already sees a $d in its row, " +
                        "column, box or cage. So ${unitName(u)}'s $d goes in the glowing cell.",
                )
            }
        }
        return null
    }

    private fun nakedSingle(s: Sight): Step? {
        for (cell in 0 until 81) {
            if (s.cells[cell] != 0 || s.cand[cell].countOneBits() != 1) continue
            val d = digitsOf(s.cand[cell])[0]
            return Step(
                NAKED_SINGLE, cell, d,
                focus = setOf(cell),
                cited = (Sudoku.peers(cell).toList() + s.board.mates[cell].toList()).filter { s.cells[it] != 0 }.toSet(),
                nudge = "What can go in the glowing cell?",
                explanation = "Its row, column, box and cage already hold every digit except $d, so $d is " +
                    "the only one left for it.",
            )
        }
        return null
    }

    // ---- the cage totals, one group at a time ------------------------------------------------------

    private fun lead(s: Sight, g: Group): String {
        return when (g.kind) {
            Kind.CAGE -> {
                val open = s.open(g)
                val r = s.remaining(g)
                val fits = s.fitting(g)
                val head = if (open.size == g.cells.size) "The ${open.size} squares of this cage total $r"
                else "The ${open.size} open squares of this cage still need $r"
                if (fits.size == 1) "$head, which only ${listed(digitsOf(fits[0]))} make."
                else "$head, so they can only use ${listed(digitsOf(fits.fold(0) { a, m -> a or m }))}."
            }
            Kind.INNIE -> {
                val sc = CageLogic.scopes[g.scope]
                val whole = 45 * sc.unitCount
                "${sc.name.cap()} ${if (sc.unitCount == 1) "holds" else "hold"} $whole and the cages inside " +
                    "${if (sc.unitCount == 1) "it" else "them"} add up to ${whole - g.sum}, so the marked squares total ${g.sum}."
            }
            Kind.OUTIE -> {
                val sc = CageLogic.scopes[g.scope]
                val whole = 45 * sc.unitCount
                "${sc.name.cap()} ${if (sc.unitCount == 1) "holds" else "hold"} $whole, but the cages touching " +
                    "${if (sc.unitCount == 1) "it" else "them"} add up to ${g.sum + whole}, so the squares outside total ${g.sum}."
            }
        }
    }

    /** CAGE_COMBO for [Kind.CAGE]; FORTY_FIVE for [Kind.INNIE] (and the outies). */
    private fun group(board: Board, plain: Sight, kind: Kind): Step? {
        for (g in board.groups) {
            if ((g.kind == Kind.CAGE) != (kind == Kind.CAGE)) continue
            val open = plain.open(g)
            if (open.isEmpty()) continue
            val allow = plain.allowed(g) ?: continue
            val after = plain.cand.copyOf()
            val removed = ArrayList<Pair<Int, Int>>()
            for (a in open.indices) for (d in 1..9) {
                if (after[open[a]] and (1 shl d) != 0 && allow[a] and (1 shl d) == 0) {
                    after[open[a]] = after[open[a]] and (1 shl d).inv()
                    removed += open[a] to d
                }
            }
            if (removed.isEmpty()) continue
            val single = singleAfter(plain, after, g, removed) ?: continue
            val text = "${lead(plain, g)} ${single.sentence}"
            if (text.length > MAX_EXPLANATION) continue
            val scope = if (g.scope >= 0) CageLogic.scopes[g.scope] else null
            return Step(
                if (g.kind == Kind.CAGE) CAGE_COMBO else FORTY_FIVE, single.cell, single.digit,
                focus = g.cells.toSet() + (scope?.cells?.toSet() ?: emptySet()) + single.cell,
                cited = g.cells.toSet() + (single.unit?.let { CageLogic.units[it].toSet() } ?: emptySet()),
                nudge = if (scope != null) "Look at ${scope.name}." else "Look at the cage totalling ${g.sum}.",
                explanation = text,
            )
        }
        return null
    }

    private class Single(val cell: Int, val digit: Int, val unit: Int?, val sentence: String)

    /** A single that exists only because of [removed]: a square of [g] left with one digit, or a digit left one place in a unit. */
    private fun singleAfter(plain: Sight, after: IntArray, g: Group, removed: List<Pair<Int, Int>>): Single? {
        for (c in g.cells.sorted()) {
            if (plain.cells[c] == 0 && after[c].countOneBits() == 1) {
                val d = digitsOf(after[c])[0]
                return Single(c, d, null, "That leaves the glowing square only $d.")
            }
        }
        for ((cell, d) in removed) {
            for (u in CageLogic.unitsOf(cell)) {
                if (plain.holds(u, d)) continue
                val places = CageLogic.units[u].filter { plain.cells[it] == 0 && after[it] and (1 shl d) != 0 }
                if (places.size == 1) {
                    return Single(places[0], d, u, "That leaves the glowing square the only place for $d in ${unitName(u)}.")
                }
            }
        }
        return null
    }

    // ---- everything together ----------------------------------------------------------------------

    /** What the generator's ladder places that no single group explains: every group worked through together. */
    private fun chain(board: Board, cells: IntArray): Step? {
        val s = Sight(board, cells)
        for (cell in 0 until 81) {
            if (cells[cell] != 0 || s.cand[cell].countOneBits() != 1) continue
            val d = digitsOf(s.cand[cell])[0]
            return Step(
                CAGE_CHAIN, cell, d,
                focus = board.cages[board.cageOf[cell]].toSet() + cell,
                cited = board.cages[board.cageOf[cell]].toSet(),
                nudge = "What do the cage totals leave for the glowing square?",
                explanation = "Work the cage totals through together with the digits around them, and the glowing " +
                    "square can only be $d.",
            )
        }
        for (u in 0 until 27) for (d in 1..9) {
            if (s.holds(u, d)) continue
            val places = s.places(u, d)
            if (places.size != 1) continue
            return Step(
                CAGE_CHAIN, places[0], d,
                focus = CageLogic.units[u].toSet(),
                cited = board.cages[board.cageOf[places[0]]].toSet(),
                nudge = "Look at ${unitName(u)}.",
                explanation = "Work the cage totals through together with the digits around them, and " +
                    "${unitName(u)} has only one place left for $d: the glowing square.",
            )
        }
        return null
    }
}
