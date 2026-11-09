package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.LoopEdges
import com.joebywan.daybook.core.LoopFound
import com.joebywan.daybook.core.LoopGrid
import com.joebywan.daybook.core.squareName

/**
 * Loop, reasoned the way a person reasons it: a number at a time, a dot at a time, a number with its corners, then the
 * rule that the loop is one, and on Expert one supposition followed a few rounds.
 *
 * Every board Daybook deals is finished by exactly this ladder ([LoopLogic.accepts]), so a step always exists on a board
 * whose marks are right, and there is no fallback to measure.
 *
 * [deduce] is handed the numbers and the player's edges and has no parameter through which the answer could arrive. The
 * answer is read in one place, [mistake], to decide which of the player's marks cannot be right; sound because every
 * shipped board has exactly one loop.
 *
 * A dot with two lines takes no more: the teacher reads its other edges as crossed whether or not the player has
 * crossed them ([LoopEdges.view]), so it never asks for that tidying.
 *
 * Techniques, simplest first:
 * 1. [COUNT] — a number with all its lines crosses its other sides; one with only as many sides left as it needs draws them.
 * 2. [ONE_WAY] — a dot with one line and one way on: the line continues.
 * 3. [DEAD_END] — a dot with one way in can never be passed through: cross it.
 * 4. [CORNER] — a number and the dots at its corners together (each dot takes no line or two) leave an edge one way.
 * 5. [SMALL_LOOP] — joining two ends of one line would close a loop that is not the whole answer.
 * 6. [WHAT_IF] — suppose a line (or a cross), follow the rules a few rounds, and something breaks.
 *
 * Within a technique the first square or dot in reading order wins (edges in edge order for 5 and 6), so a board gets
 * the same hint on every platform.
 *
 * ## Highlight indices
 * Squares are `row * size + column`; edge `e` is `size * size + e` ([edgeIndex]); dot `d` is after the edges ([dotIndex]).
 */
internal object LoopTeacher {

    const val COUNT = "count"
    const val ONE_WAY = "one-way"
    const val DEAD_END = "dead-end"
    const val CORNER = "corner"
    const val SMALL_LOOP = "small-loop"
    const val WHAT_IF = "what-if"
    const val MISTAKE = "mistake"

    /** Every technique in the order [deduce] prefers them, for reports. */
    val TECHNIQUES = listOf(COUNT, ONE_WAY, DEAD_END, CORNER, SMALL_LOOP, WHAT_IF)

    /** What the hint panel has room for (the test pins it). */
    const val MAX_NUDGE = 70
    const val MAX_EXPLANATION = 200

    /** One step: edges to draw, cross or clear, where to look, and why. [clears] is only ever a mistake. */
    class Step(
        val technique: String,
        val lines: List<Int> = emptyList(),
        val crosses: List<Int> = emptyList(),
        val clears: List<Int> = emptyList(),
        val focus: Set<Int>,
        val cited: Set<Int>,
        val nudge: String,
        val explanation: String,
    ) {
        /** Highlight indices of the edges the move changes. */
        fun targets(n: Int): Set<Int> = (lines + crosses + clears).map { edgeIndex(n, it) }.toSet()
    }

    fun edgeIndex(n: Int, e: Int) = n * n + e

    fun dotIndex(n: Int, d: Int) = n * n + LoopLogic.grid(n).edgeCount + d

    fun teach(s: LoopState): Step? {
        if (s.solved) return null
        mistake(s)?.let { return it }
        return deduce(s.size, s.clues, s.edges)
    }

    // ---- mistakes ---------------------------------------------------------------------------

    /** The first edge, in edge order, that the board's one loop disagrees with. */
    private fun mistake(s: LoopState): Step? {
        val n = s.size
        val g = LoopLogic.grid(n)
        for (e in 0 until g.edgeCount) {
            val wrongLine = s.edges[e] == LoopEdges.LINE_MARK && s.loop[e] == '0'
            val wrongCross = s.edges[e] == LoopEdges.CROSS_MARK && s.loop[e] == '1'
            if (!wrongLine && !wrongCross) continue
            return Step(
                technique = MISTAKE,
                clears = listOf(e),
                focus = setOf(edgeIndex(n, e)),
                cited = LoopLogic.squaresBeside(n, e).toSet(),
                nudge = "One of your marks doesn't fit the numbers.",
                explanation = if (wrongLine) {
                    "No loop that keeps every number has the glowing line. Rub it out."
                } else {
                    "The loop has to run along the glowing cross: take it off."
                },
            )
        }
        return null
    }

    // ---- reasoning --------------------------------------------------------------------------

    /**
     * The next step reasoned from the [clues] and the player's [edges] alone, or null when nothing can be settled (a
     * finished board, or one with a wrong mark).
     */
    fun deduce(n: Int, clues: String, edges: String): Step? {
        val g = LoopLogic.grid(n)
        val st = LoopEdges.view(g, edges)
        val found = localFounds(g, n, clues, st)
        if (found.any { it.contradiction }) return null
        // The simplest kind, the first square or dot in reading order among them.
        found.minByOrNull { it.kind }?.let { return localStep(g, n, clues, st, it) }
        val rules = LoopLogic.Rules(n, clues)
        LoopEdges.smallLoop(rules, st, firstOnly = true)?.let { f ->
            return if (f.contradiction) null else loopStep(g, n, st, f.crosses[0])
        }
        // A short what-if, as the generator's ladder uses; when a player's own order has left none short enough, the
        // shortest of any length (still sound, still named), so the hints never run out.
        val w = LoopEdges.bestWhatIf(rules, st) ?: LoopEdges.bestWhatIf(rules, st, cap = Int.MAX_VALUE) ?: return null
        return whatIfStep(g, n, clues, w)
    }

    /** Every local rule that settles something: each number (its count, else its corners), then each dot, in reading order. */
    private fun localFounds(g: LoopGrid, n: Int, clues: String, st: IntArray): List<LoopFound> = buildList {
        for (q in 0 until n * n) if (clues[q] != '.') LoopLogic.numberRule(g, n, clues, st, q)?.let(::add)
        for (d in 0 until g.nodes) LoopEdges.pointRule(g, st, d, mustPass = false, full = false)?.let(::add)
    }

    /**
     * Every step of [technique] reasoned from the [clues] and [edges] alone, as [deduce] would give them: each number's
     * or dot's rule of that kind, or each edge that would close a small loop. A what-if is only ever the one [deduce]
     * picks. Empty on a board with a contradiction.
     */
    fun steps(n: Int, clues: String, edges: String, technique: String): List<Step> {
        val g = LoopLogic.grid(n)
        val st = LoopEdges.view(g, edges)
        val found = localFounds(g, n, clues, st)
        if (found.any { it.contradiction }) return emptyList()
        if (technique == SMALL_LOOP) {
            val f = LoopEdges.smallLoop(LoopLogic.Rules(n, clues), st) ?: return emptyList()
            if (f.contradiction) return emptyList()
            return f.crosses.map { loopStep(g, n, st, it) }
        }
        return found.map { localStep(g, n, clues, st, it) }.filter { it.technique == technique }
    }

    private fun localStep(g: LoopGrid, n: Int, clues: String, st: IntArray, f: LoopFound): Step {
        val touched = (f.lines + f.crosses).map { edgeIndex(n, it) }.toSet()
        fun step(technique: String, nudge: String, why: String, focus: Set<Int>, cited: Set<Int> = focus) = Step(
            technique, f.lines.toList(), f.crosses.toList(), focus = focus, cited = cited, nudge = nudge, explanation = why,
        )
        return when (f.kind) {
            LoopLogic.K_COUNT -> {
                val q = f.at
                val k = clues[q] - '0'
                val why = when {
                    k == 0 -> "A 0 has no line round it: cross all four of its sides."
                    f.crosses.isNotEmpty() -> "The $k already has ${lines(k)} round it, so its other sides are empty: cross them."
                    else -> "The $k has only ${k.words()} ${if (k == 1) "side" else "sides"} left that could take a line, " +
                        "so ${if (k == 1) "it is a line" else "all of them are lines"}."
                }
                step(COUNT, "Look at the glowing $k.", why, setOf(q) + touched)
            }
            LoopEdges.K_ONE_WAY -> step(
                ONE_WAY, "Follow the line into the glowing dot.",
                "The line into the glowing dot has only one way on, so it continues there.",
                setOf(dotIndex(n, f.at)) + touched, setOf(dotIndex(n, f.at)) + dotEdges(g, n, f.at),
            )
            LoopEdges.K_DEAD_END -> step(
                DEAD_END, "The glowing dot has only one way out.",
                "A loop through the glowing dot would need two ways, and it has only one left. So no line goes there: cross it.",
                setOf(dotIndex(n, f.at)) + touched, setOf(dotIndex(n, f.at)) + dotEdges(g, n, f.at),
            )
            else -> cornerStep(g, n, clues, st, f, touched)
        }
    }

    private fun dotEdges(g: LoopGrid, n: Int, d: Int): Set<Int> =
        (0 until 4).mapNotNull { g.edge(d, it).takeIf { e -> e >= 0 }?.let { e -> edgeIndex(n, e) } }.toSet()

    private val CORNER_NAMES = listOf("top-left", "top-right", "bottom-right", "bottom-left")

    /**
     * A number with its corners: names the one corner that settles it alone if there is one (with why that dot is
     * pinned), else the corners together.
     */
    private fun cornerStep(g: LoopGrid, n: Int, clues: String, st: IntArray, f: LoopFound, touched: Set<Int>): Step {
        val q = f.at
        val k = clues[q] - '0'
        val dots = LoopLogic.corners(n, q)
        val sides = LoopLogic.sides(n, q)
        val wanted = f.lines.toSet() + f.crosses.toSet()
        // The first corner that settles one of the step's edges alone.
        val alone = (0 until 4).firstOrNull { i ->
            val one = LoopLogic.cornerRule(g, n, st, q, k, use = 1 shl i) ?: return@firstOrNull false
            !one.contradiction && (one.lines.toList() + one.crosses.toList()).any { it in wanted }
        }
        val needs = "The $k needs ${lines(k)} round it"
        val why = if (alone == null) {
            "$needs, and each dot at its corners takes no line or two. Every way to do both " +
                "${verb(f)} the glowing ${if (wanted.size == 1) "edge" else "edges"}."
        } else {
            val d = dots[alone]
            val mine = setOf(sides[alone], sides[(alone + 3) % 4])
            val outer = (0 until 4).map { g.edge(d, it) }.filter { it >= 0 && it !in mine }
            val into = outer.count { st[it] == LoopLogic.LINE }
            val free = outer.count { st[it] == LoopLogic.OPEN }
            val corner = CORNER_NAMES[alone]
            val pinned = when {
                into == 0 && free == 0 -> "its $corner dot has no way out but its two sides there, so both or neither are lines"
                into == 1 && free == 0 -> "the line into its $corner dot must go on along one of its two sides there"
                into == 1 -> "a line comes into its $corner dot, so only one more line leaves it"
                else -> "its $corner dot takes no line or two"
            }
            "$needs, and $pinned. Every way to do both ${verb(f)} the glowing ${if (wanted.size == 1) "edge" else "edges"}."
        }
        val focus = setOf(q) + touched + if (alone != null) setOf(dotIndex(n, dots[alone])) else emptySet()
        return Step(
            CORNER, f.lines.toList(), f.crosses.toList(),
            focus = focus, cited = focus + sides.map { edgeIndex(n, it) },
            nudge = if (alone != null) "Look at the glowing $k and its ${CORNER_NAMES[alone]} corner." else "Look at the glowing $k and its corners.",
            explanation = why,
        )
    }

    private fun verb(f: LoopFound): String = when {
        f.crosses.isEmpty() -> "draws"
        f.lines.isEmpty() -> "crosses out"
        else -> "settles"
    }

    private fun loopStep(g: LoopGrid, n: Int, st: IntArray, e: Int): Step {
        val a = g.endA[e]
        val b = g.endB[e]
        // The stretch the edge would close, for the highlight.
        val s = LoopEdges.stretches(g, st)
        val stretch = (0 until g.edgeCount).filter { st[it] == LoopLogic.LINE && s.root[g.endA[it]] == s.root[a] }
        val others = s.edgesIn[s.root[a]] != s.total
        return Step(
            SMALL_LOOP,
            crosses = listOf(e),
            focus = setOf(dotIndex(n, a), dotIndex(n, b), edgeIndex(n, e)),
            cited = stretch.map { edgeIndex(n, it) }.toSet() + setOf(dotIndex(n, a), dotIndex(n, b)),
            nudge = "Two ends of one line meet at the glowing edge.",
            explanation = "A line on the glowing edge would close a loop that " +
                (if (others) "leaves out other lines" else "leaves a number without its count") +
                ". There is only one loop, so cross it.",
        )
    }

    private fun whatIfStep(g: LoopGrid, n: Int, clues: String, w: LoopEdges.WhatIf): Step {
        val e = w.edge
        val line = w.assume == LoopLogic.LINE
        val c = w.contradiction.at
        val broke = when (w.contradiction.kind) {
            LoopEdges.C_THREE -> "${dotName(n, c)} would need three lines"
            LoopEdges.C_DEAD_LINE -> "a line would run into a dead end at ${dotName(n, c)}"
            LoopLogic.C_COUNT -> "the ${clues[c]} at ${squareName(n, c)} could not have its count"
            LoopLogic.C_CORNER -> "the ${clues[c]} at ${squareName(n, c)} could not have its count with its corners"
            else -> "a line would close a loop that is not the whole answer"
        }
        val rounds = if (w.rounds <= 1) "" else " (${w.rounds} steps on)"
        val suppose = if (line) "With a line on the glowing edge" else "With no line on the glowing edge"
        val cited = when (w.contradiction.kind) {
            LoopEdges.C_THREE, LoopEdges.C_DEAD_LINE -> setOf(dotIndex(n, c))
            LoopLogic.C_COUNT, LoopLogic.C_CORNER -> setOf(c)
            else -> emptySet()
        }
        return Step(
            WHAT_IF,
            lines = if (line) emptyList() else listOf(e),
            crosses = if (line) listOf(e) else emptyList(),
            focus = setOf(edgeIndex(n, e), dotIndex(n, g.endA[e]), dotIndex(n, g.endB[e])),
            cited = cited + edgeIndex(n, e),
            nudge = "Suppose ${if (line) "a line ran" else "no line ran"} along the glowing edge.",
            explanation = "$suppose$rounds, $broke. So ${if (line) "there is none: cross it" else "there is one: draw it"}.",
        )
    }

    // ---- words ------------------------------------------------------------------------------

    private fun lines(k: Int) = if (k == 1) "one line" else "${k.words()} lines"

    private fun Int.words() = listOf("no", "one", "two", "three", "four").getOrElse(this) { toString() }

    /** "the dot at the top-left of row 2, column 3": a dot named by a square it is a corner of. */
    fun dotName(n: Int, d: Int): String {
        val m = n + 1
        val r = d / m
        val c = d % m
        val qr = minOf(r, n - 1)
        val qc = minOf(c, n - 1)
        val corner = (if (r > qr) "bottom" else "top") + "-" + (if (c > qc) "right" else "left")
        return "the $corner dot of ${squareName(n, qr * n + qc)}"
    }
}
