package com.joebywan.daybook.core

import com.joebywan.daybook.puzzles.Tally
import com.joebywan.daybook.puzzles.TallyLogic
import com.joebywan.daybook.puzzles.TallyState
import com.joebywan.daybook.puzzles.TallyTeacher

/**
 * Tally's course, on [MoveDrill]: the player removes or circles the step's numbers (one at a time or in a sweep), and a
 * mark the board's one answer disagrees with ends the run, a wrong circle included, as the hints flag it.
 */
val TallyTraining: Course = Course(
    puzzle = Tally,
    explanations = linkedMapOf(
        TallyTeacher.DONE to "When the circled numbers in a row or column already make its target, nothing else there " +
            "can stay: remove the rest.",
        TallyTeacher.TOO_BIG to "Take what a line's circled numbers make from its target: that is all it still needs. " +
            "A number bigger than that can't stay. Remove it.",
        TallyTeacher.TOO_MUCH to "Add up what is left in a line and see how far over its target it is. A number bigger " +
            "than that surplus can't go, or the line would fall short: circle it with Keep.",
        TallyTeacher.ALL_LEFT to "When what is left in a line adds up to exactly its target, every number there stays: " +
            "circle them all.",
        TallyTeacher.WAYS to "List the ways a line can still make its target. A number every way uses stays; one no " +
            "way uses goes.",
        TallyTeacher.WHAT_IF to "Suppose a number stays (or goes) and follow the lines it settles. If some line is left " +
            "with no way to make its target, the number does the opposite.",
    ),
    // TallyLogic.accepts: Standard needs only the four shortcuts, every Hard board the ways, every Expert a what-if.
    tierFor = tierByIndex({ TallyTraining.techniques }, hard = 4, expert = 5),
    // TallyTeacher.deduce reads the digits, the targets and the marks only; the targets are printed on the board.
    // Any line's instance of the technique (or any what-if within the hint's reach) is right, not only the hint's pick.
    drill = MoveDrill(
        next = { s ->
            (s as TallyState).let { t ->
                TallyTeacher.deduce(t.size, TallyLogic.digitArray(t), TallyLogic.targets(t), t.marks)?.let { Tally.deduction(t, it) }
            }
        },
        every = { s, technique ->
            (s as TallyState).let { t ->
                TallyTeacher.steps(t.size, TallyLogic.digitArray(t), TallyLogic.targets(t), t.marks)
                    .filter { it.technique == technique }.map { Tally.deduction(t, it) }
            }
        },
    ),
    badge = "T",
    // The first practice seed whose Expert board's walk needs all six. Pinned by TallyTrainingTest.
    examIndex = 0,
)
