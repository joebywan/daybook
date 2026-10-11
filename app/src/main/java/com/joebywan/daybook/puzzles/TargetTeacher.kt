package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Deduction
import kotlin.math.abs

/**
 * Target, taught by searching what is on the table.
 *
 * Every board has many answers and the player may take any road, so there is no stored answer to read: [deduce] is handed
 * the tiles and the target and searches from there ([TargetLogic.table], exhaustive). A step is offered only when the
 * target is still reachable after it, and it is a step on a *shortest* road from where the player stands. The answer
 * is never consulted, so there is no fallback to measure: the teacher cannot point at a square it should not know.
 *
 * Which shortest road: one that makes the target itself if any ([FINISH]); else the step whose answer lies nearest the
 * target ([STEP]), because that is the one a person can see the point of. Ties go to the lowest slots and operator order,
 * so the hint is stable (and the same on the web).
 *
 * A mistake is a step after which *no* sequence of operations from the tiles left reaches the target, found by the same
 * table; the teacher names the first such step in the log and "Show me" takes it (and what followed) back. A step that
 * merely lengthens the road is not a mistake.
 *
 * Highlight indices: tiles are slots `0..5`, an operator is [Target.OP0] + its ordinal, the target is [Target.TARGET].
 */
internal object TargetTeacher {

    const val FINISH = "finish"
    const val STEP = "step"
    const val MISTAKE = "mistake"

    val TECHNIQUES = listOf(FINISH, STEP)

    const val MAX_NUDGE = 70
    const val MAX_EXPLANATION = 200

    class Step(
        val technique: String,
        /** The two slots (the answer goes in [a]) and the operator. -1 for a mistake. */
        val a: Int,
        val b: Int,
        val op: TargetOp?,
        val nudge: String,
        val explanation: String,
        val focus: Set<Int>,
        val cited: Set<Int>,
        val targets: Set<Int>,
        /** The tiles the board should show after the step, sorted; empty for a mistake. */
        val after: List<Int>,
    )

    /** Mistakes first, then a step on a shortest road. Null once solved (or if the position is lost with nothing to take back, which a board made here never is). */
    fun teach(s: TargetState): Step? {
        if (s.solved) return null
        mistake(s)?.let { return it }
        return deduce(s.slots, s.target)
    }

    /** The first step of the log after which the target can no longer be reached, as the log index, or null if it still can. */
    fun badStep(s: TargetState): Int? {
        if (TargetLogic.steps(s.tiles, s.target) != TargetLogic.NO) return null
        for (k in 1..s.log.size) {
            val prefix = s.withoutFrom(k)
            if (TargetLogic.steps(prefix.tiles, s.target) == TargetLogic.NO) return k - 1
        }
        return null
    }

    private fun mistake(s: TargetState): Step? {
        val i = badStep(s) ?: return null
        return Step(
            MISTAKE, -1, -1, null,
            "Check where you are heading.",
            "After ${s.log[i].text()}, no way of combining what is left reaches ${s.target}. Take that step back.",
            setOf(Target.TARGET), emptySet(), setOf(Target.TARGET), emptyList(),
        )
    }

    private class Move(val a: Int, val b: Int, val op: TargetOp, val result: Int, val left: Int, val right: Int, val after: List<Int>, val steps: Int)

    /** A step on a shortest road from [slots] to [target], reasoning from the tiles alone. Null when the target cannot be reached. */
    fun deduce(slots: List<Int>, target: Int): Step? {
        val tiles = slots.filter { it > 0 }
        val here = TargetLogic.steps(tiles, target)
        if (here <= 0) return null
        var best: Move? = null
        for (a in slots.indices) {
            if (slots[a] == 0) continue
            for (b in a + 1 until slots.size) {
                if (slots[b] == 0) continue
                for (op in TargetOp.entries) {
                    if ((op == TargetOp.Mul || op == TargetOp.Div) && minOf(slots[a], slots[b]) == 1) continue
                    val r = TargetLogic.combine(slots[a], slots[b], op) ?: continue
                    val rest = slots.filterIndexed { k, v -> v > 0 && k != a && k != b } + r
                    val left = if (r == target) 0 else TargetLogic.steps(rest, target)
                    if (left != here - 1) continue
                    val hi = maxOf(slots[a], slots[b])
                    val lo = minOf(slots[a], slots[b])
                    val cand = Move(a, b, op, r, if (op == TargetOp.Sub || op == TargetOp.Div) hi else slots[a], if (op == TargetOp.Sub || op == TargetOp.Div) lo else slots[b], rest.sorted(), left)
                    val cur = best
                    if (cur == null || closer(cand, cur, target)) best = cand
                }
            }
        }
        val m = best ?: return null
        return explain(m, target)
    }

    /** Finishing beats everything; then the nearer answer; ties keep the one found first. */
    private fun closer(x: Move, y: Move, target: Int): Boolean {
        val xf = x.result == target
        val yf = y.result == target
        if (xf != yf) return xf
        return abs(x.result - target) < abs(y.result - target)
    }

    private fun explain(m: Move, target: Int): Step {
        val sum = "${m.left} ${m.op.symbol} ${m.right}"
        val finish = m.result == target
        val gap = abs(m.result - target)
        val side = if (m.result < target) "below" else "above"
        val more = if (m.steps == 1) "one more step" else "${m.steps} more steps"
        return Step(
            if (finish) FINISH else STEP, m.a, m.b, m.op,
            if (finish) "Do two of these make the target?" else "Which two tiles belong together?",
            if (finish) "$sum is $target, the target. Tap ${m.left}, then ${m.op.symbol}, then ${m.right}."
            else "$sum makes ${m.result}, $gap $side the target, and from there $more reaches it. Tap ${m.left}, then ${m.op.symbol}, then ${m.right}.",
            setOf(m.a, m.b), setOf(Target.TARGET), setOf(m.a, m.b, Target.OP0 + m.op.ordinal), m.after,
        )
    }

    /** The [Deduction] for the play screen; `Target.teach` is just this. */
    fun deduction(s: TargetState): Deduction? {
        val step = teach(s) ?: return null
        if (step.technique == MISTAKE) {
            return Deduction(
                technique = MISTAKE, nudge = step.nudge, explanation = step.explanation,
                focus = step.focus, cited = step.cited, targets = step.targets, mistake = true,
                applyTo = { now -> (now as TargetState).let { n -> badStep(n)?.let { n.withoutFrom(it) } ?: n } },
                reachedBy = { now -> (now as TargetState).let { it.solved || TargetLogic.steps(it.tiles, it.target) != TargetLogic.NO } },
            )
        }
        val va = s.slots[step.a]
        val vb = s.slots[step.b]
        return Deduction(
            technique = step.technique, nudge = step.nudge, explanation = step.explanation,
            focus = step.focus, cited = step.cited, targets = step.targets,
            applyTo = { now ->
                val n = now as TargetState
                // The board may have moved on while the hint was open; if the same two tiles are still there use them, else reason afresh.
                val same = n.slots[step.a] == va && n.slots[step.b] == vb
                val again = if (same) step else deduce(n.slots, n.target)
                again?.let { n.combine(it.a, it.op!!, it.b) } ?: n
            },
            reachedBy = { now -> (now as TargetState).let { it.solved || it.tiles.sorted() == step.after } },
        )
    }
}
