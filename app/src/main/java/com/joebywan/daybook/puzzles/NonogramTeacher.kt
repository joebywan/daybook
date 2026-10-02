package com.joebywan.daybook.puzzles

/**
 * Nonograms, reasoned the way a person reasons them: one row or column at a time.
 *
 * Every board Daybook deals is line-solvable ([NonogramLogic]), so a step always exists and is
 * always a fact about a single line: its clue, and what is already marked in it. That is the whole
 * ladder, and there is no guessing step and no fallback to measure — if the player's marks are all
 * right, some line always has a square it can settle.
 *
 * [deduce] is handed the board as the player sees it (the clues and the marks) and has no
 * parameter through which the hidden picture could arrive, so a step it explains cannot lean on a
 * fact only the answer knows. The picture is read in one place, [mistake], to decide which of the
 * player's marks cannot be right.
 *
 * Techniques, simplest first, because the hint should be the step a person would have found next:
 * 1. [FULL] — the clues and their gaps take exactly the width of the line, so there is nowhere to
 *    slide and every square is settled.
 * 2. [FINISHED] — the squares already filled give the line every clue it has; the rest is empty.
 * 3. [OVERLAP] — slide the clues as far one way and then as far the other: squares covered both
 *    times are filled.
 * 4. [LINE] — everything else: with the marks already in the line, every way the clues can still be
 *    laid out agrees about these squares. The general case, which the three above are the common
 *    shortcuts of.
 *
 * Within a technique the first line wins, rows top to bottom and then columns left to right, so a
 * board gets the same hint on every platform.
 *
 * ## Highlight indices
 * Squares are `row * width + column`. The clues are highlightable too, so the hint can point at
 * the number it is about: row `r`'s clue is `width * height + r`, column `c`'s is
 * `width * height + height + c` ([NonogramLogic.rowClueIndex], [NonogramLogic.colClueIndex]).
 */
internal object NonogramTeacher {

    const val FULL = "full-line"
    const val FINISHED = "line-finished"
    const val OVERLAP = "overlap"
    const val LINE = "line-logic"
    const val MISTAKE = "mistake"

    /** Every technique in the order [deduce] prefers them, for reports. */
    val TECHNIQUES = listOf(FULL, FINISHED, OVERLAP, LINE)

    /** What the hint panel has room for (CLAUDE.md "Teaching"; the test pins it). */
    const val MAX_NUDGE = 70
    const val MAX_EXPLANATION = 200

    /**
     * One step: what to fill, cross or clear, where to look, and why. [clears] is only ever a
     * mistake being taken back.
     */
    class Step(
        val technique: String,
        val fills: List<Int> = emptyList(),
        val crosses: List<Int> = emptyList(),
        val clears: List<Int> = emptyList(),
        val focus: Set<Int>,
        val cited: Set<Int>,
        val nudge: String,
        val explanation: String,
    ) {
        val targets: Set<Int> get() = (fills + crosses + clears).toSet()
    }

    fun teach(s: NonogramState): Step? {
        if (s.solved) return null
        mistake(s)?.let { return it }
        return deduce(s.width, s.height, NonogramLogic.rowClues(s), NonogramLogic.colClues(s), s.cells)
    }

    // ---- mistakes -------------------------------------------------------------------------

    /**
     * The first of the player's marks, in reading order, that no picture fitting the clues can
     * keep. The boards have one picture, so that is a mark that disagrees with it.
     */
    private fun mistake(s: NonogramState): Step? {
        for (i in s.cells.indices) {
            val mark = s.cells[i]
            val wrong = (mark == NonogramLogic.FILLED && s.solution[i] != '1') ||
                (mark == NonogramLogic.CROSSED && s.solution[i] == '1')
            if (!wrong) continue
            val r = i / s.width
            val c = i % s.width
            val where = "row ${r + 1}, column ${c + 1}"
            return Step(
                technique = MISTAKE,
                clears = listOf(i),
                focus = setOf(i),
                cited = setOf(
                    NonogramLogic.rowClueIndex(s.width, s.height, r),
                    NonogramLogic.colClueIndex(s.width, s.height, c),
                ),
                nudge = "One of your marks doesn't fit the clues.",
                explanation = if (mark == NonogramLogic.FILLED) {
                    "No picture that fits the clues has $where filled. Take the fill back."
                } else {
                    "$where can't be empty: every picture that fits the clues fills it. Take the cross back."
                },
            )
        }
        return null
    }

    // ---- reasoning ------------------------------------------------------------------------

    /**
     * The next step reasoned from the clues and the marks in [cells] alone, or null when no line
     * has a square it can settle (a finished board, or one with a wrong mark in it).
     */
    fun deduce(width: Int, height: Int, rows: List<List<Int>>, cols: List<List<Int>>, cells: String): Step? {
        var best: Step? = null
        var bestRank = Int.MAX_VALUE
        for (line in 0 until height + width) {
            val step = lineStep(width, height, line, if (line < height) rows[line] else cols[line - height], cells)
                ?: continue
            val rank = TECHNIQUES.indexOf(step.technique)
            // Strictly better only, so on a tie the earlier line stays.
            if (rank < bestRank) {
                best = step
                bestRank = rank
            }
        }
        return best
    }

    private fun lineStep(width: Int, height: Int, line: Int, clue: List<Int>, cells: String): Step? {
        val at = NonogramLogic.lineCells(width, height, line)
        val n = at.size
        val marks = NonogramLogic.lineMarks(cells, at)
        val found = NonogramLogic.forced(clue, marks) ?: return null
        if (found.all { it == NonogramLogic.UNKNOWN }) return null

        val row = line < height
        val clueIndex = if (row) NonogramLogic.rowClueIndex(width, height, line)
        else NonogramLogic.colClueIndex(width, height, line - height)
        val name = if (row) "row ${line + 1}" else "column ${line - height + 1}"
        val cap = name.replaceFirstChar { it.uppercaseChar() }
        val clueText = clue.joinToString(" ")
        val clues = if (clue.size == 1) "clue $clueText" else "clues $clueText"

        fun cellsOf(values: Int, from: IntArray = found) = at.filterIndexed { x, _ -> from[x] == values }
        fun spots(values: Int, from: IntArray = found) = (0 until n).filter { from[it] == values }
        val fills = cellsOf(NonogramLogic.FILL)
        val crosses = cellsOf(NonogramLogic.CROSS)
        val lineSet = at.toSet()

        fun step(technique: String, fill: List<Int>, cross: List<Int>, nudge: String, why: String) = Step(
            technique = technique,
            fills = fill,
            crosses = cross,
            focus = lineSet + clueIndex,
            cited = emptySet(),
            nudge = nudge,
            explanation = why,
        )

        // 1. Nowhere to slide.
        if (clue.isNotEmpty() && NonogramLogic.minLength(clue) == n) {
            val why = if (clue.size == 1) {
                "$cap's clue $clueText is as wide as the line, so every square of it is filled."
            } else {
                "$cap has $n squares and its $clues take ${clue.joinToString(" + ")} + ${clue.size - 1} " +
                    "${if (clue.size == 2) "gap" else "gaps"} = $n exactly. With no room to slide, every square is settled."
            }
            return step(FULL, fills, crosses, "Look at $name: its clues leave no spare room.", why)
        }

        // 2. Every clue is already on the board.
        val filledNow = BooleanArray(n) { marks[it] == NonogramLogic.FILL }
        if (NonogramLogic.runs(filledNow) == clue && crosses.isNotEmpty()) {
            return step(
                FINISHED, emptyList(), crosses, "$cap looks complete.",
                if (clue.isEmpty()) "$cap has no clue, so none of its squares can be filled. Cross them out."
                else "$cap already has all of its $clues filled in, so none of its other squares can be filled. Cross them out.",
            )
        }

        // 3. The overlap of the clues alone.
        val blank = NonogramLogic.forcedOnBlank(clue, n)
        val overlap = (0 until n).filter { blank[it] == NonogramLogic.FILL && marks[it] == NonogramLogic.UNKNOWN }
        if (overlap.isNotEmpty()) {
            val (start, end) = if (row) "left" to "right" else "top" to "bottom"
            return step(
                OVERLAP, overlap.map { at[it] }, emptyList(), "Slide the clues along $name.",
                "In $name, push the $clues as far $start as they go, then as far $end. " +
                    "${describe(overlap)} ${if (overlap.size == 1) "is" else "are"} covered both times, so filled.",
            )
        }

        // 4. What the marks already in the line leave possible.
        val why = when {
            fills.isNotEmpty() && crosses.isNotEmpty() ->
                "With the marks already in $name, every way to fit its $clues fills ${describe(spots(NonogramLogic.FILL))} " +
                    "and leaves ${describe(spots(NonogramLogic.CROSS))} empty."
            fills.isNotEmpty() ->
                "With the marks already in $name, every way to fit its $clues fills ${describe(spots(NonogramLogic.FILL))}."
            else ->
                "With the marks already in $name, every way to fit its $clues leaves ${describe(spots(NonogramLogic.CROSS))} empty."
        }
        return step(LINE, fills, crosses, "$cap has a square that can only go one way.", why)
    }

    /** "square 4", "squares 4 to 7", "squares 2, 5 to 6 and 9", or a count when there are many. */
    private fun describe(positions: List<Int>): String {
        val groups = ArrayList<IntRange>()
        for (p in positions.map { it + 1 }) {
            val last = groups.lastOrNull()
            if (last != null && last.last + 1 == p) groups[groups.lastIndex] = last.first..p else groups.add(p..p)
        }
        if (positions.size > 6 || groups.size > 3) return "${positions.size} squares"
        val parts = groups.map { if (it.first == it.last) "${it.first}" else "${it.first} to ${it.last}" }
        val list = if (parts.size == 1) parts[0] else parts.dropLast(1).joinToString(", ") + " and " + parts.last()
        return if (positions.size == 1) "square $list" else "squares $list"
    }
}
