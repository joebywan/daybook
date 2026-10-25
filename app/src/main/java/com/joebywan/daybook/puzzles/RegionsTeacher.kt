package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.PlacementStep
import com.joebywan.daybook.core.bit
import com.joebywan.daybook.core.has


/**
 * Regions, reasoned a step at a time.
 *
 * As in [InequalityTeacher], the split is the point: [deduce] and [findStep] are handed the digits
 * the player can see and the region layout, and nothing else, so a step they explain cannot lean on
 * the stored answer. The answer is read only in [teach]: to decide which digits are mistakes (sound,
 * because a shipped board has one answer), and for [FALLBACK], which says openly that it points at it.
 *
 * Candidates are derived from the visible board on every call: an empty square can take the digits
 * 1..its region's size that no region-mate holds and no touching square holds.
 *
 * Techniques, simplest first. Each ends in one digit placed:
 * 1. [LAST] — a region with one empty square.
 * 2. [ONLY_DIGIT] — a square whose region and touching squares leave one digit.
 * 3. [ONLY_PLACE] — a digit with one square left in its region.
 *
 * The generator keeps a board only while these finish it ([solvesByLogic]), so a board made here
 * never reaches [FALLBACK]; it exists for a board that did not come from the generator and is
 * measured by `RegionsTeachingTest`. Every loop walks squares and regions in index order, so a
 * board gets the same hint on every platform (CLAUDE.md, "Hash iteration order").
 *
 * Highlight indices: `0 until n*n` are squares, [pad] a digit key.
 */
internal object RegionsTeacher {

    const val LAST = "last-square"
    const val ONLY_DIGIT = "only-digit"
    const val ONLY_PLACE = "only-place"
    const val FALLBACK = "fallback"
    const val MISTAKE = "mistake"

    val TECHNIQUES = listOf(LAST, ONLY_DIGIT, ONLY_PLACE, FALLBACK)

    const val MAX_EXPLANATION = 200

    /** Highlight index of digit [d]'s key under the board. */
    fun pad(d: Int): Int = 1000 + d

    // ---- what the player can see ------------------------------------------------------------------


    /** For each empty square the digits still possible there; 0 for a filled square. */
    private fun candidates(layout: RegionsLayout, cells: List<Int>): IntArray {
        val base = IntArray(layout.n * layout.n)
        for (i in base.indices) {
            if (cells[i] != 0) continue
            var m = RegionsLogic.fullMask(layout.sizeOf[i])
            for (j in layout.peers[i]) if (cells[j] != 0) m = m and bit(cells[j]).inv()
            base[i] = m
        }
        return base
    }

    // ---- finding a step ---------------------------------------------------------------------------

    private class Hit(val technique: String, val cell: Int, val digit: Int, val region: Int)

    private fun findStep(layout: RegionsLayout, cells: List<Int>): Hit? {
        val base = candidates(layout, cells)
        if (cells.indices.any { cells[it] == 0 && base[it] == 0 }) return null
        // 1. The last square of a region.
        for ((r, members) in layout.members.withIndex()) {
            val empty = members.filter { cells[it] == 0 }
            if (empty.size != 1) continue
            val missing = RegionsLogic.fullMask(members.size) and members.fold(0) { m, i -> if (cells[i] != 0) m or bit(cells[i]) else m }.inv()
            if (missing.countOneBits() == 1) return Hit(LAST, empty[0], missing.countTrailingZeroBits(), r)
        }
        // 2. A square whose region and neighbours leave one digit.
        for (i in cells.indices) {
            if (cells[i] == 0 && base[i].countOneBits() == 1) return Hit(ONLY_DIGIT, i, base[i].countTrailingZeroBits(), layout.region[i])
        }
        // 3. A digit with one place left in its region.
        for ((r, members) in layout.members.withIndex()) {
            for (d in 1..members.size) {
                if (members.any { cells[it] == d }) continue
                val places = members.filter { cells[it] == 0 && has(base[it], d) }
                if (places.size == 1) return Hit(ONLY_PLACE, places[0], d, r)
            }
        }
        return null
    }

    /** Whether the three techniques alone finish this board: what the generator asks of every board it ships. */
    fun solvesByLogic(layout: RegionsLayout, given: List<Int>): Boolean {
        val cells = given.toMutableList()
        while (cells.any { it == 0 }) {
            val hit = findStep(layout, cells) ?: return false
            cells[hit.cell] = hit.digit
        }
        return RegionsLogic.isSolved(layout, cells)
    }

    // ---- the whole hint: mistakes, then reasoning, then the honest fallback ------------------------

    fun teach(s: RegionsState): PlacementStep? {
        if (s.solved) return null
        mistake(s)?.let { return it }
        return deduce(RegionsLayout(s.size, s.region), s.cells) ?: fallback(s)
    }

    private fun fallback(s: RegionsState): PlacementStep? {
        val i = s.cells.indices.firstOrNull { s.cells[it] == 0 } ?: return null
        val d = s.solution[i]
        return PlacementStep(
            FALLBACK, i, d, setOf(i), emptySet(),
            "Try this square.",
            "No short reason found: the stored answer has a $d here.",
        )
    }

    private fun mistake(s: RegionsState): PlacementStep? {
        val layout = RegionsLayout(s.size, s.region)
        val wrong = s.cells.indices.firstOrNull { s.cells[it] != 0 && !s.givens[it] && s.cells[it] != s.solution[it] }
            ?: return null
        val d = s.cells[wrong]
        val clear = "Select it and tap $d again to clear it."
        fun step(explanation: String, cited: Set<Int>) =
            PlacementStep(MISTAKE, wrong, 0, setOf(wrong), cited, "Check this $d.", explanation)

        val size = layout.sizeOf[wrong]
        if (d > size) return step("This region has $size squares, so it only holds 1 to $size. $clear", layout.members[layout.region[wrong]].toSet() - wrong)
        layout.members[layout.region[wrong]].firstOrNull { it != wrong && s.cells[it] == d }
            ?.let { return step("This $d repeats the $d in its region. $clear", setOf(it)) }
        layout.around[wrong].firstOrNull { s.cells[it] == d }
            ?.let { return step("This $d touches another $d, and equal digits may not touch, even at a corner. $clear", setOf(it)) }
        return step("No valid way to finish the board keeps this $d. $clear", emptySet())
    }

    // ---- explaining ---------------------------------------------------------------------------------

    private fun andList(ds: List<Int>) = if (ds.size < 2) ds.joinToString("") else ds.dropLast(1).joinToString(", ") + " and " + ds.last()

    fun deduce(layout: RegionsLayout, cells: List<Int>): PlacementStep? {
        val hit = findStep(layout, cells) ?: return null
        val x = hit.cell
        val d = hit.digit
        val members = layout.members[hit.region]
        return when (hit.technique) {
            LAST -> PlacementStep(
                LAST, x, d, members.toSet(), members.filter { cells[it] != 0 }.toSet(),
                "Look at this region.",
                if (members.size == 1) "A region of one square holds just a 1."
                else "This region has every digit from 1 to ${members.size} except $d, so its last empty square is a $d.",
            )
            ONLY_DIGIT -> {
                val inRegion = members.filter { it != x && cells[it] != 0 }
                val regionDigits = inRegion.map { cells[it] }.sorted()
                val touching = layout.around[x].filter { cells[it] != 0 && cells[it] <= members.size && cells[it] !in regionDigits }
                val touchDigits = touching.map { cells[it] }.sorted().distinct()
                val why = when {
                    regionDigits.isNotEmpty() && touchDigits.isNotEmpty() ->
                        "Its region already has ${andList(regionDigits)}, and the squares touching it have ${andList(touchDigits)}, so only $d is left."
                    regionDigits.isNotEmpty() -> "Its region already has ${andList(regionDigits)}, so only $d is left."
                    touchDigits.isEmpty() -> "Its region is one square, so it holds just a 1."
                    else -> "The squares touching it have ${andList(touchDigits)}, so only $d is left."
                }
                PlacementStep(
                    ONLY_DIGIT, x, d, setOf(x), (inRegion + touching.sorted().distinctBy { cells[it] }).toSet(),
                    "Which digits can this square still take?", why,
                )
            }
            else -> {
                val base = candidates(layout, cells)
                val blockers = members.filter { it != x && cells[it] == 0 && !has(base[it], d) }
                    .mapNotNull { o -> layout.around[o].firstOrNull { cells[it] == d } }.toSet()
                PlacementStep(
                    ONLY_PLACE, x, d, members.toSet(), blockers,
                    "Where can the $d go in this region?",
                    "This region still needs a $d, and every other empty square in it has a $d touching it. This square is the only place left.",
                )
            }
        }
    }
}
