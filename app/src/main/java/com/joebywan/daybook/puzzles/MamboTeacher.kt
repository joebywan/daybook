package com.joebywan.daybook.puzzles

/**
 * Mambo, reasoned the way a person reasons it, one named step at a time.
 *
 * As in [KingsTeacher], the split is the point. [deduce] is handed the board as the player sees it
 * — the size, the symbols on it and the printed links — and has no parameter through which the
 * stored answer could reach it, so a step it explains cannot lean on a fact only the answer knows.
 * The answer is read in exactly two places, both in [teach]: to decide which of the player's
 * symbols are mistakes, and for [FALLBACK], which says openly that it is pointing at the answer.
 *
 * Techniques are tried simplest first, because the hint should be the step a person would have
 * found next, not the cleverest one available:
 * 1. [PAIR] — two alike side by side; the squares at either end take the other symbol.
 * 2. [SANDWICH] — a gap between two alike takes the other symbol.
 * 3. [LINK] — a printed `=` or `x` with one end filled carries it across.
 * 4. [QUOTA] — a line that already holds its half of one symbol; the rest are the other.
 * 5. [ALMOST] — a line one short of its half of one symbol, where putting that last one on a
 *    particular square would leave three of the other together.
 * 6. [WHAT_IF] — one symbol here would force, in one round of the rules above, a board that breaks
 *    a rule; so it is the other.
 *
 * Every generated board is carved so that the first four alone finish it ([Mambo]'s
 * `solvableByLogic`), so on a real board [ALMOST], [WHAT_IF] and the fallback are only reached from
 * boards the rules have not been applied to in order — they matter for boards with several answers,
 * which the tests walk too.
 *
 * Orders never come from a hash: every loop walks lines and cells in index order, so the same board
 * gets the same hint on every platform (CLAUDE.md, "Hash iteration order").
 */
internal object MamboTeacher {

    const val PAIR = "pair"
    const val SANDWICH = "sandwich"
    const val LINK = "link"
    const val QUOTA = "quota-met"
    const val ALMOST = "quota-almost-met"
    const val WHAT_IF = "what-if"
    const val FALLBACK = "fallback"
    const val MISTAKE = "mistake"

    /** Every technique in the order [deduce] tries them, for reports. */
    val TECHNIQUES = listOf(PAIR, SANDWICH, LINK, QUOTA, ALMOST, WHAT_IF, FALLBACK)

    /**
     * The longest explanation the panel is trusted to show whole. It has four lines of body text on
     * a phone; about 200 characters fit, and the tests hold every wording under this.
     */
    const val MAX_EXPLANATION = 200

    /**
     * One step: which symbols to place or which squares to clear, where to look, and why. [clears]
     * is only ever a mistake being taken back.
     */
    class Step(
        val technique: String,
        val places: Map<Int, Sym> = emptyMap(),
        val clears: List<Int> = emptyList(),
        val focus: Set<Int>,
        val cited: Set<Int>,
        val nudge: String,
        val explanation: String,
    ) {
        val targets: Set<Int> get() = places.keys + clears
    }

    /** A row or a column, with the name the board would be read by. */
    private class Line(val cells: List<Int>, val name: String)

    private fun linesOf(n: Int): List<Line> = buildList {
        for (r in 0 until n) add(Line(List(n) { r * n + it }, "row ${r + 1}"))
        for (c in 0 until n) add(Line(List(n) { it * n + c }, "column ${c + 1}"))
    }

    // ---- the whole hint: mistakes, then reasoning, then the honest fallback ----------------------

    /**
     * The next thing to show this player, or null on a solved board.
     *
     * Mistakes come first: a step reasoned from a board with a wrong symbol on it could be reasoning
     * from a falsehood.
     */
    fun teach(s: MamboState): Step? {
        if (s.solved) return null
        mistake(s)?.let { return it }
        return deduce(s.size, s.cells, s.links) ?: fallback(s)
    }

    /**
     * A symbol no legal answer keeps. One that visibly breaks a rule is named first, since the
     * board is already pointing at it; otherwise the first in reading order.
     */
    private fun mistake(s: MamboState): Step? {
        val wrong = Mambo.mistakes(s)
        if (wrong.isEmpty()) return null
        val broken = s.violations()
        val cell = wrong.firstOrNull { w -> broken.any { w in it.cells } } ?: wrong[0]
        val sym = s.cells[cell]
        // The tap cycle runs moon, sun, empty: a wrong moon is one tap from the right sun, while a
        // wrong sun goes to empty. Say which, so the player is not left tapping round the cycle.
        val fix = if (sym == Sym.MOON) "Tap it once to make it a sun." else "Tap it to clear it."
        val why = broken.firstOrNull { cell in it.cells }
        val (reason, cited) = when (why?.rule) {
            Broken.TRIPLE -> "This makes three ${plural(sym)} together." to why.cells.toSet()
            Broken.BALANCE -> {
                val name = linesOf(s.size).first { it.cells == why.cells }.name
                "${name.cap()} has more than ${s.size / 2} ${plural(sym)} with this one." to why.cells.toSet()
            }
            Broken.LINK -> {
                val link = s.links.first { setOf(it.a, it.b) == why.cells.toSet() }
                (if (link.same) "The = says these two match, and they don't." else "The x says these two differ, and they don't.") to
                    why.cells.toSet()
            }
            null -> "This ${sym.word()} doesn't fit the rest of the board." to emptySet()
        }
        return Step(
            technique = MISTAKE,
            clears = listOf(cell),
            focus = setOf(cell),
            cited = cited - cell,
            nudge = "Check this ${sym.word()}.",
            explanation = "$reason $fix",
        )
    }

    /**
     * Nothing short enough to explain applies, so point at a square from the answer — in the line
     * with the fewest empty squares, which is where the player is nearest to finishing. Says
     * plainly that it is doing this.
     */
    private fun fallback(s: MamboState): Step? {
        val n = s.size
        val line = linesOf(n)
            .filter { l -> l.cells.any { s.cells[it] == Sym.NONE } }
            .minByOrNull { l -> l.cells.count { s.cells[it] == Sym.NONE } } ?: return null
        val cell = line.cells.first { s.cells[it] == Sym.NONE }
        val sym = Mambo.answerFor(s)[cell]
        return Step(
            technique = FALLBACK,
            places = mapOf(cell to sym),
            focus = line.cells.toSet(),
            cited = emptySet(),
            nudge = "This one takes a longer chain. Look at ${line.name}.",
            explanation = "This one needs a longer chain than a hint can walk through, " +
                "so here's a square from the answer: it's a ${sym.word()}.",
        )
    }

    // ---- reasoning from what the player can see ---------------------------------------------------

    /**
     * The simplest step visible on this board, or null when none of the techniques applies.
     *
     * Takes no answer on purpose — see the class comment. [cells] is taken as the player's; the
     * caller has already dealt with any that are mistakes.
     */
    fun deduce(n: Int, cells: List<Sym>, links: List<Link>): Step? {
        val lines = linesOf(n)
        return pair(cells, lines)
            ?: sandwich(cells, lines)
            ?: link(cells, links)
            ?: quota(n, cells, lines)
            ?: almost(n, cells, lines)
            ?: whatIf(n, cells, links, lines)
    }

    private fun pair(cells: List<Sym>, lines: List<Line>): Step? {
        for (line in lines) {
            val l = line.cells
            for (k in 0 until l.size - 1) {
                val sym = cells[l[k]]
                if (sym == Sym.NONE || cells[l[k + 1]] != sym) continue
                val ends = listOfNotNull(l.getOrNull(k - 1), l.getOrNull(k + 2)).filter { cells[it] == Sym.NONE }
                if (ends.isEmpty()) continue
                val other = sym.other()
                val where = if (ends.size == 2) "both ends" else "the open end"
                return Step(
                    technique = PAIR,
                    places = ends.associateWith { other },
                    focus = setOf(l[k], l[k + 1]),
                    cited = setOf(l[k], l[k + 1]),
                    nudge = "Look at the two ${plural(sym)} side by side in ${line.name}.",
                    explanation = "Two ${plural(sym)} sit side by side in ${line.name}, so $where must be " +
                        "${if (ends.size == 2) plural(other) else "a ${other.word()}"}. " +
                        "Another ${sym.word()} there would make three together.",
                )
            }
        }
        return null
    }

    private fun sandwich(cells: List<Sym>, lines: List<Line>): Step? {
        for (line in lines) {
            val l = line.cells
            for (k in 0 until l.size - 2) {
                val sym = cells[l[k]]
                if (sym == Sym.NONE || cells[l[k + 2]] != sym || cells[l[k + 1]] != Sym.NONE) continue
                val other = sym.other()
                return Step(
                    technique = SANDWICH,
                    places = mapOf(l[k + 1] to other),
                    focus = setOf(l[k], l[k + 1], l[k + 2]),
                    cited = setOf(l[k], l[k + 2]),
                    nudge = "Look at the gap between two ${plural(sym)} in ${line.name}.",
                    explanation = "The gap between two ${plural(sym)} must be a ${other.word()}: a ${sym.word()} " +
                        "there would make three ${plural(sym)} together.",
                )
            }
        }
        return null
    }

    private fun link(cells: List<Sym>, links: List<Link>): Step? {
        for (link in links) {
            val a = cells[link.a]
            val b = cells[link.b]
            val (known, open) = when {
                a != Sym.NONE && b == Sym.NONE -> link.a to link.b
                b != Sym.NONE && a == Sym.NONE -> link.b to link.a
                else -> continue
            }
            val sym = cells[known]
            val result = if (link.same) sym else sym.other()
            return Step(
                technique = LINK,
                places = mapOf(open to result),
                focus = setOf(link.a, link.b),
                cited = setOf(known),
                nudge = "Look at the ${if (link.same) "=" else "x"} between the glowing squares.",
                explanation = if (link.same) {
                    "The = says these two squares match. One is a ${sym.word()}, so the other is a ${result.word()} too."
                } else {
                    "The x says these two squares differ. One is a ${sym.word()}, so the other is a ${result.word()}."
                },
            )
        }
        return null
    }

    private fun quota(n: Int, cells: List<Sym>, lines: List<Line>): Step? {
        val half = n / 2
        for (line in lines) {
            val open = line.cells.filter { cells[it] == Sym.NONE }
            if (open.isEmpty()) continue
            for (sym in SYMS) {
                if (line.cells.count { cells[it] == sym } != half) continue
                val other = sym.other()
                return Step(
                    technique = QUOTA,
                    places = open.associateWith { other },
                    focus = line.cells.toSet(),
                    cited = line.cells.filter { cells[it] == sym }.toSet(),
                    nudge = "Count the ${plural(sym)} in ${line.name}.",
                    explanation = "${line.name.cap()} already has its ${numberWord(half)} ${plural(sym)}, " +
                        "so the rest of it must be ${plural(other)}.",
                )
            }
        }
        return null
    }

    /**
     * A line one short of its quota of some symbol. Its last one must go on one of the open squares
     * and every other open square gets the opposite; if putting it on a particular square leaves
     * three of the opposite together, that square is the opposite.
     */
    private fun almost(n: Int, cells: List<Sym>, lines: List<Line>): Step? {
        val half = n / 2
        for (line in lines) {
            val open = line.cells.filter { cells[it] == Sym.NONE }
            if (open.size < 2) continue
            for (sym in SYMS) {
                if (line.cells.count { cells[it] == sym } != half - 1) continue
                val other = sym.other()
                for (e in open) {
                    val trial = line.cells.map { i ->
                        when {
                            cells[i] != Sym.NONE -> cells[i]
                            i == e -> sym
                            else -> other
                        }
                    }
                    val run = (0 until n - 2).firstOrNull { k ->
                        trial[k] == other && trial[k + 1] == other && trial[k + 2] == other
                    } ?: continue
                    return Step(
                        technique = ALMOST,
                        places = mapOf(e to other),
                        focus = line.cells.toSet(),
                        cited = (run..run + 2).map { line.cells[it] }.toSet(),
                        nudge = "${line.name.cap()} needs just one more ${sym.word()}.",
                        explanation = "${line.name.cap()} needs one more ${sym.word()}. If it went here, the rest " +
                            "would be ${plural(other)}, and three would sit together. So this is a ${other.word()}.",
                    )
                }
            }
        }
        return null
    }

    /** Why a hypothetical board cannot stand, and the squares that show it. */
    private class Contradiction(val phrase: String, val cells: Set<Int>)

    /**
     * One symbol on a square, followed through one round of [PAIR], [SANDWICH], [LINK] and [QUOTA].
     * If what that round forces breaks a rule, the square takes the other symbol. One round only, so
     * the whole argument still fits in a sentence the player can check against the board.
     */
    private fun whatIf(n: Int, cells: List<Sym>, links: List<Link>, lines: List<Line>): Step? {
        for (i in cells.indices) {
            if (cells[i] != Sym.NONE) continue
            for (sym in SYMS) {
                val trial = cells.toMutableList().also { it[i] = sym }
                val forced = forcedOnce(n, trial, links, lines)
                val clash = forced.entries.firstOrNull { it.value.size > 1 }
                val contradiction = if (clash != null) {
                    Contradiction("one square would have to be both a sun and a moon", setOf(clash.key))
                } else {
                    val after = trial.toMutableList()
                    for ((cell, syms) in forced) after[cell] = syms.first()
                    brokenRule(n, after, links, lines)
                } ?: continue
                val other = sym.other()
                return Step(
                    technique = WHAT_IF,
                    places = mapOf(i to other),
                    focus = setOf(i),
                    cited = forced.keys + contradiction.cells,
                    nudge = "What if the glowing square were a ${sym.word()}?",
                    explanation = "If this were a ${sym.word()}, the rules would force the marked squares, and " +
                        "${contradiction.phrase}. So it's a ${other.word()}.",
                )
            }
        }
        return null
    }

    /** Every open square one round of the four simple techniques fills, with what it would take. */
    private fun forcedOnce(n: Int, cells: List<Sym>, links: List<Link>, lines: List<Line>): Map<Int, Set<Sym>> {
        val half = n / 2
        val out = LinkedHashMap<Int, MutableSet<Sym>>()
        fun force(i: Int, sym: Sym) {
            if (cells[i] == Sym.NONE) out.getOrPut(i) { LinkedHashSet() } += sym
        }
        for (line in lines) {
            val l = line.cells
            for (k in 0 until l.size - 2) {
                val x = cells[l[k]]
                val y = cells[l[k + 1]]
                val z = cells[l[k + 2]]
                if (x != Sym.NONE && x == y) force(l[k + 2], x.other())
                if (y != Sym.NONE && y == z) force(l[k], y.other())
                if (x != Sym.NONE && x == z) force(l[k + 1], x.other())
            }
            for (sym in SYMS) {
                if (l.count { cells[it] == sym } == half) l.forEach { force(it, sym.other()) }
            }
        }
        for (link in links) {
            val a = cells[link.a]
            val b = cells[link.b]
            if (a != Sym.NONE) force(link.b, if (link.same) a else a.other())
            if (b != Sym.NONE) force(link.a, if (link.same) b else b.other())
        }
        return out
    }

    /** The first rule [cells] visibly breaks, worded to follow "and", or null if none. */
    private fun brokenRule(n: Int, cells: List<Sym>, links: List<Link>, lines: List<Line>): Contradiction? {
        val half = n / 2
        for (line in lines) {
            val l = line.cells
            for (k in 0 until l.size - 2) {
                val sym = cells[l[k]]
                if (sym != Sym.NONE && cells[l[k + 1]] == sym && cells[l[k + 2]] == sym) {
                    return Contradiction("that makes three ${plural(sym)} together", setOf(l[k], l[k + 1], l[k + 2]))
                }
            }
            for (sym in SYMS) {
                if (l.count { cells[it] == sym } > half) {
                    return Contradiction("${line.name} would have too many ${plural(sym)}", l.toSet())
                }
            }
        }
        for (link in links) {
            val a = cells[link.a]
            val b = cells[link.b]
            if (a != Sym.NONE && b != Sym.NONE && (a == b) != link.same) {
                return Contradiction("the ${if (link.same) "=" else "x"} between two of them breaks", setOf(link.a, link.b))
            }
        }
        return null
    }

    // ---- words --------------------------------------------------------------------------------------

    private val SYMS = listOf(Sym.SUN, Sym.MOON)

    private fun Sym.word() = if (this == Sym.SUN) "sun" else "moon"

    private fun plural(sym: Sym) = sym.word() + "s"

    private fun numberWord(k: Int) =
        listOf("zero", "one", "two", "three", "four", "five", "six").getOrElse(k) { k.toString() }

    private fun String.cap() = replaceFirstChar { it.uppercaseChar() }
}
