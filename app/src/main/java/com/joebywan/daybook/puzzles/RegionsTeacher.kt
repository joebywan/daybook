package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.PlacementStep
import com.joebywan.daybook.core.bit
import com.joebywan.daybook.core.has
import com.joebywan.daybook.core.listed

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
 * 4. [LOCKED] — a digit a region still needs whose every place touches one square outside it: that square cannot hold
 *    it. Then that square is left one digit, or its own region's digit one place. One elimination, then a placement.
 *
 * The generator keeps a board only while these (up to its tier's rung) finish it ([solvesByLogic]), so a board made here
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
    const val LOCKED = "locked-candidate"
    const val FALLBACK = "fallback"
    const val MISTAKE = "mistake"

    val TECHNIQUES = listOf(LAST, ONLY_DIGIT, ONLY_PLACE, LOCKED, FALLBACK)

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

    /**
     * A placement. For [LOCKED]: [struck] the square that loses [lockDigit], [places] where the [region]'s [lockDigit]
     * can still go, and [byPlace] whether the placement is the only place left in the struck square's region (else the
     * struck square is left one digit, and [cell] is [struck]).
     */
    private class Hit(
        val technique: String, val cell: Int, val digit: Int, val region: Int,
        val struck: Int = -1, val lockDigit: Int = 0, val places: List<Int> = emptyList(), val byPlace: Boolean = false,
    )

    /** The rung each technique sits on, in [TECHNIQUES] order: the depth a walk reports. */
    fun rung(technique: String): Int = TECHNIQUES.indexOf(technique)

    /** The deepest rung any board may use. */
    val DEEPEST = rung(LOCKED)

    /** The one empty square of [members] whose mask in [m] has [d]; -1 when there are none or several, or one holds [d]. */
    private fun lonePlace(members: List<Int>, cells: List<Int>, m: IntArray, d: Int): Int {
        var at = -1
        for (i in members) {
            if (cells[i] == d) return -1
            if (cells[i] == 0 && has(m[i], d)) { if (at >= 0) return -1; at = i }
        }
        return at
    }

    private fun findStep(layout: RegionsLayout, cells: List<Int>, deepest: Int = DEEPEST): Hit? {
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
        if (deepest < rung(LOCKED)) return null
        // 4. A region's digit whose places all touch one square outside it: that square loses the digit.
        for ((r, members) in layout.members.withIndex()) {
            for (d in 1..members.size) {
                if (members.any { cells[it] == d }) continue
                val places = members.filter { cells[it] == 0 && has(base[it], d) }
                if (places.size < 2) continue
                for (x in layout.around[places[0]]) {
                    if (cells[x] != 0 || layout.region[x] == r || !has(base[x], d)) continue
                    if (places.any { p -> x !in layout.around[p] }) continue
                    val left = base[x] and bit(d).inv()
                    if (single(left)) return Hit(LOCKED, x, left.countTrailingZeroBits(), r, x, d, places)
                    // Only x changed, so only its own region's d can have come down to one place.
                    val cut = base.copyOf().also { it[x] = left }
                    val at = lonePlace(layout.members[layout.region[x]], cells, cut, d)
                    if (at >= 0) return Hit(LOCKED, at, d, r, x, d, places, byPlace = true)
                }
            }
        }
        return null
    }

    /** The deepest rung a walk of the ladder (up to [deepest]) needs to finish this board, the hints' way; null when it cannot. */
    fun depth(layout: RegionsLayout, given: List<Int>, deepest: Int = DEEPEST): Int? {
        val cells = given.toMutableList()
        var most = -1
        while (cells.any { it == 0 }) {
            val hit = findStep(layout, cells, deepest) ?: return null
            cells[hit.cell] = hit.digit
            most = maxOf(most, rung(hit.technique))
        }
        return if (RegionsLogic.isSolved(layout, cells)) most else null
    }

    /** Whether the ladder up to [deepest] finishes this board: what the generator asks of every board it ships. */
    fun solvesByLogic(layout: RegionsLayout, given: List<Int>, deepest: Int = DEEPEST): Boolean =
        lockedSteps(layout, given, deepest) != null

    /**
     * How many [LOCKED] steps the hints' walk needs, found faster: null when the ladder up to [deepest] cannot finish.
     * The three single rungs come to "a square left one digit, or a digit left one place in its region", and placing a
     * right digit never takes such a step away, so every order of them stops at the same board: all of a pass are placed
     * at once, and the walk meets the same stuck boards, where [findStep] takes the same locked step the hints do. That
     * holds while every digit placed is right, as on a board with one answer made from its own solution.
     */
    fun lockedSteps(layout: RegionsLayout, given: List<Int>, deepest: Int = DEEPEST): Int? {
        val cells = given.toMutableList()
        var locked = 0
        while (cells.any { it == 0 }) {
            val base = candidates(layout, cells)
            if (cells.indices.any { cells[it] == 0 && base[it] == 0 }) return null
            var placed = false
            for (i in cells.indices) if (cells[i] == 0 && single(base[i])) { cells[i] = base[i].countTrailingZeroBits(); placed = true }
            for (members in layout.members) for (d in 1..members.size) {
                val at = lonePlace(members, cells, base, d)
                if (at >= 0) { cells[at] = d; placed = true }
            }
            if (placed) continue
            val hit = findStep(layout, cells, deepest) ?: return null
            cells[hit.cell] = hit.digit
            locked++
        }
        return if (RegionsLogic.isSolved(layout, cells)) locked else null
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
                        "Its region already has ${listed(regionDigits)}, and the squares touching it have ${listed(touchDigits)}, so only $d is left."
                    regionDigits.isNotEmpty() -> "Its region already has ${listed(regionDigits)}, so only $d is left."
                    touchDigits.isEmpty() -> "Its region is one square, so it holds just a 1."
                    else -> "The squares touching it have ${listed(touchDigits)}, so only $d is left."
                }
                PlacementStep(
                    ONLY_DIGIT, x, d, setOf(x), (inRegion + touching.sorted().distinctBy { cells[it] }).toSet(),
                    "Which digits can this square still take?", why,
                )
            }
            LOCKED -> {
                val n = layout.n
                fun at(i: Int) = "row ${i / n + 1}, column ${i % n + 1}"
                val why = "This region's ${hit.lockDigit} must go in one of the marked squares. They all touch the square at " +
                    "${at(hit.struck)}, so it is not a ${hit.lockDigit}."
                val then = if (hit.byPlace) "That leaves ${at(x)} as its region's only place for a $d."
                else "That leaves it only $d."
                Step(
                    LOCKED, x, d, members.toSet() + hit.struck, hit.places.toSet() + hit.struck + x,
                    "Where can the ${hit.lockDigit} go in this region?", "$why $then",
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
