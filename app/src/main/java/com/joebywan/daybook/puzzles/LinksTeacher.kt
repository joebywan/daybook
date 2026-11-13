package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.TeachStep
import com.joebywan.daybook.core.cap
import com.joebywan.daybook.core.squareName

/**
 * Links, reasoned the way a person reasons it: a line's end with nowhere else to go, a square with only two ways in,
 * then which paths can still reach a square, what a way would cut off, and on Expert one supposition followed a few
 * steps.
 *
 * Every board Daybook deals is finished by exactly this ladder ([LinksLogic.accepts]; the steps are
 * [LinksLogic.nextStep], shared with the generator), so a step always exists on a board whose lines are right, and there
 * is no fallback to measure.
 *
 * [deduce] is handed the ends and the player's lines and has no parameter through which the answer could arrive. The
 * answer is read in one place, [mistake], to decide which line cannot be right; sound because every shipped board has
 * exactly one answer. No rung assumes that: none uses "a path never fills a 2x2", which holds only of one-answer boards.
 *
 * Techniques, simplest first:
 * 1. [ONE_WAY] — a line's end (or an end with no line yet) that can step to only one square goes there.
 * 2. [TWO_WAYS] — every square is on a path, so an empty square that only two neighbours can join is joined to both.
 * 3. [CUT] — a way that would cut a path off from its other end, or leave a square no path can fill, is struck.
 * 4. [WHAT_IF] — a line's end with two ways: suppose one, follow at most [LinksLogic.MAX_LINKS] forced steps, and
 *    something breaks, so it takes the other.
 *
 * Within a technique the first square in reading order wins, so a board gets the same hint on every platform.
 *
 * ## Highlight indices
 * Squares, `row * size + column`.
 */
internal object LinksTeacher {

    const val ONE_WAY = "one-way"
    const val TWO_WAYS = "two-ways"
    const val CUT = "cut-off"
    const val WHAT_IF = "what-if"
    const val MISTAKE = "mistake"

    /** Every technique in the order [deduce] prefers them, for reports. */
    val TECHNIQUES = listOf(ONE_WAY, TWO_WAYS, CUT, WHAT_IF)

    /** What the hint panel has room for (the test pins it). */
    const val MAX_NUDGE = 70
    const val MAX_EXPLANATION = 230

    /** One step: lines to draw (or, for a mistake, to take out), where to look, and why. */
    class Step(
        override val technique: String,
        val lines: List<Pair<Int, Int>> = emptyList(),
        val clears: List<Pair<Int, Int>> = emptyList(),
        override val focus: Set<Int>,
        override val cited: Set<Int>,
        override val nudge: String,
        override val explanation: String,
    ) : TeachStep {
        /** The squares the move touches. */
        override val targets: Set<Int> get() = (lines + clears).flatMap { listOf(it.first, it.second) }.toSet()
    }

    fun teach(s: LinksState): Step? {
        if (s.solved) return null
        mistake(s)?.let { return it }
        return deduce(s.size, s.ends, s.links)
    }

    // ---- mistakes -----------------------------------------------------------------------------

    /** The first line, in reading order, that the board's one answer does not have. */
    private fun mistake(s: LinksState): Step? {
        val n = s.size
        val mine = LinksLogic.masksOf(s.links)
        val answer = LinksLogic.masksOf(s.answer)
        for (q in 0 until n * n) for (d in listOf(LinksLogic.RIGHT, LinksLogic.DOWN)) {
            val bit = 1 shl d
            if (mine[q] and bit == 0 || answer[q] and bit != 0) continue
            val x = LinksLogic.next(n, q, d)
            return Step(
                technique = MISTAKE,
                clears = listOf(q to x),
                focus = setOf(q, x),
                cited = setOf(q, x),
                nudge = "One of your lines can't be right.",
                explanation = "No way of joining every pair and filling every square has a line between the glowing squares. Take it out.",
            )
        }
        return null
    }

    // ---- reasoning ----------------------------------------------------------------------------

    /** The simplest step on the board the player sees: the ends and their lines. Never the answer. */
    fun deduce(n: Int, ends: String, links: String): Step? {
        val b = LinksLogic.Board.of(n, ends)
        val s = LinksLogic.nextStep(b, LinksLogic.masksOf(links)) ?: return null
        val v = LinksLogic.View(b, LinksLogic.masksOf(links))
        return explain(v, s)
    }

    /**
     * Every step of [technique] on the visible board, [deduce]'s pick first when it is one: Training's "any instance counts"
     * (`MoveDrill(every)`). Never the answer.
     */
    fun steps(n: Int, ends: String, links: String, technique: String): List<Step> {
        val b = LinksLogic.Board.of(n, ends)
        val m = LinksLogic.masksOf(links)
        val rung = when (technique) {
            ONE_WAY, TWO_WAYS -> LinksLogic.LOCAL
            CUT -> LinksLogic.CUT
            WHAT_IF -> LinksLogic.WHAT_IF
            else -> return emptyList()
        }
        val v = LinksLogic.View(b, m)
        return LinksLogic.steps(b, m, rung).filter { techniqueOf(it) == technique }.map { explain(v, it) }
    }

    /** The technique a ladder step is taught as. */
    fun techniqueOf(s: LinksLogic.Step): String = when (s.rung) {
        LinksLogic.LOCAL -> if (s.kind == LinksLogic.K_ONE_WAY) ONE_WAY else TWO_WAYS
        LinksLogic.CUT -> CUT
        else -> WHAT_IF
    }

    private fun explain(v: LinksLogic.View, s: LinksLogic.Step): Step {
        val n = v.n
        val q = s.square
        val technique = techniqueOf(s)
        val here = squareName(n, q)
        val who = whose(v, q)
        val moved = s.lines.flatMap { listOf(it.first, it.second) }.toSet()
        val dests = s.lines.map { (a, x) -> if (a == q) x else a }
        val goes = dests.joinToString(" and ") { way(n, q, it) }
        val tip = s.kind == LinksLogic.K_ONE_WAY
        val nudge = if (tip) "Look at ${who.first} at $here." else "Look at the empty square at $here."
        val struck = s.struck.mapIndexed { i, x -> x to s.why.getOrNull(i) }
        val explanation = when (technique) {
            ONE_WAY -> "${who.second.cap()} at $here can step to only one square: $goes. Draw it there."
            TWO_WAYS -> "Every square is on a path. The one at $here can only be joined from $goes, so its path runs through both."
            CUT -> {
                val first = struck.firstOrNull { it.second != null } ?: struck.first()
                val because = first.second?.let { brokeText(v, it) } ?: "nothing could be finished"
                val dir = way(n, q, first.first)
                if (tip) {
                    "If ${who.second} at $here went $dir, $because. " + (if (struck.size > 1) "Nor can it go ${struck.drop(1).joinToString(" or ") { way(n, q, it.first) }}. " else "") + "So it goes $goes."
                } else {
                    "If the square at $here joined the one $dir, $because. That leaves $goes, so its path runs through both."
                }
            }
            else -> {
                val because = s.chainBroke?.let { brokeText(v, it) } ?: "something would break"
                val dir = way(n, q, s.supposed)
                val chain = if (s.chain.isEmpty()) "at once" else "a few forced moves later"
                if (tip) {
                    "Suppose ${who.second} at $here went $dir: $chain, $because. So it goes $goes."
                } else {
                    "Suppose the square at $here joined the one $dir: $chain, $because. So it joins $goes."
                }
            }
        }
        val cited = buildSet {
            add(q)
            addAll(s.struck)
            if (technique == WHAT_IF) addAll(s.chain)
            if (s.supposed >= 0) add(s.supposed)
        } - moved
        return Step(
            technique = technique,
            lines = s.lines,
            focus = setOf(q),
            cited = cited,
            nudge = nudge,
            explanation = explanation,
        )
    }

    /** How a tip is named: (for the nudge, in the sentence). */
    private fun whose(v: LinksLogic.View, q: Int): Pair<String, String> {
        val p = v.colour[q]
        return when {
            p >= 0 && v.deg[q] == 0 -> "the ${p + 1}" to "the ${p + 1}"
            p >= 0 -> "path ${p + 1}'s end" to "path ${p + 1}'s line"
            v.deg[q] == 1 -> "a loose line's end" to "the loose line"
            else -> "the square" to "the square"
        }
    }

    /** "up", "right", "down" or "left": where [x] is from [q]. */
    fun way(n: Int, q: Int, x: Int): String = when (LinksLogic.dirTo(n, q, x)) {
        LinksLogic.UP -> "up"
        LinksLogic.RIGHT -> "right"
        LinksLogic.DOWN -> "down"
        else -> "left"
    }

    /** What broke, as the end of a sentence. */
    private fun brokeText(v: LinksLogic.View, br: LinksLogic.Broken): String {
        val where = if (br.square >= 0) squareName(v.n, br.square) else ""
        return when (br.kind) {
            LinksLogic.B_CUT_OFF -> "path ${br.pair + 1} would be cut off from its other end"
            LinksLogic.B_STRANDED -> "no path could reach the square at $where"
            LinksLogic.B_DEAD_SQUARE -> "the square at $where would be boxed in"
            LinksLogic.B_DEAD_END -> "the line at $where would have nowhere to go"
            LinksLogic.B_CLASH -> "the square at $where would have to join two paths"
            else -> "two lines would clash"
        }
    }
}
