package com.joebywan.daybook.puzzles

/**
 * Pearls, reasoned the way a person reasons it: a pearl at a time, a square at a time, then the rule
 * that the loop is one, and on Expert one supposition followed a few rounds.
 *
 * Every board Daybook deals is finished by exactly this ladder ([PearlsLogic.accepts]), so a step
 * always exists on a board whose marks are right, and there is no fallback to measure.
 *
 * [deduce] is handed the pearls and the player's edges and has no parameter through which the answer
 * could arrive. The answer is read in one place, [mistake], to decide which of the player's marks
 * cannot be right; sound because every shipped board has exactly one loop.
 *
 * A square with two lines takes no more: the teacher reads its other edges as crossed whether or not
 * the player has crossed them ([view]), so it never asks for that tidying.
 *
 * Techniques, simplest first:
 * 1. [BLACK_PEARL] — a black pearl turns and runs straight on through the next square each way: an
 *    arm it cannot draw sends it the other way, and a line out of it carries on.
 * 2. [WHITE_PEARL] — a white pearl goes straight through: a way it cannot go sends it the other.
 * 3. [WHITE_TURN] — a white pearl's loop turns on at least one side: straight on one side, a turn on
 *    the other.
 * 4. [ONE_WAY] — a square with one line and one way on: the line continues.
 * 5. [PEARL_WAYS] — a pearl with only two ways in left takes both.
 * 6. [DEAD_END] — an empty square with one way in can never be passed through: cross it.
 * 7. [SMALL_LOOP] — joining two ends of one line would close a loop that leaves something out.
 * 8. [WHAT_IF] — suppose a line (or a cross), follow the rules a few rounds, and something breaks.
 *
 * Within a technique the first square in reading order wins (edges in edge order for 7 and 8), so a
 * board gets the same hint on every platform.
 *
 * ## Highlight indices
 * Squares are `row * size + column`; edge `e` is `size * size + e` ([edgeIndex]).
 */
internal object PearlsTeacher {

    const val BLACK_PEARL = "black-pearl"
    const val WHITE_PEARL = "white-pearl"
    const val WHITE_TURN = "white-turn"
    const val ONE_WAY = "one-way"
    const val PEARL_WAYS = "pearl-ways"
    const val DEAD_END = "dead-end"
    const val SMALL_LOOP = "small-loop"
    const val WHAT_IF = "what-if"
    const val MISTAKE = "mistake"

    /** Every technique in the order [deduce] prefers them, for reports. */
    val TECHNIQUES = listOf(BLACK_PEARL, WHITE_PEARL, WHITE_TURN, ONE_WAY, PEARL_WAYS, DEAD_END, SMALL_LOOP, WHAT_IF)

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

    fun teach(s: PearlsState): Step? {
        if (s.solved) return null
        mistake(s)?.let { return it }
        return deduce(s.size, s.pearls, s.edges)
    }

    /**
     * The edges as the teacher reads them: the player's lines and crosses, and every open edge of a
     * square that already has two lines read as crossed.
     */
    fun view(n: Int, edges: String): IntArray {
        val g = PearlsGrid.of(n)
        val st = PearlsLogic.stateOf(edges)
        val lines = IntArray(g.squares)
        for (e in 0 until g.edgeCount) if (st[e] == PearlsLogic.LINE) {
            lines[g.endA[e]]++
            lines[g.endB[e]]++
        }
        for (e in 0 until g.edgeCount) {
            if (st[e] == PearlsLogic.OPEN && (lines[g.endA[e]] >= 2 || lines[g.endB[e]] >= 2)) st[e] = PearlsLogic.CROSS
        }
        return st
    }

    // ---- mistakes ---------------------------------------------------------------------------

    /** The first edge, in edge order, that the board's one loop disagrees with. */
    private fun mistake(s: PearlsState): Step? {
        val n = s.size
        val g = PearlsGrid.of(n)
        for (e in 0 until g.edgeCount) {
            val wrongLine = s.edges[e] == PearlsLogic.LINE_MARK && s.loop[e] == '0'
            val wrongCross = s.edges[e] == PearlsLogic.CROSS_MARK && s.loop[e] == '1'
            if (!wrongLine && !wrongCross) continue
            val where = "between ${at(n, g.endA[e])} and ${squareName(n, g.endB[e])}"
            return Step(
                technique = MISTAKE,
                clears = listOf(e),
                focus = setOf(edgeIndex(n, e)),
                cited = setOf(g.endA[e], g.endB[e]),
                nudge = "One of your marks doesn't fit the pearls.",
                explanation = if (wrongLine) {
                    "No loop that keeps every pearl's rule has the line $where. Rub it out."
                } else {
                    "The loop has to pass $where: take the cross off."
                },
            )
        }
        return null
    }

    // ---- reasoning --------------------------------------------------------------------------

    /**
     * The next step reasoned from the [pearls] and the player's [edges] alone, or null when nothing
     * can be settled (a finished board, or one with a wrong mark).
     */
    fun deduce(n: Int, pearls: String, edges: String): Step? {
        val g = PearlsGrid.of(n)
        val st = view(n, edges)
        var best: PearlsLogic.Found? = null
        for (s in 0 until g.squares) {
            val pearl = when (pearls[s]) {
                PearlsLogic.BLACK -> PearlsLogic.blackRule(g, pearls, st, s)
                PearlsLogic.WHITE -> PearlsLogic.whiteRule(g, st, s)
                else -> null
            }
            for (f in listOfNotNull(pearl, PearlsLogic.squareRule(g, pearls, st, s, full = false))) {
                if (f.contradiction) return null
                if (best == null || f.kind < best.kind) best = f
            }
        }
        if (best != null) return localStep(g, pearls, st, best)
        PearlsLogic.smallLoop(g, pearls, st, firstOnly = true)?.let { f ->
            return if (f.contradiction) null else loopStep(g, st, f)
        }
        val w = PearlsLogic.bestWhatIf(g, pearls, st) ?: return null
        return whatIfStep(g, pearls, w)
    }

    private fun localStep(g: PearlsGrid, pearls: String, st: IntArray, f: PearlsLogic.Found): Step {
        val n = g.n
        val s = f.square
        val place = at(n, s)
        val edgesOf = (0 until 4).mapNotNull { d -> g.edge(s, d).takeIf { it >= 0 } }
        fun step(technique: String, nudge: String, why: String, focus: Set<Int>, cited: Set<Int> = focus) = Step(
            technique, f.lines.toList(), f.crosses.toList(), focus = focus, cited = cited, nudge = nudge, explanation = why,
        )
        val touched = (f.lines + f.crosses).map { edgeIndex(n, it) }.toSet()
        return when (f.kind) {
            PearlsLogic.K_BLACK -> {
                val why = (0 until 2).mapNotNull { axis -> blackAxis(g, pearls, st, s, axis) }.joinToString(" ")
                step(
                    BLACK_PEARL, "Look at the black pearl $place.",
                    "A black pearl turns, and runs straight on through the next square each way. $why",
                    setOf(s) + touched,
                )
            }
            PearlsLogic.K_WHITE -> {
                val axis = if (f.lines.any { !g.isHorizontal(it) } || f.crosses.any { g.isHorizontal(it) }) 0 else 1
                val run = if (axis == 0) "up and down" else "across"
                val other = if (axis == 0) "across" else "up and down"
                val has = (0 until 2).any { d -> st[g.edge(s, axis + 2 * d)] == PearlsLogic.LINE }
                val reason = if (has) "The line through it already runs $run" else "It can't go $other (${whiteReason(g, st, s, 1 - axis)})"
                step(
                    WHITE_PEARL, "Look at the white pearl $place.",
                    "A white pearl goes straight through. $reason, so it goes straight $run.",
                    setOf(s) + touched,
                )
            }
            PearlsLogic.K_WHITE_TURN -> {
                val far = f.crosses[0]
                val near = if (g.endA[far] == s || g.endB[far] == s) s else neighbourOn(g, s, far)
                val straight = (0 until 4).first { d ->
                    val q = g.next(s, d)
                    q >= 0 && q != near && st[g.edge(s, d)] == PearlsLogic.LINE && g.edge(q, d) >= 0 && st[g.edge(q, d)] == PearlsLogic.LINE
                }
                val q = g.next(s, straight)
                step(
                    WHITE_TURN, "The white pearl $place must turn on one side.",
                    "The loop turns just before or just after a white pearl. It runs straight on through ${squareName(n, q)}, " +
                        "so it turns at ${squareName(n, near)}: cross the edge straight on from there.",
                    setOf(s, near) + touched, setOf(s, q, near) + touched,
                )
            }
            PearlsLogic.K_ONE_WAY -> step(
                ONE_WAY, "Follow the line into the square $place.",
                "The line into the square $place has only one way on, so it continues there.",
                setOf(s) + touched, setOf(s) + edgesOf.map { edgeIndex(n, it) },
            )
            PearlsLogic.K_PEARL_WAYS -> step(
                PEARL_WAYS, "The pearl $place has only two ways in.",
                "The loop passes every pearl, and the pearl $place has only two ways in left, so both are lines.",
                setOf(s) + touched, setOf(s) + edgesOf.map { edgeIndex(n, it) },
            )
            else -> step(
                DEAD_END, "The square $place has only one way in.",
                "A loop passing the square $place would need two ways in, and it has only one left. So no line goes there: cross it.",
                setOf(s) + touched, setOf(s) + edgesOf.map { edgeIndex(n, it) },
            )
        }
    }

    private fun loopStep(g: PearlsGrid, st: IntArray, f: PearlsLogic.Found): Step {
        val n = g.n
        val e = f.crosses[0]
        val a = g.endA[e]
        val b = g.endB[e]
        // The stretch the edge would close, for the highlight.
        val s = PearlsLogic.stretches(g, st)
        val stretch = (0 until g.edgeCount).filter { st[it] == PearlsLogic.LINE && s.root[g.endA[it]] == s.root[a] }
        return Step(
            SMALL_LOOP,
            crosses = listOf(e),
            focus = setOf(a, b, edgeIndex(n, e)),
            cited = stretch.map { edgeIndex(n, it) }.toSet() + setOf(a, b),
            nudge = "Two ends of one line meet ${at(n, a)}.",
            explanation = "A line between ${squareName(n, a)} and ${squareName(n, b)} would close a loop that leaves out " +
                "a pearl or another line. There is only one loop, so cross it.",
        )
    }

    private fun whatIfStep(g: PearlsGrid, pearls: String, w: PearlsLogic.WhatIf): Step {
        val n = g.n
        val e = w.edge
        val line = w.assume == PearlsLogic.LINE
        val a = g.endA[e]
        val b = g.endB[e]
        val where = "between ${squareName(n, a)} and ${squareName(n, b)}"
        val c = w.contradiction.square
        val broke = when (w.contradiction.kind) {
            PearlsLogic.C_THREE -> "${squareName(n, c)} would need three lines"
            PearlsLogic.C_DEAD_LINE -> "the line would run into a dead end at ${squareName(n, c)}"
            PearlsLogic.C_PEARL_SHUT -> "the pearl at ${squareName(n, c)} would be left without two ways in"
            PearlsLogic.C_BLACK -> "the black pearl at ${squareName(n, c)} could not turn with a straight arm each way"
            PearlsLogic.C_WHITE -> "the white pearl at ${squareName(n, c)} could not go straight with a turn beside it"
            else -> "the line would close a loop that leaves something out"
        }
        val rounds = if (w.rounds <= 1) "" else ", following the rules ${w.rounds} steps on,"
        val suppose = if (line) "If there were a line $where" else "If the loop did not pass $where"
        return Step(
            WHAT_IF,
            lines = if (line) emptyList() else listOf(e),
            crosses = if (line) listOf(e) else emptyList(),
            focus = setOf(a, b, edgeIndex(n, e)),
            cited = setOf(a, b, c),
            nudge = "Suppose the loop ${if (line) "ran" else "did not run"} $where.",
            explanation = "$suppose$rounds $broke. So ${if (line) "it doesn't: cross it" else "it does: draw it"}.",
        )
    }

    // ---- why a pearl can't go a way ----------------------------------------------------------

    /** The sentence for one axis of a black pearl's step, or null when that axis settles nothing. */
    private fun blackAxis(g: PearlsGrid, pearls: String, st: IntArray, p: Int, axis: Int): String? {
        val a = axis
        val b = axis + 2
        fun has(d: Int) = g.edge(p, d) >= 0 && st[g.edge(p, d)] == PearlsLogic.LINE
        val arm = when {
            has(a) -> a
            has(b) -> b
            !PearlsLogic.blackArmOk(g, pearls, st, p, b) -> a
            !PearlsLogic.blackArmOk(g, pearls, st, p, a) -> b
            else -> return null
        }
        val back = (arm + 2) % 4
        val out = g.edge(p, arm)
        val on = g.edge(g.next(p, arm), arm)
        val backEdge = g.edge(p, back)
        val settles = st[out] == PearlsLogic.OPEN || st[on] == PearlsLogic.OPEN ||
            backEdge >= 0 && st[backEdge] == PearlsLogic.OPEN
        if (!settles) return null
        return if (has(arm)) {
            "Its line goes ${DIRS[arm]}, so it carries straight on and can't go ${DIRS[back]}."
        } else {
            "It can't go ${DIRS[back]} (${blackReason(g, pearls, st, p, back)}), so it goes ${DIRS[arm]} for two squares."
        }
    }

    private fun blackReason(g: PearlsGrid, pearls: String, st: IntArray, p: Int, d: Int): String {
        val out = g.edge(p, d)
        if (out < 0) return "the edge of the board"
        if (st[out] == PearlsLogic.CROSS) return "crossed"
        val q = g.next(p, d)
        if (pearls[q] == PearlsLogic.BLACK) return "the black pearl there would have to go straight"
        val on = g.edge(q, d)
        if (on < 0) return "no room to run on"
        if (st[on] == PearlsLogic.CROSS) return "it couldn't run on"
        return "the next square's line runs across"
    }

    private fun whiteReason(g: PearlsGrid, st: IntArray, p: Int, axis: Int): String {
        for (d in intArrayOf(axis, axis + 2)) {
            val e = g.edge(p, d)
            if (e < 0) return "the edge of the board"
            if (st[e] == PearlsLogic.CROSS) return "crossed"
        }
        for (d in intArrayOf((axis + 1) % 4, (axis + 3) % 4)) {
            val e = g.edge(p, d)
            if (e >= 0 && st[e] == PearlsLogic.LINE) return "a line already crosses that way"
        }
        return "straight on both sides would leave it no turn"
    }

    /** The square beside [s] at the far end of edge [e] (which belongs to that neighbour). */
    private fun neighbourOn(g: PearlsGrid, s: Int, e: Int): Int {
        for (d in 0 until 4) {
            val q = g.next(s, d)
            if (q >= 0 && (g.endA[e] == q || g.endB[e] == q)) return q
        }
        return s
    }

    private val DIRS = listOf("up", "right", "down", "left")

    // ---- words ------------------------------------------------------------------------------

    /** "at row 2, column 3". */
    private fun at(n: Int, s: Int) = "at row ${s / n + 1}, column ${s % n + 1}"

    /** "row 2, column 3". */
    private fun squareName(n: Int, s: Int) = "row ${s / n + 1}, column ${s % n + 1}"
}
