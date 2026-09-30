package com.joebywan.daybook.puzzles

/**
 * Kings, reasoned the way a person reasons it, one named step at a time.
 *
 * The split in this file is the point of it. [deduce] is handed the board as the player sees it —
 * the regions, their kings and their crosses — and has no parameter through which the stored answer
 * could reach it, so a step it explains cannot lean on a fact only the answer knows. That is a
 * guarantee of the signature rather than of anyone's care. The answer is read in exactly two places,
 * both in [teach]: to decide which of the player's moves are mistakes, and for [FALLBACK], which says
 * openly that it is pointing at the answer.
 *
 * Techniques are tried simplest first, because the hint should be the step a person would have
 * found next, not the cleverest one available:
 * 1. [LAST_SQUARE] — a row, column or colour with one open square left.
 * 2. [LOCKED] — a colour whose open squares share one line, or a line whose open squares share one
 *    colour; the rest of that line (or colour) is out.
 * 3. [WOULD_EMPTY] — a king on these squares would rule out every open square of some row, column or
 *    colour.
 * 4. [CONFINED] — N colours that fit only in N lines, or N lines that only reach N colours. [LOCKED]
 *    is the N = 1 case, kept separate because it is the one players learn first.
 * 5. [WHAT_IF] — a king here would force a short chain of last squares that ends with somewhere
 *    empty.
 *
 * Orders never come from a hash: every loop walks cells, houses or subsets in index order, so the
 * same board gets the same hint on every platform (CLAUDE.md, "Hash iteration order").
 */
internal object KingsTeacher {

    const val LAST_SQUARE = "last-square"
    const val LOCKED = "locked-to-a-line"
    const val WOULD_EMPTY = "would-empty"
    const val CONFINED = "confined"
    const val WHAT_IF = "what-if"
    const val FALLBACK = "fallback"
    const val MISTAKE = "mistake"

    /** Every technique in the order [deduce] tries them, for reports. */
    val TECHNIQUES = listOf(LAST_SQUARE, LOCKED, WOULD_EMPTY, CONFINED, WHAT_IF, FALLBACK)

    /**
     * How many forced last squares a [WHAT_IF] may walk through before it counts as a "longer
     * chain". One sentence has to carry the whole argument, and past two it stops being a sentence
     * anyone can hold in their head while looking at the board.
     */
    const val MAX_CHAIN = 2

    /**
     * One step: what to crown, cross or clear, where to look, and why.
     *
     * [clears] is only ever a mistake being taken back.
     */
    class Step(
        val technique: String,
        val kings: List<Int> = emptyList(),
        val crosses: List<Int> = emptyList(),
        val clears: List<Int> = emptyList(),
        val focus: Set<Int>,
        val cited: Set<Int>,
        val nudge: String,
        val explanation: String,
    ) {
        val targets: Set<Int> get() = (kings + crosses + clears).toSet()
    }

    private enum class Kind { REGION, ROW, COLUMN }

    /** A row, a column or a colour region: somewhere that needs exactly one king. */
    private class House(val kind: Kind, val id: Int, val cells: List<Int>, colour: String = "") {
        val name: String = when (kind) {
            Kind.REGION -> colour
            Kind.ROW -> "row ${id + 1}"
            Kind.COLUMN -> "column ${id + 1}"
        }
    }

    /**
     * The board as the player sees it: kings, crosses, and what the kings rule out. A square is
     * open when none of those three closes it.
     */
    private class Sight(
        val n: Int,
        val region: List<Int>,
        val kings: List<Int>,
        val blocked: BooleanArray,
        /** Each region's colour, by name, as the board paints it. */
        val names: List<String> = Kings.regionNames(n, region),
    ) {
        private val ruledOut = BooleanArray(n * n).also { out ->
            for (k in kings) for (c in attacks(n, region, k)) out[c] = true
        }

        /** Regions first: "purple" is the first thing a player reads a Kings board by. */
        val houses: List<House> = buildList {
            region.distinct().sorted().forEach { id ->
                add(House(Kind.REGION, id, region.indices.filter { region[it] == id }, names[id]))
            }
            for (r in 0 until n) add(House(Kind.ROW, r, List(n) { r * n + it }))
            for (c in 0 until n) add(House(Kind.COLUMN, c, List(n) { it * n + c }))
        }

        fun isOpen(i: Int) = !blocked[i] && !ruledOut[i]

        fun openIn(h: House): List<Int> = h.cells.filter(::isOpen)

        fun satisfied(h: House) = h.cells.any { it in kings }

        fun withKing(cell: Int) = Sight(n, region, kings + cell, blocked, names)

        fun unsatisfied(kind: Kind) = houses.filter { it.kind == kind && !satisfied(it) }
    }

    /** Every square a king on [cell] rules out, itself included. Mirrors [KingsState.eliminatedBy]. */
    private fun attacks(n: Int, region: List<Int>, cell: Int): List<Int> {
        val r = cell / n
        val c = cell % n
        return region.indices.filter { i ->
            val ri = i / n
            val ci = i % n
            ri == r || ci == c || region[i] == region[cell] ||
                (kotlin.math.abs(ri - r) <= 1 && kotlin.math.abs(ci - c) <= 1)
        }
    }

    // ---- the whole hint: mistakes, then reasoning, then the honest fallback ----------------------

    /**
     * The next thing to show this player, or null on a solved board.
     *
     * Mistakes come first: a step reasoned from a board with a wrong king on it could be reasoning
     * from a falsehood, and the player learns more from "this king leaves purple no room" than
     * from being walked further down a board that cannot be finished.
     */
    fun teach(s: KingsState): Step? {
        if (s.solved) return null
        mistake(s)?.let { return it }
        return deduce(s.size, s.region, s.marks) ?: fallback(s)
    }

    /** A king the answer does not have, or a cross on a square the answer crowns. */
    private fun mistake(s: KingsState): Step? {
        val n = s.size
        val kings = s.marks.indices.filter { s.marks[it] == Mark.KING }
        val correct = kings.filter { it in s.solution }
        // Only the player's crosses that agree with the answer count as seen: reasoning from a wrong
        // one would be reasoning from a falsehood too.
        val blocked = BooleanArray(n * n) { s.marks[it] == Mark.BLOCKED && it !in s.solution }

        kings.firstOrNull { it !in s.solution }?.let { wrong ->
            return Step(
                technique = MISTAKE,
                clears = listOf(wrong),
                focus = setOf(wrong),
                cited = emptySet(),
                nudge = "Check this king.",
                explanation = "",
            ).let { base -> explainWrongKing(n, s.region, kings, correct, blocked, wrong, base) }
        }

        s.marks.indices.firstOrNull { s.marks[it] == Mark.BLOCKED && it in s.solution }?.let { cross ->
            val sight = Sight(n, s.region, correct, blocked)
            val last = sight.houses.firstOrNull {
                !sight.satisfied(it) && sight.openIn(it) == listOf(cross)
            }
            return Step(
                technique = MISTAKE,
                clears = listOf(cross),
                focus = setOf(cross),
                cited = last?.cells?.toSet().orEmpty(),
                nudge = "Check this cross.",
                explanation = if (last != null) {
                    "This is the only square ${last.name} has left, so it can't be crossed out. " +
                        "Tap it to clear the cross."
                } else {
                    "This cross doesn't belong. Tap it to clear it."
                },
            )
        }
        return null
    }

    private fun explainWrongKing(
        n: Int,
        region: List<Int>,
        kings: List<Int>,
        correct: List<Int>,
        blocked: BooleanArray,
        wrong: Int,
        base: Step,
    ): Step {
        fun step(explanation: String, cited: Set<Int>) = Step(
            technique = base.technique, clears = base.clears, focus = base.focus,
            cited = cited, nudge = base.nudge, explanation = explanation,
        )
        val wr = wrong / n
        val wc = wrong % n
        // A king that breaks a rule outright says so; the red crowns already hint at it.
        for (other in kings) {
            if (other == wrong) continue
            val why = when {
                other / n == wr -> "shares a row with another king"
                other % n == wc -> "shares a column with another king"
                region[other] == region[wrong] -> "is the second king in ${Kings.regionNames(n, region)[region[wrong]]}"
                kotlin.math.abs(other / n - wr) <= 1 && kotlin.math.abs(other % n - wc) <= 1 ->
                    "touches another king"
                else -> null
            } ?: continue
            return step("This king $why. Double-tap it to take it back.", setOf(other))
        }
        val chain = chainToEmpty(Sight(n, region, correct + wrong, blocked))
        if (chain != null) {
            val cited = chain.cited()
            return step(
                if (chain.forced.isEmpty()) {
                    "This king leaves ${chain.emptied.name} with no room for a king. Double-tap it to take it back."
                } else {
                    "With this king, ${chain.forcedPhrase()}, and that leaves ${chain.emptied.name} " +
                        "with no room. Double-tap it to take it back."
                },
                cited,
            )
        }
        return step(
            "This king isn't part of the answer. Double-tap it to take it back, and look again.",
            emptySet(),
        )
    }

    /**
     * Nothing short enough to explain applies, so point at a square the answer crowns — in the
     * colour with the fewest open squares, which is where the player was most likely looking.
     * Says plainly that it is doing this.
     */
    private fun fallback(s: KingsState): Step? {
        val n = s.size
        val kings = s.marks.indices.filter { s.marks[it] == Mark.KING }
        val sight = Sight(n, s.region, kings, BooleanArray(n * n) { s.marks[it] == Mark.BLOCKED })
        val target = s.solution.sorted()
            .filter { s.marks[it] != Mark.KING }
            .minByOrNull { cell -> sight.openIn(sight.houses.first { it.kind == Kind.REGION && it.id == s.region[cell] }).size }
            ?: return null
        val home = sight.houses.first { it.kind == Kind.REGION && it.id == s.region[target] }
        return Step(
            technique = FALLBACK,
            kings = listOf(target),
            focus = home.cells.toSet(),
            cited = emptySet(),
            nudge = "This one takes a longer chain. Look at ${home.name}.",
            explanation = "This one needs a longer chain than a hint can walk through, " +
                "so here's a square that holds a king.",
        )
    }

    // ---- reasoning from what the player can see ---------------------------------------------------

    /**
     * The simplest step visible on this board, or null when none of the techniques applies.
     *
     * Takes no answer on purpose — see the class comment. [marks] is taken as the player's; the
     * caller has already dealt with any that are mistakes.
     */
    fun deduce(n: Int, region: List<Int>, marks: List<Mark>): Step? {
        val sight = Sight(
            n, region,
            marks.indices.filter { marks[it] == Mark.KING },
            BooleanArray(n * n) { marks[it] == Mark.BLOCKED },
        )
        return lastSquare(sight)
            ?: confinement(sight, 1)
            ?: wouldEmpty(sight)
            ?: (2 until n).firstNotNullOfOrNull { k -> confinement(sight, k) }
            ?: whatIf(sight)
    }

    private fun lastSquare(s: Sight): Step? {
        for (h in s.houses) {
            if (s.satisfied(h)) continue
            val open = s.openIn(h)
            if (open.size != 1) continue
            return Step(
                technique = LAST_SQUARE,
                kings = open,
                focus = h.cells.toSet(),
                cited = h.cells.toSet(),
                nudge = "Look at ${h.name}.",
                explanation = "${h.name.cap()} has only one square left that could hold a king, " +
                    "so its king goes there.",
            )
        }
        return null
    }

    /**
     * [k] colours whose open squares fit in [k] lines, or [k] lines whose open squares fit in [k]
     * colours. Either way those lines and colours are spoken for by each other, so anything else in
     * them is out. [k] = 1 is [LOCKED]; the rest is [CONFINED].
     */
    private fun confinement(s: Sight, k: Int): Step? {
        val technique = if (k == 1) LOCKED else CONFINED
        val regions = s.unsatisfied(Kind.REGION)
        val rows = s.unsatisfied(Kind.ROW)
        val cols = s.unsatisfied(Kind.COLUMN)

        // Colours into lines.
        for (lineKind in listOf(Kind.ROW, Kind.COLUMN)) {
            if (k >= regions.size) break
            for (group in combinations(regions, k)) {
                val open = group.flatMap(s::openIn)
                val lines = open.map { lineOf(s.n, lineKind, it) }.distinct().sorted()
                if (lines.size != k) continue
                val ids = group.map { it.id }.toSet()
                val crosses = s.houses
                    .filter { it.kind == lineKind && it.id in lines }
                    .flatMap(s::openIn)
                    .filter { s.region[it] !in ids }
                    .distinct().sorted()
                if (crosses.isEmpty()) continue
                val lineHouses = s.houses.filter { it.kind == lineKind && it.id in lines }
                return Step(
                    technique = technique,
                    crosses = crosses,
                    focus = group.flatMap { it.cells }.toSet(),
                    cited = open.toSet(),
                    nudge = if (k == 1) "Look at ${group[0].name}." else "Look at ${join(group.map { it.name })} together.",
                    explanation = if (k == 1) {
                        val line = lineHouses[0].name
                        "Every open square of ${group[0].name} is in $line, so $line's king must be " +
                            "${group[0].name}. The rest of $line can be crossed out."
                    } else {
                        val names = join(group.map { it.name })
                        val where = linesPhrase(lineKind, lines)
                        "$names only fit in $where, so those ${numberWord(k)} kings are theirs. " +
                            "Nothing else in $where can hold a king."
                    },
                )
            }
        }

        // Lines into colours.
        for ((lineKind, lineHouses) in listOf(Kind.ROW to rows, Kind.COLUMN to cols)) {
            if (k >= lineHouses.size) continue
            for (group in combinations(lineHouses, k)) {
                val open = group.flatMap(s::openIn)
                val ids = open.map { s.region[it] }.distinct().sorted()
                if (ids.size != k) continue
                val lines = group.map { it.id }.toSet()
                val crosses = s.houses
                    .filter { it.kind == Kind.REGION && it.id in ids }
                    .flatMap(s::openIn)
                    .filter { lineOf(s.n, lineKind, it) !in lines }
                    .distinct().sorted()
                if (crosses.isEmpty()) continue
                val colours = ids.map { s.names[it] }
                return Step(
                    technique = technique,
                    crosses = crosses,
                    focus = group.flatMap { it.cells }.toSet(),
                    cited = open.toSet(),
                    nudge = if (k == 1) "Look at ${group[0].name}." else "Look at ${linesPhrase(lineKind, lines.sorted())} together.",
                    explanation = if (k == 1) {
                        val line = group[0].name
                        "Every open square in $line is ${colours[0]}, so ${colours[0]}'s king must be in " +
                            "$line. The rest of ${colours[0]} can be crossed out."
                    } else {
                        val where = linesPhrase(lineKind, lines.sorted())
                        "${where.cap()} only reach ${join(colours)}, so those ${numberWord(k)} colours' kings " +
                            "are in them. The rest of ${join(colours)} can be crossed out."
                    },
                )
            }
        }
        return null
    }

    /**
     * Squares where a king would rule out every open square of some row, column or colour. The
     * house with the fewest open squares wins, because that is the argument easiest to see.
     */
    private fun wouldEmpty(s: Sight): Step? {
        var best: Pair<House, List<Int>>? = null
        var bestSize = Int.MAX_VALUE
        for (h in s.houses) {
            if (s.satisfied(h)) continue
            val open = s.openIn(h)
            if (open.size >= bestSize) continue
            val attackers = s.region.indices.filter { x ->
                s.isOpen(x) && x !in h.cells && attacks(s.n, s.region, x).containsAll(open)
            }
            if (attackers.isEmpty()) continue
            best = h to attackers
            bestSize = open.size
        }
        val (house, attackers) = best ?: return null
        val open = s.openIn(house)
        val squares = if (attackers.size == 1) "a king here" else "a king on any glowing square"
        return Step(
            technique = WOULD_EMPTY,
            crosses = attackers,
            focus = house.cells.toSet(),
            cited = open.toSet(),
            nudge = "Look at ${house.name}.",
            explanation = "${squares.cap()} would rule out every open square of ${house.name}, " +
                "leaving it nowhere for a king. Cross ${if (attackers.size == 1) "it" else "them"} out.",
        )
    }

    /** A hypothetical king, followed through at most [MAX_CHAIN] forced last squares to a dead end. */
    private class Chain(val forced: List<Pair<House, Int>>, val emptied: House) {
        fun cited(): Set<Int> = (forced.map { it.second } + emptied.cells).toSet()

        fun forcedPhrase(): String {
            val names = forced.map { it.first.name }
            return if (names.size == 1) {
                "${names[0]} would be down to one square"
            } else {
                "${names.joinToString(" and then ")} would each be down to one square"
            }
        }
    }

    private fun chainToEmpty(start: Sight): Chain? {
        var cur = start
        val forced = mutableListOf<Pair<House, Int>>()
        while (true) {
            cur.houses.firstOrNull { !cur.satisfied(it) && cur.openIn(it).isEmpty() }
                ?.let { return Chain(forced.toList(), it) }
            if (forced.size == MAX_CHAIN) return null
            val single = cur.houses.firstOrNull { !cur.satisfied(it) && cur.openIn(it).size == 1 }
                ?: return null
            val y = cur.openIn(single)[0]
            forced += single to y
            cur = cur.withKing(y)
        }
    }

    private fun whatIf(s: Sight): Step? {
        var best: Pair<Int, Chain>? = null
        for (x in s.region.indices) {
            if (!s.isOpen(x)) continue
            val chain = chainToEmpty(s.withKing(x)) ?: continue
            if (best == null || chain.forced.size < best.second.forced.size) best = x to chain
        }
        val (x, chain) = best ?: return null
        val tail = if (chain.forced.isEmpty()) {
            "${chain.emptied.name} would have nowhere left"
        } else {
            "${chain.forcedPhrase()}, and the king${if (chain.forced.size == 1) " that forces" else "s those force"} " +
                "would leave ${chain.emptied.name} with nowhere to go"
        }
        return Step(
            technique = WHAT_IF,
            crosses = listOf(x),
            focus = setOf(x),
            cited = chain.cited(),
            nudge = "What if a king went on the glowing square?",
            explanation = "If a king went here, $tail. So it can be crossed out.",
        )
    }

    // ---- words and small helpers ------------------------------------------------------------------

    private fun lineOf(n: Int, kind: Kind, cell: Int) = if (kind == Kind.ROW) cell / n else cell % n

    private fun linesPhrase(kind: Kind, lines: List<Int>): String =
        (if (kind == Kind.ROW) "rows " else "columns ") + join(lines.map { (it + 1).toString() })

    private fun join(words: List<String>): String = when (words.size) {
        0 -> ""
        1 -> words[0]
        else -> words.dropLast(1).joinToString(", ") + " and " + words.last()
    }

    private fun numberWord(k: Int) =
        listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine")
            .getOrElse(k) { k.toString() }

    private fun String.cap() = replaceFirstChar { it.uppercaseChar() }

    /** Every [k]-sized subset of [items], in lexicographic index order. */
    private fun <T> combinations(items: List<T>, k: Int): Sequence<List<T>> = sequence {
        if (k > items.size || k <= 0) return@sequence
        val idx = IntArray(k) { it }
        while (true) {
            yield(idx.map { items[it] })
            var i = k - 1
            while (i >= 0 && idx[i] == items.size - k + i) i--
            if (i < 0) return@sequence
            idx[i]++
            for (j in i + 1 until k) idx[j] = idx[j - 1] + 1
        }
    }
}
