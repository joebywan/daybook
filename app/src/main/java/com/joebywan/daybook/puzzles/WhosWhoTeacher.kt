package com.joebywan.daybook.puzzles

import com.joebywan.daybook.puzzles.WhosWhoLogic.Break
import com.joebywan.daybook.puzzles.WhosWhoLogic.Step

/**
 * Who's Who, reasoned the way a person reasons it: one clue at a time against what the table already shows, and on
 * Expert one supposition followed a few steps.
 *
 * The ladder is [WhosWhoLogic.deduce], the generator's own, so every shipped board is finished by it and there is
 * no fallback to measure. This file only says each step in words and marks where to look.
 *
 * [deduce] is handed the board as the player sees it (the clues and the marks) and has no parameter through which
 * the answer could arrive. The answer is read in one place, [mistake], to decide which of the player's marks cannot
 * be right; sound because every shipped board has exactly one answer.
 *
 * Techniques, simplest first: [CLUE] (a clue that names a person settles them), [ONLY] (one item left in a cell, or
 * one person left for an item), [LINK] (two things a clue ties together, carried through what is known), [HOUSE]
 * (next to and left of against the houses still possible), [WHAT_IF] (suppose a tick; a few steps later something has
 * nothing left).
 *
 * ## Highlight indices
 * Table cell `person * columns + column`; clue `k` is [CLUE_BASE] `+ k`.
 */
internal object WhosWhoTeacher {

    const val CLUE = "clue"
    const val ONLY = "only"
    const val LINK = "link"
    const val HOUSE = "house"
    const val WHAT_IF = "what-if"
    const val MISTAKE = "mistake"

    /** Every technique in the order [deduce] prefers them, for reports. */
    val TECHNIQUES = listOf(CLUE, ONLY, LINK, HOUSE, WHAT_IF)

    const val CLUE_BASE = 1000

    /** What the hint panel has room for (the test pins it; the popover scrolls past four lines). */
    const val MAX_NUDGE = 70
    const val MAX_EXPLANATION = 260

    fun name(technique: Int): String = TECHNIQUES[technique]

    /** One step: marks to tick, cross or clear (mark indices), where to look, and why. */
    class Lesson(
        val technique: String,
        val ticks: List<Int> = emptyList(),
        val crosses: List<Int> = emptyList(),
        val clears: List<Int> = emptyList(),
        val focus: Set<Int>,
        val cited: Set<Int>,
        val targets: Set<Int>,
        val nudge: String,
        val explanation: String,
    )

    fun teach(s: WhosWhoState): Lesson? {
        if (s.solved) return null
        mistake(s)?.let { return it }
        return deduce(s, s.marks)
    }

    // ---- mistakes -------------------------------------------------------------------------------

    /** The first mark, in order, that the board's one answer disagrees with. */
    private fun mistake(s: WhosWhoState): Lesson? {
        val n = s.n
        for (at in s.marks.indices) {
            val mark = s.marks[at]
            if (mark == WhosWhoLogic.EMPTY) continue
            val cell = at / n
            val person = cell / s.columns
            val column = cell % s.columns
            val slot = at % n
            val right = s.answer[column][person] == slot
            if (mark == WhosWhoLogic.TICK && right || mark == WhosWhoLogic.CROSS && !right) continue
            val ref = (column + 1) * n + slot
            val who = s.names[person]
            return Lesson(
                technique = MISTAKE,
                clears = listOf(at),
                focus = setOf(cell),
                cited = emptySet(),
                targets = setOf(cell),
                nudge = "One of your marks doesn't fit the clues.",
                explanation = if (mark == WhosWhoLogic.TICK) {
                    "$who can't ${WhosWhoLogic.base(s, ref)}: no way of keeping every clue does. Take the tick off."
                } else {
                    "$who has to ${WhosWhoLogic.base(s, ref)}: every way of keeping the clues needs it. Take the cross off."
                },
            )
        }
        return null
    }

    // ---- reasoning ------------------------------------------------------------------------------

    /**
     * The next step reasoned from the clues and the [marks] alone, or null when nothing can be settled (a finished
     * board, or one with a wrong mark). [s] supplies the words; its answer is not read.
     */
    fun deduce(s: WhosWhoState, marks: String): Lesson? {
        val puzzle = WhosWhoLogic.puzzleOf(s)
        val grid = WhosWhoLogic.gridOf(s.n, s.columns, marks)
        if (WhosWhoLogic.broken(puzzle, grid) != null) return null
        val step = WhosWhoLogic.deduce(puzzle, grid) ?: return null
        return lesson(s, grid, step)
    }

    private fun cellsOf(s: WhosWhoState, marks: List<Int>) = marks.map { it / s.n }.toSet()

    private fun lesson(s: WhosWhoState, grid: WhosWhoLogic.Grid, step: Step): Lesson {
        val technique = name(step.technique)
        val targets = cellsOf(s, step.ticks + step.crosses)
        val clue = if (step.clue >= 0) setOf(CLUE_BASE + step.clue) else emptySet()
        val cited = clue + citedCells(s, grid, step)
        val nudge = when (step.technique) {
            WhosWhoLogic.ONLY -> if (step.person >= 0) "Look at ${s.names[step.person]}'s row." else "Look down one column."
            WhosWhoLogic.WHAT_IF -> "Try supposing something in ${s.names[cellOf(s, step.supposed) / s.columns]}'s row."
            else -> "Read clue ${step.clue + 1} against the table."
        }
        return Lesson(
            technique = technique,
            ticks = step.ticks,
            crosses = step.crosses,
            focus = targets + clue,
            cited = cited - targets,
            targets = targets,
            nudge = nudge,
            explanation = explain(s, grid, step),
        )
    }

    private fun cellOf(s: WhosWhoState, mark: Int) = mark / s.n

    /** Cells the reasoning leans on: the known holder's cell of the clue's other item, where there is one. */
    private fun citedCells(s: WhosWhoState, grid: WhosWhoLogic.Grid, step: Step): Set<Int> {
        if (step.partner < 0 || step.partner / s.n == 0) return emptySet()
        val column = step.partner / s.n - 1
        return (0 until s.n).filter { grid.has(it, step.partner) }.map { it * s.columns + column }.toSet()
    }

    // ---- words ----------------------------------------------------------------------------------

    /** "Ann", "Ann and Ben", "Ann, Ben and Cara". */
    fun list(words: List<String>): String = when (words.size) {
        0 -> ""
        1 -> words[0]
        else -> words.dropLast(1).joinToString(", ") + " and " + words.last()
    }

    /** "the fish or the bird", "house 1 or house 3". */
    private fun orList(words: List<String>): String = when (words.size) {
        1 -> words[0]
        else -> words.dropLast(1).joinToString(", ") + " or " + words.last()
    }

    private fun person(s: WhosWhoState, mark: Int) = s.names[mark / s.n / s.columns]

    private fun itemOf(s: WhosWhoState, mark: Int) = (mark / s.n % s.columns + 1) * s.n + mark % s.n

    private fun clueLine(s: WhosWhoState, k: Int) = "Clue ${k + 1}: ${WhosWhoLogic.clueText(s, k)}"

    /** "cross the cat off for Ann", "cross the fish and the bird off for Ann", "cross the baker off for Ben and Dev". */
    private fun crossOff(s: WhosWhoState, crosses: List<Int>): String {
        val people = crosses.map { person(s, it) }.distinct()
        val things = crosses.map { WhosWhoLogic.thing(s, itemOf(s, it)) }.distinct()
        return "cross ${list(things)} off for ${list(people)}"
    }

    /** What a NEXT / LEFT clue asks of the partner of the side that holds [item]: "next to", "somewhere right of". */
    private fun relation(s: WhosWhoState, clue: WhoClue, item: Int): String = when {
        clue.kind == WhosWhoLogic.NEXT -> "next to"
        item == clue.a -> "somewhere right of"
        else -> "somewhere left of"
    }

    private fun explain(s: WhosWhoState, grid: WhosWhoLogic.Grid, step: Step): String {
        val n = s.n
        return when (step.technique) {
            WhosWhoLogic.CLUE -> {
                val action = if (step.ticks.isNotEmpty()) {
                    "Tick ${WhosWhoLogic.thing(s, itemOf(s, step.ticks[0]))} for ${person(s, step.ticks[0])}."
                } else {
                    capital(crossOff(s, step.crosses)) + "."
                }
                "${clueLine(s, step.clue)} $action"
            }
            WhosWhoLogic.ONLY -> {
                val mark = step.ticks[0]
                val who = person(s, mark)
                val item = itemOf(s, mark)
                val column = item / n - 1
                if (step.person >= 0) {
                    "Every other ${WhosWhoWords.NOUNS[s.groups[column]]} is ruled out for $who, so $who ${WhosWhoLogic.has(s, item)}."
                } else {
                    "Everyone but $who is ruled out for ${WhosWhoLogic.thing(s, item)}, so $who ${WhosWhoLogic.has(s, item)}."
                }
            }
            WhosWhoLogic.LINK -> {
                val clue = s.clues[step.clue]
                val line = clueLine(s, step.clue)
                when {
                    step.ticks.isNotEmpty() -> {
                        val who = s.names[step.person]
                        "$line $who ${WhosWhoLogic.has(s, step.partner)}, so $who ${WhosWhoLogic.has(s, step.item)} too."
                    }
                    step.person >= 0 && clue.kind == WhosWhoLogic.NOT -> {
                        val who = s.names[step.person]
                        "$line $who ${WhosWhoLogic.has(s, step.partner)}, so ${crossOff(s, step.crosses)}."
                    }
                    step.person >= 0 -> {
                        val who = s.names[step.person]
                        "$line $who ${WhosWhoLogic.has(s, clue.a)}, so ${crossOff(s, step.crosses)}."
                    }
                    clue.kind == WhosWhoLogic.SAME -> {
                        val people = list(step.crosses.map { person(s, it) })
                        "$line ${capital(WhosWhoLogic.thing(s, step.partner))} is ruled out for $people, so ${crossOff(s, step.crosses)}."
                    }
                    else -> {
                        val people = list(step.crosses.map { person(s, it) })
                        "$line ${capital(WhosWhoLogic.thing(s, clue.b))} and ${WhosWhoLogic.thing(s, clue.c)} are both ruled out for $people, so ${crossOff(s, step.crosses)}."
                    }
                }
            }
            WhosWhoLogic.HOUSE -> {
                val clue = s.clues[step.clue]
                val line = clueLine(s, step.clue)
                val rel = relation(s, clue, step.item)
                val partner = WhosWhoLogic.base(s, step.partner)
                if (step.person >= 0) {
                    val who = s.names[step.person]
                    val houses = orList(step.crosses.map { "house ${it % n + 1}" })
                    val known = if (step.item / n == 0) "" else "$who ${WhosWhoLogic.has(s, step.item)}, and "
                    "$line ${capital(known)}nobody who could $partner can live $rel $houses, so ${crossOff(s, step.crosses)}."
                } else {
                    val people = step.crosses.map { person(s, it) }
                    val them = if (people.size == 1) people[0] else "any of them"
                    "$line Wherever ${list(people)} can live, nobody who could $partner can live $rel $them, " +
                        "so ${crossOff(s, step.crosses)}."
                }
            }
            else -> {
                val supposed = step.supposed
                val who = person(s, supposed)
                val chain = step.chain.map { link(s, it) }
                val end = when (val b = step.broke) {
                    is Break.EmptyCell -> "${s.names[b.person]} has no ${WhosWhoWords.NOUNS[s.groups[b.column]]} left"
                    is Break.NoHolder -> "nobody can ${WhosWhoLogic.base(s, (b.column + 1) * n + b.slot)}"
                    is Break.Clue -> "clue ${b.clue + 1} can't hold"
                    null -> "nothing fits"
                }
                val steps = (chain + end).let { if (it.size == 1) it[0] else it.dropLast(1).joinToString(", ") + ", and " + it.last() }
                "Suppose $who ${WhosWhoLogic.has(s, itemOf(s, supposed))}: then $steps. So ${crossOff(s, step.crosses)}."
            }
        }
    }

    /** One step of a what-if, as a clause: "Ann is the baker (clue 2)", "Ben can't have the cat (clue 4)". */
    private fun link(s: WhosWhoState, step: Step): String {
        val why = if (step.clue >= 0) " (clue ${step.clue + 1})" else ""
        if (step.ticks.isNotEmpty()) {
            val mark = step.ticks[0]
            return "${person(s, mark)} ${WhosWhoLogic.has(s, itemOf(s, mark))}$why"
        }
        val people = step.crosses.map { person(s, it) }.distinct()
        val items = step.crosses.map { itemOf(s, it) }.distinct()
        val what = WhosWhoLogic.base(s, items[0]) + if (items.size > 1) " or " + orList(items.drop(1).map { WhosWhoLogic.thing(s, it) }) else ""
        return "${list(people)} can't $what$why"
    }

    private fun capital(text: String) = WhosWhoLogic.capital(text)
}
