package com.joebywan.daybook.core

import com.joebywan.daybook.puzzles.Mambo
import com.joebywan.daybook.puzzles.MamboState
import com.joebywan.daybook.puzzles.MamboTeacher

/**
 * Mambo's course, on [MoveDrill]: the player places the step's suns or moons (a tap cycles moon, sun, empty), and a
 * symbol no legal answer keeps (`Mambo.mistakes`) ends the run.
 */
val MamboTraining: Course = Course(
    puzzle = Mambo,
    explanations = linkedMapOf(
        MamboTeacher.PAIR to "Two suns side by side can't have a third beside them, so the squares at either end are " +
            "moons; the same goes the other way round. Tap a square once for a moon, twice for a sun.",
        MamboTeacher.SANDWICH to "A gap between two matching symbols takes the other one: the same there would make " +
            "three together.",
        MamboTeacher.LINK to "An = says two squares match, an x says they differ. When one end is filled, the other " +
            "follows.",
        MamboTeacher.QUOTA to "Every row and column holds as many suns as moons. When a line already has its half of " +
            "one symbol, every square left in it is the other.",
        MamboTeacher.ALMOST to "A line one short of its half of one symbol: try its last one on each open square, the " +
            "rest the other. Where that leaves three of the other together, the square can't take it.",
        MamboTeacher.WHAT_IF to "Suppose a symbol on a square and follow the simple rules a round. If that breaks a rule " +
            "somewhere, the square takes the other symbol.",
    ),
    // MamboTeacher.depth: Standard falls to the first four, every Hard board needs one short, every Expert a what-if.
    tierFor = tierByIndex({ MamboTraining.techniques }, hard = 4, expert = 5),
    // MamboTeacher.deduce has no parameter through which the answer could reach it.
    // A sun is two taps, through a moon that may be wrong: a wrong verdict waits as long as the board's own caption.
    // Any instance of the technique on the position is right, not only the hint's pick.
    drill = MoveDrill(
        settleMillis = Mambo.SETTLE_MILLIS,
        next = { s -> (s as MamboState).let { m -> MamboTeacher.deduce(m.size, m.cells, m.links)?.let { Mambo.deduction(m, it) } } },
        every = { s, technique ->
            (s as MamboState).let { m ->
                MamboTeacher.steps(m.size, m.cells, m.links).filter { it.technique == technique }.map { Mambo.deduction(m, it) }.toList()
            }
        },
    ),
    badge = "&",
    // The first practice seed whose Expert board's walk needs all six. Pinned by MamboTrainingTest.
    examIndex = 0,
)
