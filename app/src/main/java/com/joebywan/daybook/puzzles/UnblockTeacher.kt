package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.core.SearchVerdict
import com.joebywan.daybook.core.TeachStep

/**
 * Unblock, taught by searching what is on the board.
 *
 * Every car is in sight and boards have many answers, so there is no stored answer to read and no fallback: [deduce] is
 * handed the cars and searches from there, breadth first ([UnblockLogic.solve]), so the move it names is the first of a
 * shortest way out from where the player stands (never the generator's road, never [UnblockState.par]). A slide can
 * always be slid back, so no move is ever a mistake; one that lengthens the way is just a longer way.
 *
 * The move is named by what it frees, read off the rest of that shortest road: the squares the car leaves, and the first
 * later slide that passes over one of them. Steps, by what that is: [EXIT] the red car drives out; [CLEAR] the car is in
 * the red car's way; [MAKE_ROOM] it makes room for another car; [PLAN] nothing later needs its squares (measured: never on
 * a shortest road, kept so a hint is never empty).
 *
 * A search that ran out of budget ([SearchVerdict.Truncated]) is said openly ([UNSURE]); it cannot happen on a board made
 * here, whose whole space is smaller than the budget ([UnblockLogic.SPACE_CAP]).
 *
 * Highlight indices are squares, `row * SIZE + col`. Every loop walks cars and squares in index order.
 */
internal object UnblockTeacher {

    const val EXIT = "exit"
    const val CLEAR = "clear-the-way"
    const val MAKE_ROOM = "make-room"
    const val PLAN = "plan"

    /** The search ran out of budget: said openly, never a mistake. */
    const val UNSURE = "unsure"

    val TECHNIQUES = listOf(EXIT, CLEAR, MAKE_ROOM, PLAN)

    const val MAX_NUDGE = 70
    const val MAX_EXPLANATION = 200

    class Step(
        override val technique: String,
        override val nudge: String,
        override val explanation: String,
        override val focus: Set<Int>,
        override val cited: Set<Int>,
        override val targets: Set<Int>,
        /** The slide, or null when nothing could be said ([UNSURE]). */
        val move: UnblockMove?,
        /** Moves left on the shortest way, this one included. */
        val left: Int,
    ) : TeachStep

    fun teach(s: UnblockState): Step? = if (s.solved) null else deduce(s.cars)

    /** A step on a shortest way out, reasoning from the cars alone. Null only when the board is solved. */
    fun deduce(cars: List<UnblockCar>): Step? {
        if (UnblockLogic.isSolved(cars)) return null
        return when (val v = UnblockLogic.solve(cars)) {
            is SearchVerdict.Solvable -> explain(cars, v.path)
            else -> Step(
                UNSURE, "There's no quick way through from here.",
                "There's no quick way through from here. Try sliding a few cars back.",
                emptySet(), emptySet(), emptySet(), null, 0,
            )
        }
    }

    /** What car [i] is called: "the red car", or its colour, kind and line ("the teal truck in column 5"). */
    fun name(cars: List<UnblockCar>, i: Int): String {
        if (i == 0) return "the red car"
        val c = cars[i]
        val kind = if (c.length == 3) "truck" else "car"
        val line = if (c.across) "row ${c.row + 1}" else "column ${c.col + 1}"
        return "the ${Unblock.hueNames[Unblock.hueOf(cars, i)]} $kind in $line"
    }

    private fun way(c: UnblockCar, by: Int): String {
        val dir = if (c.across) (if (by < 0) "left" else "right") else (if (by < 0) "up" else "down")
        val n = kotlin.math.abs(by)
        return "$dir ${if (n == 1) "one square" else "$n squares"}"
    }

    /** The squares car [i] passes over or lands on in a slide of [by], not counting those it already held. */
    private fun swept(c: UnblockCar, by: Int): Set<Int> {
        val lo = minOf(0, by)
        val hi = maxOf(0, by) + c.length - 1
        return (lo..hi).map { k ->
            if (c.across) c.row * UnblockLogic.SIZE + c.col + k else (c.row + k) * UnblockLogic.SIZE + c.col
        }.toSet() - c.cells().toSet()
    }

    private fun explain(cars: List<UnblockCar>, road: List<UnblockMove>): Step {
        val m = road.first()
        val car = cars[m.car]
        val after = UnblockLogic.slide(cars, m.car, m.by)
        val left = road.size
        val focus = car.cells().toSet()
        val targets = after[m.car].cells().toSet()
        val slide = "Slide ${name(cars, m.car)} ${way(car, m.by)}"
        val more = if (left == 1) "" else " From here the red car is out in $left moves."
        if (UnblockLogic.isSolved(after)) return Step(
            EXIT, "The red car's way out is clear.",
            "Slide the red car ${way(car, m.by)}: nothing is between it and the exit.",
            focus, emptySet(), targets, m, left,
        )
        // Who uses the squares this slide leaves: the first later slide that passes over one.
        val vacated = focus - targets
        var now = after
        for (k in 1 until road.size) {
            val next = road[k]
            if (next.car != m.car && swept(now[next.car], next.by).any { it in vacated }) {
                val them = now[next.car]
                return if (next.car == 0) Step(
                    CLEAR, "Something is in the red car's way.",
                    "$slide: it is in the red car's way to the exit.$more",
                    focus, them.cells().toSet(), targets, m, left,
                ) else Step(
                    MAKE_ROOM, "Make room before the way can clear.",
                    "$slide: that makes room for ${name(now, next.car)} to slide ${way(them, next.by).substringBefore(' ')}.$more",
                    focus, them.cells().toSet(), targets, m, left,
                )
            }
            now = UnblockLogic.slide(now, next.car, next.by)
        }
        return Step(PLAN, "Think a few moves ahead.", "$slide: it's the first move of the shortest way out.$more", focus, emptySet(), targets, m, left)
    }

    /** The [Deduction] for the play screen; `Unblock.teach` is just this. */
    fun deduction(s: UnblockState): Deduction? {
        val step = teach(s) ?: return null
        val m = step.move
        val target = m?.let { UnblockLogic.slide(s.cars, it.car, it.by)[it.car] }
        return Deduction(
            step,
            applyTo = { now ->
                val n = now as UnblockState
                when {
                    m == null -> n
                    n.cars == s.cars -> n.slide(m.car, m.by) ?: n
                    // The board moved on while the hint was open: reason afresh.
                    else -> deduction(n)?.apply(n) ?: n
                }
            },
            reachedBy = { now ->
                val n = now as UnblockState
                n.solved || (m != null && target != null && n.cars[m.car] == target)
            },
        )
    }
}
