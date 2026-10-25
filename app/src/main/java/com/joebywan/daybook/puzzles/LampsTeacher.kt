package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.Deduction
import com.joebywan.daybook.puzzles.LampsLogic.Fact
import com.joebywan.daybook.puzzles.LampsLogic.LAMP
import com.joebywan.daybook.puzzles.LampsLogic.NO

/**
 * Lamps, reasoned a fact at a time: exactly [LampsLogic.nextFact]'s ladder, which is also what the generator ships a
 * board only when it finishes, so a step always exists on a board whose lamps are right.
 *
 * [deduce] is handed what the player can see (the walls, the numbers, their marks) and nothing else, so a step cannot
 * lean on the stored answer. The answer is read only in [teach]: to find a lamp no answer keeps (sound because a shipped
 * board has one answer) and for [FALLBACK], which says openly that it points at the answer.
 *
 * Knowledge starts from the player's lamps (once none is a mistake); crosses are notes and are never read. A fact the
 * board already shows (every square it rules out crossed) is skipped, so the hint is the first thing the player has not
 * yet put down. Every loop walks squares in index order, so a board gets the same hint on every platform.
 *
 * Highlight indices are squares, `row * size + col`; a number is cited by its wall's square.
 */
internal object LampsTeacher {

    const val ZERO = LampsLogic.ZERO
    const val DONE = LampsLogic.DONE
    const val ROOM = LampsLogic.ROOM
    const val ONLY_LIGHT = LampsLogic.ONLY_LIGHT
    const val STARVE = LampsLogic.STARVE
    const val DARK = LampsLogic.DARK
    const val WHAT_IF = LampsLogic.WHAT_IF
    const val FALLBACK = "fallback"
    const val MISTAKE = "mistake"

    val TECHNIQUES = listOf(ZERO, DONE, ROOM, ONLY_LIGHT, STARVE, DARK, WHAT_IF, FALLBACK)

    const val MAX_NUDGE = 70

    /** More than most teachers: a what-if names its chain, and the popover scrolls past its room (Cipher's precedent). */
    const val MAX_EXPLANATION = 260

    class Step(
        val technique: String,
        /** What the targets become: [LAMP], [NO] (a cross), or 0 to take a lamp away (a mistake). */
        val value: Int,
        val targets: Set<Int>,
        val focus: Set<Int>,
        val cited: Set<Int>,
        val nudge: String,
        val explanation: String,
    )

    fun teach(s: LampsState): Step? {
        if (s.solved) return null
        mistake(s)?.let { return it }
        deduce(s.size, s.walls, s.marks)?.let { return it }
        val i = s.solution.indices.firstOrNull { s.solution[it] == '1' && s.marks[it] != LampsLogic.LAMP_MARK } ?: return null
        return Step(FALLBACK, LAMP, setOf(i), setOf(i), emptySet(), "Try this square.", "No short reason found: the stored answer has a lamp here.")
    }

    /** The [Deduction] for the play screen; `Lamps.teach` is just this. */
    fun deduction(s: LampsState): Deduction? {
        val step = teach(s) ?: return null
        val mistake = step.technique == MISTAKE
        return Deduction(
            technique = step.technique,
            nudge = step.nudge,
            explanation = step.explanation,
            focus = step.focus,
            cited = step.cited,
            targets = step.targets,
            mistake = mistake,
            fallback = step.technique == FALLBACK,
            applyTo = { now ->
                step.targets.fold(now as LampsState) { acc, i ->
                    when (step.value) {
                        LAMP -> acc.withMark(i, LampsLogic.LAMP_MARK)
                        NO -> if (acc.marks[i] == LampsLogic.LAMP_MARK) acc else acc.withMark(i, LampsLogic.CROSS)
                        else -> acc.withMark(i, LampsLogic.NONE)
                    }
                }
            },
            reachedBy = { now ->
                val t = now as LampsState
                val lit by lazy { t.lit() }
                step.targets.all {
                    when (step.value) {
                        LAMP -> t.marks[it] == LampsLogic.LAMP_MARK
                        NO -> t.marks[it] == LampsLogic.CROSS || (t.marks[it] == LampsLogic.NONE && lit[it])
                        else -> t.marks[it] != LampsLogic.LAMP_MARK
                    }
                }
            },
        )
    }

    // ---- mistakes -------------------------------------------------------------------------------

    private fun mistake(s: LampsState): Step? {
        val n = s.size
        val wrong = s.marks.indices.firstOrNull { s.marks[it] == LampsLogic.LAMP_MARK && s.solution[it] != '1' } ?: return null
        fun step(why: String, cited: Set<Int>) =
            Step(MISTAKE, 0, setOf(wrong), setOf(wrong), cited, "Check this lamp.", "$why Tap it to take it away.")
        val sees = LampsLogic.sight(n, s.walls, wrong).filter { s.marks[it] == LampsLogic.LAMP_MARK }
        if (sees.isNotEmpty()) return step("This lamp lights another lamp in its row or column.", sees.toSet())
        val over = LampsLogic.orth(n, wrong).firstOrNull { s.numberAt(it) >= 0 && s.lampsBeside(it) > s.numberAt(it) }
        if (over != null) return step("The ${s.numberAt(over)} beside it would have too many lamps.", setOf(over))
        return step("No way to finish the board keeps a lamp here.", emptySet())
    }

    // ---- reasoning ------------------------------------------------------------------------------

    /** The first fact the ladder gives that the board does not already show, or null when it has no more. */
    fun deduce(n: Int, walls: String, marks: String): Step? {
        val g = LampsLogic.Geo(n, walls)
        val k = IntArray(n * n) { if (g.white[it] && marks[it] == LampsLogic.LAMP_MARK) LAMP else LampsLogic.OPEN }
        while (true) {
            val f = LampsLogic.nextFact(g, k, LampsLogic.WHAT_IF_RUNG) ?: return null
            LampsLogic.apply(k, f)
            val shown = f.value == NO && f.targets.all { marks[it] == LampsLogic.CROSS }
            if (!shown) return explain(g, f)
        }
    }

    private fun plural(k: Int, one: String, many: String = one + "s") = if (k == 1) "1 $one" else "$k $many"

    private fun its(num: Int) = when (num) {
        1 -> "its lamp"
        2 -> "both its lamps"
        else -> "all $num of its lamps"
    }

    private fun explain(g: LampsLogic.Geo, f: Fact): Step {
        val t = f.targets.toSet()
        val num = if (f.wall >= 0) g.number[f.wall] else 0
        return when (f.technique) {
            ZERO -> Step(
                ZERO, NO, t, t + f.wall, setOf(f.wall),
                "Look beside the 0.",
                "A 0 has no lamps beside it, so none of the squares next to it can hold one: cross them out.",
            )
            DONE -> Step(
                DONE, NO, t, t + f.wall, setOf(f.wall) + g.beside[f.wall].filter { it !in t },
                "Count the lamps around this $num.",
                "The $num already has ${its(num)}, so the other squares beside it hold none: cross them out.",
            )
            ROOM -> Step(
                ROOM, LAMP, t, t + f.wall, setOf(f.wall),
                "How many squares beside this $num can still take a lamp?",
                "The $num needs ${plural(t.size, if (num > t.size) "more lamp" else "lamp")}, and only ${if (t.size == 1) "1 square" else "${t.size} squares"} " +
                    "beside it can still take one (the others are walls, lamps, lit or ruled out), so ${if (t.size == 1) "it is a lamp" else "they are all lamps"}.",
            )
            ONLY_LIGHT -> if (f.targets[0] == f.seen) Step(
                ONLY_LIGHT, LAMP, t, t, emptySet(),
                "How can this square be lit?",
                "No other square can light this one: everything in its row and column is lit, ruled out or behind a wall. " +
                    "So it holds its own lamp.",
            ) else Step(
                ONLY_LIGHT, LAMP, t, t + f.seen, setOf(f.seen),
                "How can the marked square be lit?",
                "The marked square still needs light, and only one square can still give it: the glowing one. That one is a lamp.",
            )
            STARVE -> Step(
                STARVE, NO, t, t + f.wall + f.lit, setOf(f.wall) + f.lit,
                "What would a lamp here do to the $num?",
                "A lamp here would light ${if (f.lit.size == 1) "a square" else "${f.lit.size} squares"} beside the $num, leaving it too few " +
                    "places for its ${plural(num, "lamp")}. So no lamp here: cross it out.",
            )
            DARK -> Step(
                DARK, NO, t, t + f.seen + f.lit, setOf(f.seen) + f.lit,
                "What would a lamp here leave in the dark?",
                "A lamp here would light every square that could light the marked one, without lighting it, so nothing could " +
                    "ever light it. So no lamp here: cross it out.",
            )
            else -> whatIf(g, f)
        }
    }

    private fun whatIf(g: LampsLogic.Geo, f: Fact): Step {
        val w = f.whatIf!!
        val lamp = w.assume == LAMP
        val cited = mutableSetOf<Int>()
        val chain = w.links.map { link ->
            cited += link.targets
            if (link.wall >= 0) cited += link.wall
            if (link.seen >= 0) cited += link.seen
            val num = if (link.wall >= 0) g.number[link.wall] else 0
            when (link.technique) {
                ROOM -> "the $num would need ${if (link.targets.size == 1) "its last free square" else "its last ${link.targets.size} squares"}"
                ONLY_LIGHT -> "a square would have one way left to be lit"
                else -> "the $num would have ${its(num)}"
            }
        }
        cited += w.broken.at
        val brokenNum = g.number[w.broken.at]
        val end = when (w.broken.kind) {
            LampsLogic.OVER -> "the $brokenNum would have too many lamps"
            LampsLogic.SHORT -> "the $brokenNum could not get its ${plural(brokenNum, "lamp")}"
            else -> "a square could never be lit"
        }
        val start = if (lamp) "Suppose a lamp here." else "Suppose no lamp here."
        val then = (chain + end).let { parts ->
            if (parts.size == 1) parts[0] else parts.dropLast(1).joinToString(", then ") + ", and " + parts.last()
        }
        val verdict = if (lamp) "So no lamp here: cross it out." else "So it holds a lamp."
        return Step(
            WHAT_IF, f.value, setOf(w.square), setOf(w.square), cited - w.square,
            if (lamp) "What if this square held a lamp?" else "What if this square had no lamp?",
            "$start Then $then. $verdict",
        )
    }
}
