package com.joebywan.daybook.core

import com.joebywan.daybook.puzzles.Pearls
import com.joebywan.daybook.puzzles.PearlsState
import com.joebywan.daybook.puzzles.PearlsTeacher

/**
 * Pearls' course on the move drill: the player draws the lines and crosses the edges the technique settles, at the hint's
 * square or any other the technique settles (`PearlsTeacher.steps`; a what-if is only the hint's). A line or a
 * cross the board's one loop disagrees with is the teacher's mistake and ends the run (sound: every board has one loop).
 *
 * "A pearl with two ways in" is left out: no walk ever reaches it (0 of 30 boards on any tier; the pearls' and squares'
 * other rules always settle it first), so there is no board to practise it on.
 */
val PearlsTraining: Course = Course(
    puzzle = Pearls,
    explanations = linkedMapOf(
        PearlsTeacher.BLACK_PEARL to "The loop turns on a black pearl and runs straight on through the next square both " +
            "ways. An arm it can't draw sends it the other way. Drag to draw a line; tap an edge to cross it.",
        PearlsTeacher.WHITE_PEARL to "The loop goes straight through a white pearl. When it can't go one way, for an " +
            "edge crossed or the border, it runs straight through the other way.",
        PearlsTeacher.WHITE_TURN to "The loop turns just before or just after a white pearl. When it runs straight on " +
            "through the square on one side, it must turn at the square on the other: cross the edge straight on from there.",
        PearlsTeacher.ONE_WAY to "A line can't stop. When the square at its end has only one way on left, the line goes there.",
        PearlsTeacher.DEAD_END to "A loop through a square needs two ways in. A square with only one way left can't be " +
            "passed: cross its last edge.",
        PearlsTeacher.SMALL_LOOP to "There is only one loop. Joining two ends of one line would close a loop that leaves " +
            "out a pearl or another line, so cross the edge between them.",
        PearlsTeacher.WHAT_IF to "Suppose a line on an edge, or none, and follow the rules a few steps. If a square " +
            "would need three lines or a pearl couldn't keep its rule, the edge goes the other way.",
    ),
    // PearlsLogic.specFor: Standard the pearls' and squares' own rules, Hard must need the one loop, Expert a what-if.
    tierFor = tierByIndex({ PearlsTraining.techniques }, hard = 5, expert = 6),
    // PearlsTeacher.deduce is handed the pearls and the edges; the loop never reaches it.
    drill = MoveDrill(
        next = { s -> (s as PearlsState).let { p -> PearlsTeacher.deduce(p.size, p.pearls, p.edges)?.let { Pearls.deduction(p, it) } } },
        every = { s, t -> (s as PearlsState).let { p -> PearlsTeacher.steps(p.size, p.pearls, p.edges, t).map { Pearls.deduction(p, it) } } },
    ),
    badge = "O",
    examIndex = 0,
)
