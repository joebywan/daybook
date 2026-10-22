package com.joebywan.daybook.puzzles

/**
 * Tally, reasoned the way a person reasons it: one row or column at a time, and on Expert one
 * supposition followed a few lines further.
 *
 * Every board Daybook deals is finished by exactly this ladder ([TallyLogic.accepts]: techniques
 * 1-4 are [TallyLogic.shortcuts], 5 is [TallyLogic.forced], 6 is [TallyLogic.firstWhatIf]), so a
 * step always exists on a board whose marks are right, and there is no fallback to measure.
 *
 * [deduce] is handed the board as the player sees it (the digits, the targets and the marks) and
 * has no parameter through which the answer could arrive. The answer is read in one place,
 * [mistake], to decide which of the player's marks cannot be right; sound because every shipped
 * board has exactly one answer.
 *
 * Techniques, simplest first:
 * 1. [DONE] — the circled numbers already make the target, so everything else in the line goes.
 * 2. [TOO_BIG] — a number bigger than what the line still needs (target less the circled) goes.
 * 3. [TOO_MUCH] — a number bigger than the line's surplus (what is left less the target) stays:
 *    removing it would take away too much.
 * 4. [ALL_LEFT] — what is left in the line makes the target exactly, so all of it stays. Such a
 *    line already shows green, so circling it is tidying up; it comes after the shortcuts that
 *    move an unfinished line on (found on the emulator: a nearly finished board's hint said
 *    "circle the 6 and 2" in a green row while the last row still had numbers to remove).
 * 5. [WAYS] — everything else one line says: every way to make the target uses this number, or
 *    none does. The general case, which the four above are the common shortcuts of.
 * 6. [WHAT_IF] — suppose one number stays (or goes), follow at most [TallyLogic.MAX_LINKS] lines
 *    from it, and some line is left with no way to make its target, so the number does the
 *    opposite. Only Expert boards need it, and every Expert board does at least once (as every
 *    Hard board needs 5 and no Standard board does).
 *
 * Within a technique the first line wins, rows top to bottom and then columns left to right, so a
 * board gets the same hint on every platform.
 *
 * ## Highlight indices
 * Squares are `row * size + column`. Row `r`'s target is `size * size + r`, column `c`'s is
 * `size * size + size + c` ([TallyLogic.rowTargetIndex], [TallyLogic.colTargetIndex]).
 */
internal object TallyTeacher {

    const val DONE = "line-done"
    const val ALL_LEFT = "all-left"
    const val TOO_BIG = "too-big"
    const val TOO_MUCH = "too-much"
    const val WAYS = "every-way"
    const val WHAT_IF = "what-if"
    const val MISTAKE = "mistake"

    /** Every technique in the order [deduce] prefers them, for reports. */
    val TECHNIQUES = listOf(DONE, TOO_BIG, TOO_MUCH, ALL_LEFT, WAYS, WHAT_IF)

    /** What the hint panel has room for (the test pins it). */
    const val MAX_NUDGE = 70
    const val MAX_EXPLANATION = 200

    /** One step: what to circle, remove or clear, where to look, and why. [clears] is only ever a mistake. */
    class Step(
        val technique: String,
        val stays: List<Int> = emptyList(),
        val goes: List<Int> = emptyList(),
        val clears: List<Int> = emptyList(),
        val focus: Set<Int>,
        val cited: Set<Int>,
        val nudge: String,
        val explanation: String,
    ) {
        val targets: Set<Int> get() = (stays + goes + clears).toSet()
    }

    fun teach(s: TallyState): Step? {
        if (s.solved) return null
        mistake(s)?.let { return it }
        return deduce(s.size, TallyLogic.digitArray(s), TallyLogic.targets(s), s.marks)
    }

    // ---- mistakes -------------------------------------------------------------------------

    /** The first mark, in reading order, that the board's one answer disagrees with. */
    private fun mistake(s: TallyState): Step? {
        val n = s.size
        for (i in s.marks.indices) {
            val mark = s.marks[i]
            val removedWrongly = mark == TallyLogic.REMOVED && s.keep[i] == '1'
            val keptWrongly = mark == TallyLogic.KEPT && s.keep[i] == '0'
            if (!removedWrongly && !keptWrongly) continue
            val what = "The ${s.digitAt(i)} at ${at(n, i)}"
            return Step(
                technique = MISTAKE,
                clears = listOf(i),
                focus = setOf(i),
                cited = setOf(TallyLogic.rowTargetIndex(n, i / n), TallyLogic.colTargetIndex(n, i % n)),
                nudge = "One of your marks doesn't fit the targets.",
                explanation = if (removedWrongly) {
                    "$what has to stay: no way of meeting every target removes it. Put it back."
                } else {
                    "$what can't stay: no way of meeting every target keeps it. Take the circle off."
                },
            )
        }
        return null
    }

    // ---- reasoning ------------------------------------------------------------------------

    /**
     * The next step reasoned from the digits, the [targets] (rows then columns) and the [marks]
     * alone, or null when nothing can be settled (a finished board, or one with a wrong mark).
     */
    fun deduce(n: Int, digits: IntArray, targets: List<Int>, marks: String): Step? {
        val grid = TallyLogic.markState(marks)
        var best: Step? = null
        var bestRank = Int.MAX_VALUE
        for (line in 0 until 2 * n) {
            val step = lineStep(n, digits, targets, grid, line) ?: continue
            val rank = TECHNIQUES.indexOf(step.technique)
            // Strictly better only, so on a tie the earlier line stays.
            if (rank < bestRank) {
                best = step
                bestRank = rank
            }
        }
        if (best != null) return best
        val w = TallyLogic.firstWhatIf(n, digits, targets, grid) ?: return null
        return whatIfStep(n, digits, targets, w)
    }

    private fun lineStep(n: Int, digits: IntArray, targets: List<Int>, grid: IntArray, line: Int): Step? {
        val at = TallyLogic.lineCells(n, line)
        val values = IntArray(n) { digits[at[it]] }
        val state = IntArray(n) { grid[at[it]] }
        val target = targets[line]
        val found = TallyLogic.forced(values, state, target) ?: return null
        if (found.all { it == TallyLogic.OPEN }) return null

        val name = lineName(n, line)
        val cap = name.replaceFirstChar { it.uppercaseChar() }
        val open = (0 until n).filter { state[it] == TallyLogic.OPEN }
        val kept = (0 until n).filter { state[it] == TallyLogic.STAY }.sumOf { values[it] }
        val left = (0 until n).filter { state[it] != TallyLogic.GO }.sumOf { values[it] }
        val focus = at.toSet() + targetIndex(n, line)

        fun step(technique: String, stays: List<Int>, goes: List<Int>, nudge: String, why: String) = Step(
            technique = technique,
            stays = stays.map { at[it] },
            goes = goes.map { at[it] },
            focus = focus,
            cited = focus,
            nudge = nudge,
            explanation = why,
        )

        if (kept == target) {
            return step(
                DONE, emptyList(), open, "$cap already has its $target.",
                "The circled numbers in $name already make $target, so nothing else there can stay: " +
                    "remove ${names(values, open)}.",
            )
        }
        if (left == target) {
            return step(
                ALL_LEFT, open, emptyList(), "Add up what is left in $name.",
                "What is left in $name adds up to exactly $target, so every number there stays: circle ${names(values, open)}.",
            )
        }
        val need = target - kept
        val tooBig = open.filter { values[it] > need }
        if (tooBig.isNotEmpty()) {
            val needs = if (kept == 0) "$cap needs $target" else "$cap needs $target and its circled numbers make $kept, so only $need more"
            return step(
                TOO_BIG, emptyList(), tooBig, "$cap has a number bigger than it needs.",
                "$needs: ${names(values, tooBig)} ${if (tooBig.size == 1) "is" else "are"} too big to stay. Remove ${if (tooBig.size == 1) "it" else "them"}.",
            )
        }
        val excess = left - target
        val tooMuch = open.filter { values[it] > excess }
        if (tooMuch.isNotEmpty()) {
            return step(
                TOO_MUCH, tooMuch, emptyList(), "$cap can't lose its biggest numbers.",
                "What is left in $name makes $left, $excess over its $target. Removing ${names(values, tooMuch, "or")} " +
                    "would take away too much, so ${if (tooMuch.size == 1) "it stays" else "they stay"}. Circle ${if (tooMuch.size == 1) "it" else "them"}.",
            )
        }

        val stays = (0 until n).filter { found[it] == TallyLogic.STAY }
        val goes = (0 until n).filter { found[it] == TallyLogic.GO }
        val ways = TallyLogic.ways(values, state, target)
        val how = if (ways.size <= 3) {
            "$cap makes $target only as " + ways.joinToString(" or ") { m -> (0 until n).filter { m shr it and 1 == 1 }.joinToString("+") { "${values[it]}" } } + ". "
        } else {
            "There are ${ways.size} ways to make $target in $name. "
        }
        val verdict = when {
            stays.isNotEmpty() && goes.isNotEmpty() ->
                "Every one uses ${names(values, stays)} and none uses ${names(values, goes, "or")}."
            stays.isNotEmpty() -> "Every one uses ${names(values, stays)}, so circle ${if (stays.size == 1) "it" else "them"}."
            else -> "None uses ${names(values, goes, "or")}, so remove ${if (goes.size == 1) "it" else "them"}."
        }
        var why = how + verdict
        if (why.length > MAX_EXPLANATION) why = "There are ${ways.size} ways to make $target in $name. $verdict"
        if (why.length > MAX_EXPLANATION) why = "Every way to make $target in $name settles ${stays.size + goes.size} of its numbers."
        return step(WAYS, stays, goes, "Look at the ways $name can make $target.", why)
    }

    private fun whatIfStep(n: Int, digits: IntArray, targets: List<Int>, w: TallyLogic.WhatIf): Step {
        val i = w.square
        val stayed = w.assume == TallyLogic.STAY
        val square = "the ${digits[i]} at ${at(n, i)}"
        val suppose = "If $square ${if (stayed) "stayed" else "went"}"
        val fail = "${lineName(n, w.line)} could no longer make ${targets[w.line]}"
        val verdict = "So it ${if (stayed) "goes" else "stays"}."

        fun linkText(link: TallyLogic.Link, full: Boolean): String {
            val line = lineName(n, link.line)
            if (!full) return "$line would settle ${link.stays.size + link.goes.size}"
            val at = TallyLogic.lineCells(n, link.line)
            val values = IntArray(n) { digits[at[it]] }
            val keep = link.stays.map { at.indexOf(it) }
            val lose = link.goes.map { at.indexOf(it) }
            return line + " would " + listOfNotNull(
                keep.takeIf { it.isNotEmpty() }?.let { "keep ${names(values, it)}" },
                lose.takeIf { it.isNotEmpty() }?.let { "lose ${names(values, it)}" },
            ).joinToString(" and ")
        }

        fun sentence(full: Boolean): String {
            val parts = w.links.map { linkText(it, full) } + fail
            val chain = if (parts.size == 1) parts[0] else parts.dropLast(1).joinToString(", ") + ", and " + parts.last()
            return "$suppose, $chain. $verdict"
        }
        var why = sentence(true)
        if (why.length > MAX_EXPLANATION) why = sentence(false)
        if (why.length > MAX_EXPLANATION) why = "$suppose, following ${w.links.size} lines on, $fail. $verdict"

        val cited = w.links.flatMap { it.stays + it.goes }.toSet() + targetIndex(n, w.line)
        return Step(
            technique = WHAT_IF,
            stays = if (stayed) emptyList() else listOf(i),
            goes = if (stayed) listOf(i) else emptyList(),
            focus = setOf(i),
            cited = cited,
            nudge = "Suppose the ${digits[i]} at ${at(n, i)} ${if (stayed) "stays" else "goes"}.",
            explanation = why,
        )
    }

    // ---- words ----------------------------------------------------------------------------

    private fun lineName(n: Int, line: Int) = if (line < n) "row ${line + 1}" else "column ${line - n + 1}"

    private fun targetIndex(n: Int, line: Int) =
        if (line < n) TallyLogic.rowTargetIndex(n, line) else TallyLogic.colTargetIndex(n, line - n)

    private fun at(n: Int, i: Int) = "row ${i / n + 1}, column ${i % n + 1}"

    /**
     * "the 8", "the 8 and 3", "the 8, 3 and 1", with a square number where the line repeats a
     * digit ("the 4 in square 2"), or a count when there are many. [joiner] is "and" or "or".
     */
    private fun names(values: IntArray, positions: List<Int>, joiner: String = "and"): String {
        if (positions.size > 4) return "${positions.size} numbers"
        val repeated = positions.any { p -> values.count { it == values[p] } > 1 }
        val parts = if (repeated) {
            positions.map { "${values[it]} in square ${it + 1}" }
        } else {
            positions.map { "${values[it]}" }
        }
        val list = if (parts.size == 1) parts[0] else parts.dropLast(1).joinToString(", ") + " $joiner " + parts.last()
        return "the $list"
    }
}
