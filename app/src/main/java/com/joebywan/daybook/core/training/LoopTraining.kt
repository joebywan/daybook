package com.joebywan.daybook.core

import com.joebywan.daybook.puzzles.Loop
import com.joebywan.daybook.puzzles.LoopState
import com.joebywan.daybook.puzzles.LoopTeacher

/**
 * Loop's course on the move drill, as Pearls' is: the player draws the lines and crosses the edges the technique settles,
 * at the hint's number or dot or any other the technique settles (`LoopTeacher.steps`; a what-if is only the hint's). A
 * line or a cross the board's one loop disagrees with is the teacher's mistake and ends the run (sound: every board has
 * one loop).
 */
val LoopTraining: Course = Course(
    puzzle = Loop,
    explanations = linkedMapOf(
        LoopTeacher.COUNT to "A number with all its lines drawn has no more: cross its other sides. One with only as many " +
            "sides left as it needs takes them all. Drag from dot to dot to draw; tap an edge to cross it.",
        LoopTeacher.ONE_WAY to "A line can't stop. When the dot at its end has only one way on left, the line goes there.",
        LoopTeacher.DEAD_END to "A loop through a dot needs two ways. A dot with no line and only one way left can't be " +
            "passed: cross that way.",
        LoopTeacher.CORNER to "Look at a number with the dots at its corners: each takes no line or two. When only one way " +
            "of giving the number its count keeps every corner right, the edges it settles go in.",
        LoopTeacher.SMALL_LOOP to "There is only one loop. Joining two ends of one line would close a loop that leaves out " +
            "another line or a number short, so cross the edge between them.",
        LoopTeacher.WHAT_IF to "Suppose a line on an edge, or none, and follow the rules a few steps. If a dot would need " +
            "three lines or a number couldn't have its count, the edge goes the other way.",
    ),
    // LoopLogic.specFor: Standard the local rules, Hard must need the one loop, Expert a what-if.
    tierFor = tierByIndex({ LoopTraining.techniques }, hard = 4, expert = 5),
    // LoopTeacher.deduce is handed the numbers and the edges; the loop never reaches it.
    drill = MoveDrill(
        // A step names an edge as a square past the board's (LoopTeacher.edgeIndex), a dot past the edges.
        view = { s -> (s as LoopState).let { p -> List<Any?>(p.size * p.size) { null } + p.edges.toList() } },
        next = { s -> (s as LoopState).let { p -> LoopTeacher.deduce(p.size, p.clues, p.edges)?.let { Loop.deduction(p, it) } } },
        every = { s, t -> (s as LoopState).let { p -> LoopTeacher.steps(p.size, p.clues, p.edges, t).map { Loop.deduction(p, it) } } },
    ),
    badge = "3",
    examIndex = 0,
)
