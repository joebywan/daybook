package com.joebywan.daybook.core

import com.joebywan.daybook.puzzles.Unblock
import com.joebywan.daybook.puzzles.UnblockLogic
import com.joebywan.daybook.puzzles.UnblockState
import com.joebywan.daybook.puzzles.UnblockTeacher

/**
 * Unblock's course: the player makes the step's slide. Right is any slide that starts a shortest way out and is the
 * technique (`UnblockTeacher.steps`, from the cars alone). The game has no mistakes (any slide can be slid back), so here
 * a slide is wrong when it leaves the red car no nearer the exit by the fewest moves: a fact about the cars, read by a
 * fresh search, never a stored road. A shortest slide of another kind plays on. "Driving the red car out" is left out: it
 * is one slide along a clear row, nothing to practise, and never praised.
 */
val UnblockTraining: Course = Course(
    puzzle = Unblock,
    explanations = linkedMapOf(
        UnblockTeacher.CLEAR to "Look along the red car's row to the exit. A car standing in it has to slide out of the row, " +
            "up or down, far enough to leave it clear.",
        UnblockTeacher.MAKE_ROOM to "Often the car in the way can't move yet: another car blocks it. Then that one moves first, " +
            "to make room. Work back from the exit: what blocks the car that blocks the red car?",
        UnblockTeacher.PLAN to "Now and then the best slide frees nothing at once: it gets a car out of the way of what comes " +
            "next, such as the red car driving on before another car has to drop into its row behind it.",
    ),
    // Every tier's walks reach all three; Standard's boards are the quickest to read.
    tierFor = { Difficulty.STANDARD },
    drill = MoveDrill(
        next = { s -> UnblockTeacher.deduction(s as UnblockState) },
        every = { s, k -> (s as UnblockState).let { b -> UnblockTeacher.steps(b.cars, k).map { UnblockTeacher.deduction(b, it) } } },
        // A practice board is a position on the hints' walk from a proved start, so it is `par - moves` from the exit, and
        // every shortest slide keeps that sum. A slide that breaks it went no nearer.
        wrong = { s -> (s as UnblockState).let { b -> UnblockTeacher.fewest(b.cars) != b.par - b.moves } },
    ),
    badge = "U",
    // The first practice seed whose Expert board's walk needs all three (plan is the rare one). Pinned by UnblockTrainingTest.
    examIndex = 34,
    generate = UnblockLogic::generateVerified,
)
