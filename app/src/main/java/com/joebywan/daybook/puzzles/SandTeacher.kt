package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Deduction

/**
 * Sand, taught by searching what is on the board.
 *
 * Nothing is hidden: every layer is in sight and boards have many answers, so there is no stored answer to read and no
 * fallback in the usual sense. [deduce] is handed the tubes and the height and searches from there ([SandLogic.solve]).
 * A step is offered only when the board is still sortable after it ([SandLogic.Verdict.Solvable]), so a hint is always
 * on a real road.
 *
 * Steps, in ladder order: [FINISH] a pour that completes a colour; [GATHER] a pour onto a matching colour, uncovering a
 * colour with somewhere to go first; [SPARE] a pour into an empty tube that frees a buried colour; and [PLAN], the first
 * pour of the search's road, explained by what it makes way for. The first three are offered only when they make
 * progress: fewer places where two colours meet in a tube, else fewer tubes in use, else more tubes finished. No pour
 * ever adds a meeting place, so following hints cannot go round in circles through them; only [PLAN] steps are neutral.
 *
 * A mistake is a board no order of pours can sort, proved exhaustively ([SandLogic.Verdict.Unsolvable]); a search that
 * ran out of budget ([SandLogic.Verdict.Truncated]) is never called a mistake. The pour named is the one after the last
 * board in [SandState.log] that still sorts ([badPour]), and "Show me" takes it back with every pour after it. A board with
 * no log (made elsewhere) instead gets the sand of one pour that could have made it put back ([takeBack]). [STUCK] is the
 * same from a board with no pours left.
 *
 * Highlight indices: tube `i` is `i`. Every loop walks tubes in index order; no hash order reaches a choice.
 */
internal object SandTeacher {

    const val FINISH = "finish"
    const val GATHER = "gather"
    const val SPARE = "spare"
    const val PLAN = "plan"
    const val MISTAKE = "mistake"
    const val STUCK = "stuck"

    /** The search ran out of budget: not a mistake, said openly. Never seen in the measurements. */
    const val UNSURE = "unsure"

    val TECHNIQUES = listOf(FINISH, GATHER, SPARE, PLAN)

    const val MAX_NUDGE = 70
    const val MAX_EXPLANATION = 200

    /** Positions a hint's search may visit. Measured from player-like positions: the largest needed was in the hundreds. */
    const val HINT_BUDGET = 200_000

    /** How many pours back a mistake's "Show me" looks for a sortable board (one was always enough, measured). */
    private const val BACK_DEPTH = 3

    class Step(
        val technique: String,
        val nudge: String,
        val explanation: String,
        val focus: Set<Int>,
        val cited: Set<Int>,
        val targets: Set<Int>,
        /** The tubes after the step: the pour made, or the sand put back. */
        val after: List<List<Int>>,
        /** The pour, or -1s for a mistake (which puts sand back rather than pouring). */
        val from: Int = -1,
        val to: Int = -1,
        /** For a mistake: how much of the log to keep ([SandState.withoutFrom]), or -1 when the board had no log. */
        val keep: Int = -1,
    )

    fun teach(s: SandState): Step? {
        if (s.solved) return null
        return when (val v = SandLogic.solve(s.tubes, s.height, HINT_BUDGET)) {
            is SandLogic.Verdict.Solvable -> deduce(s.tubes, s.height, v.path)
            SandLogic.Verdict.Unsolvable -> back(s, if (s.stuck) STUCK else MISTAKE)
            SandLogic.Verdict.Truncated -> back(s, UNSURE)
        }
    }

    /** The colour's name and the tube's number, as the player reads them. */
    private fun name(c: Int) = Sand.colourNames[c]
    private fun tube(i: Int) = "tube ${i + 1}"
    private fun layers(k: Int, c: Int) = if (k == 1) "the ${name(c)}" else "the $k ${name(c)}"

    /** How far from sorted: (places two colours meet inside a tube, tubes in use, minus tubes finished). Lower is closer. */
    private fun progress(tubes: List<List<Int>>, height: Int): Triple<Int, Int, Int> {
        var meets = 0
        var used = 0
        var finished = 0
        for (t in tubes) {
            if (t.isEmpty()) continue
            used++
            for (i in 1 until t.size) if (t[i] != t[i - 1]) meets++
            if (SandLogic.finished(t, height)) finished++
        }
        return Triple(meets, used, -finished)
    }

    private fun closer(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>) =
        compareValuesBy(a, b, { it.first }, { it.second }, { it.third }) < 0

    /**
     * A step on a road to sorted, reasoning from the tubes alone. [road] is a sorting sequence from here (the search's),
     * used for [PLAN] when no simpler pour makes progress. Null only when the board is solved.
     */
    fun deduce(tubes: List<List<Int>>, height: Int, road: List<Pair<Int, Int>>): Step? {
        if (road.isEmpty()) return null
        val here = progress(tubes, height)
        val pours = SandLogic.pours(tubes, height)
        fun keeps(after: List<List<Int>>) = SandLogic.solve(after, height, HINT_BUDGET) is SandLogic.Verdict.Solvable
        fun good(m: Pair<Int, Int>, after: List<List<Int>>) = closer(progress(after, height), here) && keeps(after)

        // 1. Finish a tube.
        for (m in pours) {
            val after = SandLogic.pour(tubes, height, m.first, m.second)
            if (SandLogic.finished(after[m.second], height) && good(m, after)) return finish(tubes, height, m, after)
        }
        // 2. Gather: onto a matching colour, uncovering a colour with somewhere to go first.
        val gathers = pours.filter { tubes[it.second].isNotEmpty() }
        val homes = gathers.filter { m -> home(SandLogic.pour(tubes, height, m.first, m.second), m.first, height) != null }
        for (m in homes + (gathers - homes.toSet())) {
            val after = SandLogic.pour(tubes, height, m.first, m.second)
            if (good(m, after)) return gather(tubes, height, m, after)
        }
        // 3. Use a spare to free a buried colour (a tube of one colour poured into an empty one frees nothing).
        for (m in pours) {
            val (a, b) = m
            if (tubes[b].isNotEmpty() || SandLogic.topRun(tubes[a]) == tubes[a].size) continue
            val after = SandLogic.pour(tubes, height, a, b)
            if (good(m, after)) return spare(tubes, height, m, after)
        }
        // 4. Plan ahead: the search's road.
        val m = road.first()
        return plan(tubes, height, m, road.getOrNull(1))
    }

    /** Where the colour now on top of [from] could go in [after]: a matching top with room, else an empty tube, else null. */
    private fun home(after: List<List<Int>>, from: Int, height: Int): Int? {
        val src = after[from]
        if (src.isEmpty()) return null
        val match = after.indices.firstOrNull { it != from && after[it].isNotEmpty() && after[it].size < height && after[it].last() == src.last() }
        return match ?: after.indices.firstOrNull { it != from && after[it].isEmpty() }
    }

    private fun moved(tubes: List<List<Int>>, height: Int, m: Pair<Int, Int>) = SandLogic.pourSize(tubes, height, m.first, m.second)

    private fun finish(tubes: List<List<Int>>, height: Int, m: Pair<Int, Int>, after: List<List<Int>>): Step {
        val (a, b) = m
        val c = tubes[a].last()
        return Step(
            FINISH,
            "One tube is a pour away from finished.",
            "Pour ${tube(a)} onto ${tube(b)}: ${layers(moved(tubes, height, m), c)} fill it, and ${tube(b)} is all ${name(c)}.",
            setOf(a), setOf(b), setOf(a, b), after, a, b,
        )
    }

    private fun gather(tubes: List<List<Int>>, height: Int, m: Pair<Int, Int>, after: List<List<Int>>): Step {
        val (a, b) = m
        val c = tubes[a].last()
        val k = moved(tubes, height, m)
        val src = after[a]
        val then = when {
            src.isEmpty() -> "That empties ${tube(a)}."
            else -> {
                val h = home(after, a, height)
                when {
                    h == null -> "That keeps the ${name(c)} together."
                    after[h].isEmpty() -> "That uncovers ${name(src.last())}, which can go in the empty ${tube(h)}."
                    else -> "That uncovers ${name(src.last())}, which can go on ${tube(h)}."
                }
            }
        }
        return Step(
            GATHER,
            "Two tubes have the same colour on top.",
            "Pour ${layers(k, c)} from ${tube(a)} onto ${tube(b)}. $then",
            setOf(a), setOf(b), setOf(a, b), after, a, b,
        )
    }

    private fun spare(tubes: List<List<Int>>, height: Int, m: Pair<Int, Int>, after: List<List<Int>>): Step {
        val (a, b) = m
        val c = tubes[a].last()
        val freed = after[a].last()
        return Step(
            SPARE,
            "An empty tube can free a buried colour.",
            "Pour ${layers(moved(tubes, height, m), c)} from ${tube(a)} into the empty ${tube(b)}. That frees the ${name(freed)} under it.",
            setOf(a), setOf(b), setOf(a, b), after, a, b,
        )
    }

    private fun plan(tubes: List<List<Int>>, height: Int, m: Pair<Int, Int>, next: Pair<Int, Int>?): Step {
        val (a, b) = m
        val c = tubes[a].last()
        val after = SandLogic.pour(tubes, height, a, b)
        val why = if (next == null) "and the board is sorted."
        else "It clears the way for pouring ${tube(next.first)} onto ${tube(next.second)}."
        return Step(
            PLAN,
            "Think a pour or two ahead.",
            "Pour ${layers(moved(tubes, height, m), c)} from ${tube(a)} onto ${tube(b)}. $why",
            setOf(a), next?.let { setOf(it.first, it.second) - setOf(a, b) } ?: emptySet(), setOf(a, b), after, a, b,
        )
    }

    /**
     * The sand of one pour put back, leaving a board [SandLogic.solve] proves sortable, nearest first (breadth first, at
     * most [BACK_DEPTH] pours back), for a board with no log. Several boards could have come before it (a median of 14
     * that sort, measured), so this is a guess: of those that sort, the one nearest sorted ([progress]), then the first in
     * index order. Null when none is found within that.
     */
    fun takeBack(s: SandState): Pair<List<List<Int>>, List<Triple<List<List<Int>>, Int, Int>>>? {
        var frontier = listOf(s.tubes to emptyList<Triple<List<List<Int>>, Int, Int>>())
        val seen = mutableListOf(s.tubes)
        repeat(BACK_DEPTH) {
            val next = mutableListOf<Pair<List<List<Int>>, List<Triple<List<List<Int>>, Int, Int>>>>()
            var best: Pair<List<List<Int>>, List<Triple<List<List<Int>>, Int, Int>>>? = null
            for ((t, chain) in frontier) for (u in SandLogic.unpours(t, s.height)) {
                if (u.first in seen) continue
                seen += u.first
                val line = chain + u
                next += u.first to line
                if (SandLogic.solve(u.first, s.height, HINT_BUDGET) !is SandLogic.Verdict.Solvable) continue
                val b = best
                if (b == null || closer(progress(u.first, s.height), progress(b.first, s.height))) best = u.first to line
            }
            best?.let { return it }
            frontier = next
        }
        return null
    }

    /** The log index of the pour after which the board stopped being sortable (the latest board before it that sorts), or null. */
    fun badPour(s: SandState): Int? {
        for (j in s.log.indices.reversed()) {
            if (SandLogic.solve(s.withoutFrom(j).tubes, s.height, HINT_BUDGET) is SandLogic.Verdict.Solvable) return j
        }
        return null
    }

    private fun back(s: SandState, technique: String): Step? {
        val (nudge, why) = when (technique) {
            STUCK -> "No pours left." to "No pour is possible, and the board is not sorted."
            UNSURE -> "There's no quick way through from here." to "There's no quick way through from here. Try undoing a few pours."
            else -> "This board can no longer be sorted." to "No order of pours sorts this board now."
        }
        badPour(s)?.let { j ->
            val p = s.log[j]
            val after = s.log.size - 1 - j
            val then = when (after) {
                0 -> "Take that pour back."
                1 -> "Take it back, and the pour after it."
                else -> "Take it back, and the $after pours after it."
            }
            val before = s.withoutFrom(j)
            val c = before.tubes[p.from].last()
            return Step(
                technique, nudge,
                "$why It went wrong when ${layers(p.count, c)} went from ${tube(p.from)} onto ${tube(p.to)}. $then",
                setOf(p.to), setOf(p.from), setOf(p.from, p.to), before.tubes, keep = j,
            )
        }
        // A board with no pours recorded (made elsewhere): put back the sand of one pour that could have made it.
        val (before, line) = takeBack(s) ?: return null
        val (_, from, to) = line.first()
        val k = s.tubes[to].size - line.first().first[to].size
        val c = s.tubes[to].last()
        val more = if (line.size > 1) " and ${line.size - 1} more" else ""
        val putBack = "Put ${layers(k, c)} on ${tube(to)} back in ${tube(from)}$more, and it can be sorted again."
        return Step(technique, nudge, "$why $putBack", setOf(to), setOf(from), setOf(from, to), before)
    }

    /** Canonical form for "has the player made this pour": tubes are interchangeable, so an equal pour into another empty tube counts. */
    private fun canonical(t: List<List<Int>>) = t.sortedWith { x, y ->
        for (i in 0 until minOf(x.size, y.size)) if (x[i] != y[i]) return@sortedWith x[i] - y[i]
        x.size - y.size
    }

    /** The [Deduction] for the play screen; `Sand.teach` is just this. */
    fun deduction(s: SandState): Deduction? {
        val step = teach(s) ?: return null
        val back = step.from < 0
        val target = canonical(step.after)
        return Deduction(
            technique = step.technique, nudge = step.nudge, explanation = step.explanation,
            focus = step.focus, cited = step.cited, targets = step.targets,
            mistake = step.technique == MISTAKE || step.technique == STUCK,
            applyTo = { now ->
                val n = now as SandState
                when {
                    back && n.tubes == s.tubes && step.keep >= 0 && n.log.size > step.keep -> n.withoutFrom(step.keep)
                    back && n.tubes == s.tubes -> n.copy(tubes = step.after, moves = n.moves + 1)
                    !back && n.tubes == s.tubes -> n.pour(step.from, step.to) ?: n
                    // The board moved on while the hint was open: reason afresh.
                    else -> deduction(n)?.apply(n) ?: n
                }
            },
            reachedBy = { now ->
                val n = now as SandState
                if (back) n.solved || SandLogic.solve(n.tubes, n.height, HINT_BUDGET) is SandLogic.Verdict.Solvable
                else n.solved || canonical(n.tubes) == target
            },
        )
    }
}
