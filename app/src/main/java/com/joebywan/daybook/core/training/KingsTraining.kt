package com.joebywan.daybook.core

import com.joebywan.daybook.puzzles.Kings
import com.joebywan.daybook.puzzles.KingsState
import com.joebywan.daybook.puzzles.KingsTeacher

/**
 * Kings' course, the first on [MoveDrill]: the teacher crowns and crosses rather than placing digits. A practice is a
 * board walked by the hints' own steps; the player makes the step, or any other step of the same technique on the board
 * (crown the square, or cross out every square it names, one at a time or in a sweep), and a cross or crown the teacher
 * calls a mistake ends the run.
 */
val KingsTraining: Course = Course(
    puzzle = Kings,
    explanations = linkedMapOf(
        KingsTeacher.LAST_SQUARE to "When a row, column or colour has one open square left, its king goes there. " +
            "Crown it with a double-tap or a long press.",
        KingsTeacher.LOCKED to "When a colour's open squares all lie in one row or column, its king is in that line, so " +
            "the line's other squares can't hold one: cross them out. It works the other way round too, a line whose " +
            "open squares all lie in one colour.",
        KingsTeacher.WOULD_EMPTY to "A king rules out its row, its column, its colour and the squares around it. When a " +
            "king on a square would rule out every open square of some row, column or colour, it can't go there: " +
            "cross it out.",
        KingsTeacher.CONFINED to "The same as a colour locked to a line, with more of them: when two colours fit only in " +
            "the same two rows (or columns), those lines' kings belong to them, so cross out the lines' other squares. Three in " +
            "three works the same way.",
        KingsTeacher.WHAT_IF to "Suppose a king on a square and follow the last squares it forces. If that leaves a row, " +
            "column or colour with no room at all, the square can't hold a king: cross it out.",
    ),
    // Kings.fitsTier: Standard reaches up to "would empty", Hard needs two colours confined, Expert a what-if (10 of 30).
    tierFor = tierByIndex({ KingsTraining.techniques }, hard = 3, expert = 4),
    // KingsTeacher.deduce has no parameter through which the answer could reach it.
    drill = MoveDrill(
        view = { (it as KingsState).marks },
        next = { s -> (s as KingsState).let { k -> KingsTeacher.deduce(k.size, k.region, k.marks)?.let { Kings.deduction(k, it) } } },
        every = { s, t -> (s as KingsState).let { k -> KingsTeacher.steps(k.size, k.region, k.marks, t).map { Kings.deduction(k, it) } } },
    ),
    badge = "K",
    // The first practice seed whose Expert board's walk needs all five. Pinned by KingsTrainingTest.
    examIndex = 1,
)
