package com.joebywan.daybook.puzzles

import com.joebywan.daybook.core.TeachStep
import com.joebywan.daybook.core.cap
import com.joebywan.daybook.core.fit
import com.joebywan.daybook.core.lineName
import com.joebywan.daybook.core.listed

/**
 * Thermometers, reasoned the way a person reasons it: one thermometer or one line at a time, and on Expert one
 * supposition followed a few lines further.
 *
 * Every board Daybook deals is finished by exactly this ladder ([ThermometersLogic.accepts]), so a step always exists
 * on a board whose marks are right, and there is no fallback to measure.
 *
 * [deduce] is handed the board as the player sees it (the tubes, the counts and the marks) and has no parameter
 * through which the answer could arrive. The answer is read in one place, [mistake], to decide which of the player's
 * marks cannot be right; sound because every shipped board has exactly one answer.
 *
 * Techniques, simplest first ([ThermometersLogic] names the rungs):
 * 1. [TUBE]: a thermometer fills from its bulb with no gap, so below a filled square everything fills, and above an
 *    empty one everything stays empty.
 * 2. [DONE]: a line that already holds its count has every other square empty.
 * 3. [ROOM]: a line with exactly as many open squares as it still needs fills them all.
 * 4. [AT_MOST]: a line needing k more takes at most k from one thermometer, so that thermometer's next open square
 *    there and everything above it stay empty (a tip that cannot fill).
 * 5. [AT_LEAST]: when the other thermometers' open squares in a line fall j short of what it needs, this one gives at
 *    least j there, so it fills from the bulb to its j-th open square (a bulb that must fill).
 * 6. [WHAT_IF]: suppose a square filled (or empty), follow at most [ThermometersLogic.MAX_LINKS] lines, and some line
 *    can no longer hold its count, so the square is the other way. Only Expert boards need it, and every one does.
 *
 * Within a technique the first thermometer (by its bulb) or the first line wins, rows top to bottom and then columns
 * left to right, so a board gets the same hint on every platform. No hash container is walked.
 *
 * ## Highlight indices
 * Squares are `row * size + column`. Row `r`'s count is `size * size + r`, column `c`'s is `size * size + size + c`
 * ([ThermometersLogic.rowCountIndex], [ThermometersLogic.colCountIndex]).
 */
internal object ThermometersTeacher {

    const val TUBE = "from-the-bulb"
    const val DONE = "line-done"
    const val ROOM = "line-room"
    const val AT_MOST = "tip-cannot"
    const val AT_LEAST = "bulb-must"
    const val WHAT_IF = "what-if"
    const val MISTAKE = "mistake"

    /** Every technique in the order [deduce] prefers them, for reports. */
    val TECHNIQUES = listOf(TUBE, DONE, ROOM, AT_MOST, AT_LEAST, WHAT_IF)

    /** What the hint panel has room for (the test pins it). */
    const val MAX_NUDGE = 70
    const val MAX_EXPLANATION = 200

    /** One step: what to fill, cross or clear, where to look, and why. [clears] is only ever a mistake. */
    class Step(
        override val technique: String,
        val full: List<Int> = emptyList(),
        val empty: List<Int> = emptyList(),
        val clears: List<Int> = emptyList(),
        override val focus: Set<Int>,
        override val cited: Set<Int>,
        override val nudge: String,
        override val explanation: String,
    ) : TeachStep {
        override val targets: Set<Int> get() = (full + empty + clears).toSet()
    }

    fun teach(s: ThermometersState): Step? {
        if (s.solved) return null
        mistake(s)?.let { return it }
        return deduce(s.size, s.tubes, ThermometersLogic.targets(s), s.marks)
    }

    // ---- mistakes -------------------------------------------------------------------------

    /** The first mark, in reading order, that the board's one answer disagrees with. */
    private fun mistake(s: ThermometersState): Step? {
        val n = s.size
        for (i in s.marks.indices) {
            val filledWrongly = s.marks[i] == ThermometersLogic.FILLED && s.fill[i] == '0'
            val crossedWrongly = s.marks[i] == ThermometersLogic.CROSSED && s.fill[i] == '1'
            if (!filledWrongly && !crossedWrongly) continue
            return Step(
                technique = MISTAKE,
                clears = listOf(i),
                focus = setOf(i),
                cited = setOf(ThermometersLogic.rowCountIndex(n, i / n), ThermometersLogic.colCountIndex(n, i % n)),
                nudge = "One of your marks doesn't fit the counts.",
                explanation = if (filledWrongly) {
                    "The glowing square can't be filled: no way of meeting every count fills it. Take it back."
                } else {
                    "The glowing square has to be filled: no way of meeting every count leaves it empty. Take the cross off."
                },
            )
        }
        return null
    }

    // ---- reasoning ------------------------------------------------------------------------

    /**
     * The next step reasoned from the [tubes], the [targets] (rows then columns) and the [marks] alone, or null when
     * nothing can be settled (a finished board, or one with a wrong mark).
     */
    fun deduce(n: Int, tubes: List<List<Int>>, targets: List<Int>, marks: String): Step? {
        val l = ThermometersLogic.Layout(n, tubes)
        val grid = ThermometersLogic.markState(marks)
        if (ThermometersLogic.broken(l, grid, targets)) return null
        val fact = ThermometersLogic.nextFact(l, grid, targets, ThermometersLogic.AT_LEAST)
        if (fact != null) return factStep(l, targets, grid, fact)
        val w = ThermometersLogic.firstWhatIf(l, targets, grid) ?: return null
        return whatIfStep(l, targets, grid, w)
    }

    private fun factStep(l: ThermometersLogic.Layout, targets: List<Int>, grid: IntArray, f: ThermometersLogic.Fact): Step {
        val n = l.n
        val squares = (f.full + f.empty).toSet()
        if (f.kind == ThermometersLogic.TUBE) {
            val cited = l.tubes[f.tube].filter { grid[it] != ThermometersLogic.OPEN }.toSet()
            return if (f.full.isNotEmpty()) {
                Step(
                    TUBE, f.full, f.empty, focus = squares, cited = cited,
                    nudge = "A thermometer has a gap under a filled square.",
                    explanation = "A thermometer fills from its bulb with no gaps, so everything under its filled square is filled too. " +
                        "Fill the glowing squares" + (if (f.empty.isNotEmpty()) " and cross out the ones above its cross." else "."),
                )
            } else {
                Step(
                    TUBE, f.full, f.empty, focus = squares, cited = cited,
                    nudge = "A thermometer has open squares above an empty one.",
                    explanation = "A thermometer fills from its bulb with no gaps, so nothing above an empty square can fill. " +
                        "Cross out the glowing squares.",
                )
            }
        }
        val line = f.line
        val name = lineName(n, line)
        val cap = name.cap()
        val at = ThermometersLogic.lineCells(n, line)
        val count = targets[line]
        val filled = at.count { grid[it] == ThermometersLogic.FULL }
        val open = at.count { grid[it] == ThermometersLogic.OPEN }
        val need = count - filled
        val lineFocus = at.toSet() + (n * n + line)
        fun step(technique: String, nudge: String, why: String, focus: Set<Int> = lineFocus, cited: Set<Int> = lineFocus) =
            Step(technique, f.full, f.empty, focus = focus, cited = cited, nudge = nudge, explanation = why)
        return when (f.kind) {
            ThermometersLogic.DONE -> step(
                DONE, "$cap already has its $count.",
                if (count == 0) "$cap counts 0, so nothing in it is filled. Cross out the glowing squares."
                else "$cap needs $count filled and already has them, so every other square there stays empty. Cross out the glowing squares.",
            )
            ThermometersLogic.ROOM -> step(
                ROOM, "Count the open squares in $name.",
                "$cap needs $need more filled and has exactly $open open ${if (open == 1) "square" else "squares"}, so all of them fill.",
            )
            ThermometersLogic.AT_MOST -> {
                val tubeSquares = l.tubes[f.tube].filter { it in at && grid[it] == ThermometersLogic.OPEN }
                step(
                    AT_MOST, "$cap can't take all of one thermometer.",
                    fit(
                        listOf(
                            "$cap needs only $need more. The glowing thermometer fills from its bulb, so it can give $name at most " +
                                "$need: its next square there, and everything above it, stay empty. Cross them out.",
                            "$cap needs only $need more, so the glowing thermometer gives it at most $need: its next square there, " +
                                "and all above, stay empty.",
                        ),
                        MAX_EXPLANATION,
                    ),
                    focus = (f.empty + tubeSquares).toSet(),
                    cited = lineFocus + l.tubes[f.tube],
                )
            }
            else -> {
                val others = open - l.tubes[f.tube].count { it in at && grid[it] == ThermometersLogic.OPEN }
                val must = need - others
                step(
                    AT_LEAST, "$cap needs more than the other thermometers can give.",
                    fit(
                        listOf(
                            "$cap needs $need more, and the other thermometers have only $others open ${if (others == 1) "square" else "squares"} " +
                                "there, so the glowing one gives at least $must. Fill it from the bulb to the glowing square.",
                            "$cap needs $need more; the others have only $others open there, so the glowing thermometer fills from its bulb to here.",
                        ),
                        MAX_EXPLANATION,
                    ),
                    focus = f.full.toSet(),
                    cited = lineFocus + l.tubes[f.tube],
                )
            }
        }
    }

    /** The open squares of [i]'s tube from the bulb to [i] (filling it) or from [i] to the tip (emptying it). */
    private fun tubeMove(l: ThermometersLogic.Layout, grid: IntArray, i: Int, full: Boolean): List<Int> {
        val tube = l.tubes[l.tubeOf[i]]
        val k = l.posOf[i]
        val range = if (full) 0..k else k until tube.size
        return range.map { tube[it] }.filter { grid[it] == ThermometersLogic.OPEN }
    }

    private fun whatIfStep(l: ThermometersLogic.Layout, targets: List<Int>, grid: IntArray, w: ThermometersLogic.WhatIf): Step {
        val n = l.n
        val i = w.square
        val filledIt = w.assume == ThermometersLogic.FULL
        val suppose = if (filledIt) "If the glowing square were filled (its thermometer with it)" else "If the glowing square were empty (and all above it)"
        val fail = "${lineName(n, w.line)} could no longer hold ${targets[w.line]}"
        val verdict = if (filledIt) "So it stays empty: cross it out." else "So it fills, from its bulb up."

        fun linkText(link: ThermometersLogic.Link, full: Boolean): String {
            val line = lineName(n, link.line)
            if (!full) return "$line would settle ${link.full.size + link.empty.size}"
            return line + " would " + listOfNotNull(
                link.full.size.takeIf { it > 0 }?.let { "fill $it" },
                link.empty.size.takeIf { it > 0 }?.let { "empty $it" },
            ).joinToString(" and ")
        }

        fun sentence(full: Boolean): String {
            val parts = w.links.map { linkText(it, full) } + fail
            return "$suppose, ${listed(parts, oxford = w.links.size > 1)}. $verdict"
        }
        val why = fit(listOf(sentence(true), sentence(false), "$suppose, $fail within ${w.links.size} lines. $verdict"), MAX_EXPLANATION)

        val cited = w.links.flatMap { it.full + it.empty }.toSet() + (n * n + w.line)
        return Step(
            technique = WHAT_IF,
            full = if (filledIt) emptyList() else tubeMove(l, grid, i, true),
            empty = if (filledIt) tubeMove(l, grid, i, false) else emptyList(),
            focus = setOf(i),
            cited = cited,
            nudge = "Suppose the glowing square ${if (filledIt) "fills" else "stays empty"}.",
            explanation = why,
        )
    }
}
