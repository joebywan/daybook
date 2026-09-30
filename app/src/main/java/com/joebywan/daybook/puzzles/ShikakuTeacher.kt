package com.joebywan.daybook.puzzles

/**
 * Shikaku, reasoned the way a person reasons it, one named step at a time.
 *
 * As in [KingsTeacher] and [AtomsTeacher], the split is the point. [deduce] is handed the board as
 * the player sees it — the grid, the numbers and the rectangles already drawn — and has no
 * parameter through which the stored answer could reach it. The answer is read only in [teach]: to
 * decide which of the player's rectangles are mistakes, and for [FALLBACK], which says openly that
 * it is pointing at the answer.
 *
 * The board has two gestures and no marks: drag corner to corner to draw a rectangle, tap one to
 * remove it. So every step ends in one rectangle to draw (or, for a mistake, one to remove). Facts a
 * player can only hold in their head — "the 4 must cover these squares", "this rectangle is out" —
 * are never a step of their own; they are the reason another rectangle becomes the only one left,
 * and the step is named after that reason.
 *
 * Techniques, simplest first:
 * 1. [ONLY_FITS] — a number has a single rectangle that fits, given the edges, the other numbers and
 *    the rectangles already drawn.
 * 2. [ONLY_REACHES] — an empty square only one number's rectangles can cover, and only one of those
 *    rectangles covers it.
 * 3. [COMMON_CELLS] — squares every rectangle of one number covers, which no other number can use;
 *    taking them away leaves another number a single rectangle.
 * 4. [WHAT_IF] — a number with two or three rectangles, where every one but one leaves another
 *    number no room, or a square nothing can cover, straight away.
 *
 * Cell indices, as [com.joebywan.daybook.core.Deduction] and the board's highlight use them, are
 * `row * width + col`. Every loop walks cells and clues in reading order and nothing iterates a
 * hash, so the same board gets the same hint on every platform.
 */
internal object ShikakuTeacher {

    const val ONLY_FITS = "only-fits"
    const val ONLY_REACHES = "only-reaches"
    const val COMMON_CELLS = "common-cells"
    const val WHAT_IF = "what-if"
    const val FALLBACK = "fallback"
    const val MISTAKE = "mistake"

    /** Every technique in the order [deduce] tries them, for reports. */
    val TECHNIQUES = listOf(ONLY_FITS, ONLY_REACHES, COMMON_CELLS, WHAT_IF, FALLBACK)

    /**
     * The most rectangles a [WHAT_IF] may rule out for one number. Each has to be named as a reason
     * in one sentence; past two the sentence stops being something to check against the board.
     */
    const val MAX_WHAT_IF_OPTIONS = 3

    /** One step: a rectangle to draw, or a mistaken one to remove, and why. */
    class Step(
        val technique: String,
        val place: Block? = null,
        val remove: Block? = null,
        val focus: Set<Int>,
        val cited: Set<Int>,
        val targets: Set<Int>,
        val nudge: String,
        val explanation: String,
    )

    // ---- the board as the player sees it --------------------------------------------------------

    private class Sight(val w: Int, val h: Int, val clues: List<Int?>, blocks: List<Block>) {
        val taken = BooleanArray(w * h).also { t -> blocks.forEach { b -> cellsOf(b).forEach { t[it] = true } } }
        val clueCells: List<Int> = clues.indices.filter { clues[it] != null }

        /** Numbers not yet inside a drawn rectangle, in reading order. */
        val open: List<Int> = clueCells.filter { !taken[it] }

        private val memo = arrayOfNulls<List<Block>>(w * h)

        fun cellsOf(b: Block): List<Int> = buildList {
            for (r in b.r0..b.r1) for (c in b.c0..b.c1) add(r * w + c)
        }

        fun cluesIn(b: Block): Int = cellsOf(b).count { clues[it] != null }

        /** Every rectangle the right size that holds clue [cell], at any position, on the grid or off. */
        fun shapes(cell: Int): List<Block> {
            val area = clues[cell] ?: return emptyList()
            val cr = cell / w
            val cc = cell % w
            return buildList {
                for (height in 1..area) {
                    if (area % height != 0) continue
                    val width = area / height
                    for (r0 in cr - height + 1..cr) for (c0 in cc - width + 1..cc) {
                        add(Block(r0, c0, r0 + height - 1, c0 + width - 1))
                    }
                }
            }
        }

        fun onGrid(b: Block) = b.r0 >= 0 && b.c0 >= 0 && b.r1 < h && b.c1 < w

        fun free(b: Block) = cellsOf(b).none { taken[it] }

        /** The rectangles clue [cell] could still take. */
        fun options(cell: Int): List<Block> = memo[cell] ?: shapes(cell)
            .filter { onGrid(it) && cluesIn(it) == 1 && free(it) }
            .also { memo[cell] = it }
    }

    /** Why a rectangle cannot be drawn, if something already on the board rules it out. */
    private sealed interface Dead
    private class NoRoom(val clue: Int) : Dead
    private class Uncovered(val cell: Int) : Dead

    /**
     * What drawing [x] for clue [owner] would leave impossible straight away: another number with no
     * rectangle left, or an empty square no number's rectangle could still cover.
     */
    private fun deadEnd(s: Sight, owner: Int, x: Block): Dead? {
        val inX = BooleanArray(s.w * s.h).also { m -> s.cellsOf(x).forEach { m[it] = true } }
        val others = s.open.filter { it != owner }
        val left = others.map { b -> s.options(b).filter { o -> s.cellsOf(o).none { inX[it] } } }
        others.forEachIndexed { i, b -> if (left[i].isEmpty()) return NoRoom(b) }
        val reach = BooleanArray(s.w * s.h)
        left.forEach { opts -> opts.forEach { o -> s.cellsOf(o).forEach { reach[it] = true } } }
        for (cell in 0 until s.w * s.h) {
            if (!s.taken[cell] && !inX[cell] && !reach[cell]) return Uncovered(cell)
        }
        return null
    }

    /** The same, for the board as it stands: a number with no room, or a square nothing reaches. */
    private fun stuck(s: Sight): Dead? {
        s.open.firstOrNull { s.options(it).isEmpty() }?.let { return NoRoom(it) }
        val reach = BooleanArray(s.w * s.h)
        s.open.forEach { c -> s.options(c).forEach { o -> s.cellsOf(o).forEach { reach[it] = true } } }
        return (0 until s.w * s.h).firstOrNull { !s.taken[it] && !reach[it] }?.let { Uncovered(it) }
    }

    // ---- the whole hint: mistakes, then reasoning, then the honest fallback ----------------------

    /** The next thing to show this player, or null on a solved board. */
    fun teach(s: ShikakuState): Step? {
        if (s.solved) return null
        mistake(s)?.let { return it }
        return deduce(s.width, s.height, s.clues, s.blocks) ?: fallback(s)
    }

    /** A drawn rectangle the answer does not have, the first in reading order. */
    private fun mistake(s: ShikakuState): Step? {
        val wrong = s.blocks.filter { it !in s.solution }.minWithOrNull(compareBy({ it.r0 }, { it.c0 })) ?: return null
        val right = s.blocks.filter { it in s.solution }
        val sight = Sight(s.width, s.height, s.clues, right + wrong)
        val cells = sight.cellsOf(wrong).toSet()
        val clue = cells.first { s.clues[it] != null }
        val n = s.clues[clue]!!
        fun step(explanation: String, cited: Set<Int>) = Step(
            technique = MISTAKE,
            remove = wrong,
            focus = cells,
            cited = cited,
            targets = cells,
            nudge = "Check this rectangle.",
            explanation = "$explanation Tap it to remove it.",
        )
        if (wrong.area != n) {
            return step("This rectangle has ${numberWord(wrong.area)} squares, but its number is $n.", setOf(clue))
        }
        return when (val dead = stuck(sight)) {
            is NoRoom -> step(
                "With this rectangle here, the marked ${s.clues[dead.clue]} has no room left for a rectangle of its own.",
                setOf(dead.clue),
            )
            is Uncovered -> step(
                "With this rectangle here, no number can reach the marked square, and every square needs one.",
                setOf(dead.cell),
            )
            null -> step("This rectangle isn't part of the answer, so look again once it's gone.", emptySet())
        }
    }

    /**
     * Nothing short enough to explain applies, so point at a rectangle from the answer, for the
     * number with the fewest ways left. Says plainly that it is doing this.
     */
    private fun fallback(s: ShikakuState): Step? {
        val sight = Sight(s.width, s.height, s.clues, s.blocks)
        val missing = s.solution.filter { it !in s.blocks }
        val block = missing.minByOrNull { b ->
            val clue = sight.cellsOf(b).first { s.clues[it] != null }
            sight.options(clue).size * 10_000 + clue
        } ?: return null
        val clue = sight.cellsOf(block).first { s.clues[it] != null }
        val n = s.clues[clue]!!
        return Step(
            technique = FALLBACK,
            place = block,
            focus = setOf(clue),
            cited = setOf(clue),
            targets = sight.cellsOf(block).toSet(),
            nudge = "This one takes a longer chain. Look at the glowing $n.",
            explanation = "This needs a longer chain than a hint can walk through, so here is the " +
                "$n's rectangle from the answer.",
        )
    }

    // ---- reasoning from what the player can see ---------------------------------------------------

    /**
     * The simplest step visible on this board, or null when none of the techniques applies.
     *
     * Takes no answer on purpose — see the class comment. [blocks] are taken as the player's; the
     * caller has already dealt with any that are mistakes.
     */
    fun deduce(w: Int, h: Int, clues: List<Int?>, blocks: List<Block>): Step? {
        val s = Sight(w, h, clues, blocks)
        if (stuck(s) != null) return null
        return onlyFits(s) ?: onlyReaches(s) ?: commonCells(s) ?: whatIf(s)
    }

    private fun onlyFits(s: Sight): Step? {
        val clue = s.open.firstOrNull { s.options(it).size == 1 } ?: return null
        val block = s.options(clue)[0]
        val n = s.clues[clue]!!
        var offGrid = false
        var drawn = false
        val numbers = mutableSetOf<Int>()
        for (b in s.shapes(clue)) {
            if (b == block) continue
            if (!s.onGrid(b)) { offGrid = true; continue }
            val others = s.cellsOf(b).filter { it != clue && s.clues[it] != null }
            if (others.isNotEmpty()) numbers += others.first() else if (!s.free(b)) drawn = true
        }
        val reasons = buildList {
            if (offGrid) add("run off the grid")
            if (numbers.isNotEmpty()) add("take in another number")
            if (drawn) add("overlap a rectangle already drawn")
        }
        val explanation = if (reasons.isEmpty()) {
            "This $n needs ${numberWord(n)} squares, and there is only one way to lay them out here."
        } else {
            "This $n needs ${numberWord(n)} squares. Any other rectangle would ${join(reasons, "or")}, " +
                "so only this one fits."
        }
        return Step(
            technique = ONLY_FITS,
            place = block,
            focus = setOf(clue),
            cited = setOf(clue) + numbers,
            targets = s.cellsOf(block).toSet(),
            nudge = "Look at the glowing $n.",
            explanation = explanation,
        )
    }

    private fun onlyReaches(s: Sight): Step? {
        for (cell in 0 until s.w * s.h) {
            if (s.taken[cell] || s.clues[cell] != null) continue
            val reaching = s.open.filter { c -> s.options(c).any { cell in s.cellsOf(it) } }
            if (reaching.size != 1) continue
            val clue = reaching[0]
            val covering = s.options(clue).filter { cell in s.cellsOf(it) }
            if (covering.size != 1) continue
            val n = s.clues[clue]!!
            return Step(
                technique = ONLY_REACHES,
                place = covering[0],
                focus = setOf(cell),
                cited = setOf(clue, cell),
                targets = s.cellsOf(covering[0]).toSet(),
                nudge = "Which number can reach the glowing square?",
                explanation = "Only the $n can reach the marked square, and every square belongs to " +
                    "some rectangle. Just one of the $n's rectangles covers it, so that is the one.",
            )
        }
        return null
    }

    private fun commonCells(s: Sight): Step? {
        for (a in s.open) {
            val opts = s.options(a)
            if (opts.size < 2) continue
            val common = s.cellsOf(opts[0]).filter { cell -> cell != a && opts.all { cell in s.cellsOf(it) } }
            if (common.isEmpty()) continue
            for (b in s.open) {
                if (b == a) continue
                val before = s.options(b)
                if (before.size < 2) continue
                val after = before.filter { o -> s.cellsOf(o).none { it in common } }
                if (after.size != 1) continue
                val na = s.clues[a]!!
                val nb = s.clues[b]!!
                val these = if (common.size == 1) "the marked square" else "the marked squares"
                // Two 6s on one board would read as one; the second is "the other".
                val other = if (na == nb) "the other $nb" else "the $nb"
                return Step(
                    technique = COMMON_CELLS,
                    place = after[0],
                    focus = setOf(a) + common,
                    cited = setOf(a, b) + common,
                    targets = s.cellsOf(after[0]).toSet(),
                    nudge = "Where must the glowing $na go?",
                    explanation = "Every rectangle this $na can make covers $these, so $other can't use " +
                        "${if (common.size == 1) "it" else "them"}. That leaves $other just one rectangle that fits.",
                )
            }
        }
        return null
    }

    private fun whatIf(s: Sight): Step? {
        for (size in 2..MAX_WHAT_IF_OPTIONS) {
            for (a in s.open) {
                val opts = s.options(a)
                if (opts.size != size) continue
                val deaths = opts.map { deadEnd(s, a, it) }
                if (deaths.count { it == null } != 1) continue
                val keep = opts[deaths.indexOf(null)]
                val n = s.clues[a]!!
                val witnesses = deaths.filterNotNull()
                val phrases = witnesses.map { d ->
                    when (d) {
                        is NoRoom -> "leave the ${s.clues[d.clue]} no room"
                        is Uncovered -> "leave a marked square no number can reach"
                    }
                }.distinct()
                val which = if (witnesses.size == 1) "its other rectangle" else "either of its other rectangles"
                val cited = witnesses.map { d ->
                    when (d) {
                        is NoRoom -> d.clue
                        is Uncovered -> d.cell
                    }
                }.toSet()
                return Step(
                    technique = WHAT_IF,
                    place = keep,
                    focus = setOf(a),
                    cited = cited + a,
                    targets = s.cellsOf(keep).toSet(),
                    nudge = "What if the glowing $n went another way?",
                    explanation = "Suppose the $n took $which. That would ${join(phrases, "or")}. " +
                        "So the $n takes this rectangle.",
                )
            }
        }
        return null
    }

    // ---- words -----------------------------------------------------------------------------------

    private fun join(words: List<String>, and: String = "and"): String = when (words.size) {
        0 -> ""
        1 -> words[0]
        else -> words.dropLast(1).joinToString(", ") + " $and " + words.last()
    }

    private fun numberWord(k: Int) =
        listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve")
            .getOrElse(k) { k.toString() }
}
