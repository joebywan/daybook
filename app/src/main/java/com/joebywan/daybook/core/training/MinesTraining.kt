package com.joebywan.daybook.core

import com.joebywan.daybook.puzzles.Mines
import com.joebywan.daybook.puzzles.MinesState
import com.joebywan.daybook.puzzles.MinesTeacher

/**
 * Mines' course: the player reveals the step's safe squares or flags its mines. A flag on a safe square is the teacher's
 * mistake; a mine revealed loses the board in a game, and here it is simply wrong: the verdict ends the board (no longer
 * interactive, so its Try again is not offered) and the next board is one tap away.
 */
val MinesTraining: Course = Course(
    puzzle = Mines,
    explanations = linkedMapOf(
        MinesTeacher.ALL_FOUND to "When a number already touches as many mines as it shows, every other hidden square " +
            "around it is safe: reveal them. A 0 touches none, so everything around it is safe.",
        MinesTeacher.ALL_MINES to "When a number has exactly as many hidden squares around it as mines it still needs, " +
            "every one of them is a mine: flag them with a long press, or with Flag below.",
        MinesTeacher.SUBSET to "Compare two numbers side by side. When every square where one still needs mines also " +
            "touches the other, the other's extra squares hold the difference: none means they're safe, as many as " +
            "there are squares means they're mines.",
        MinesTeacher.PAIR to "Two numbers that share squares: the shared squares can hold no more than the first still " +
            "needs, so the second's squares outside them must hold the rest. When that's all of them, they're mines, " +
            "and the first's own other squares are safe.",
        MinesTeacher.EDGE to "Count the mines left, shown above the board. When some numbers need every one of them on " +
            "squares none of them share, no mine is left for any other hidden square: those are all safe.",
        MinesTeacher.WHAT_IF to "Suppose a square held a mine, or were safe, and follow what the numbers around it then " +
            "say. If a number ends up with too many mines or too few squares, the other choice is the answer.",
    ),
    // Standard needs single numbers, Hard two numbers (subset 57, pair 33 of 60), Expert the count against the numbers (57)
    // and the what-if (4 of 60, still under a quarter of a second a board). "Counting the mines left" is left out: no
    // walk reaches it.
    tierFor = tierByIndex({ MinesTraining.techniques }, hard = 2, expert = 4),
    // MinesTeacher.deduce is handed the revealed numbers, the mines hit and the flags: never where a hidden mine is. The
    // walk's flags are its own sound steps, so they count as found mines here: otherwise the teacher (which never trusts a
    // player's flag) can offer a subset on a board where the flags already show a single number's step.
    drill = MoveDrill(
        view = { (it as MinesState).marks.toList() },
        next = { s ->
            (s as MinesState).let { m ->
                MinesTeacher.deduce(m.width, m.height, MinesTeacher.visibleNumbers(m), known(m), known(m), m.total)?.let(MinesTeacher::deduction)
            }
        },
        every = { s, t ->
            (s as MinesState).let { m ->
                MinesTeacher.steps(m.width, m.height, MinesTeacher.visibleNumbers(m), known(m), m.total, t).map(MinesTeacher::deduction)
            }
        },
        wrong = { it.failed },
    ),
    badge = "#mine",
    // The first practice seed whose Expert board's walk needs all six (1 of the first 60). Pinned by MinesTrainingTest.
    examIndex = 44,
)

/** The mines a practice board shows: those hit and the walk's flags (its own sound steps, never a guess). */
private fun known(m: MinesState) = BooleanArray(m.width * m.height) { it in m.bumps || m.isFlag(it) }
