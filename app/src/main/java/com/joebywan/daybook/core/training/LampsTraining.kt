package com.joebywan.daybook.core

import com.joebywan.daybook.puzzles.Lamps
import com.joebywan.daybook.puzzles.LampsLogic
import com.joebywan.daybook.puzzles.LampsState
import com.joebywan.daybook.puzzles.LampsTeacher

/**
 * Lamps' course: the player places the step's lamp or crosses out its squares. A lamp no answer keeps is the teacher's
 * mistake. The teacher reads crosses as notes, so in a game a wrong cross is never flagged; here a cross on a square
 * where the answer has a lamp is wrong (the board has one answer, so that is "no legal answer keeps this").
 */
val LampsTraining: Course = Course(
    puzzle = Lamps,
    explanations = linkedMapOf(
        LampsTeacher.ZERO to "A 0 has no lamps beside it, so none of the squares next to it can hold one: tap them to " +
            "cross them out.",
        LampsTeacher.DONE to "When a number already has all its lamps beside it, the other squares next to it hold " +
            "none: cross them out.",
        LampsTeacher.ROOM to "When a number still needs as many lamps as it has squares beside it that could take one " +
            "(not walls, lamps, lit or crossed), they are all lamps. Double-tap or long-press for a lamp.",
        LampsTeacher.ONLY_LIGHT to "Every white square must be lit. When only one square left can light a dark square, " +
            "whether itself or one in its row or column, that square holds a lamp.",
        LampsTeacher.STARVE to "A lamp lights its whole row and column. When a lamp on a square would light so many " +
            "squares beside a number that the number couldn't get its lamps, no lamp goes there: cross it out.",
        LampsTeacher.DARK to "When a lamp on a square would light every square that could light some dark square, " +
            "without lighting that square itself, nothing could ever light it. So no lamp goes there: cross it out.",
        LampsTeacher.WHAT_IF to "Suppose a lamp on a square, or none, and follow the numbers and squares it forces. If " +
            "a number gets too many lamps or too few, or a square could never be lit, the other choice is the answer.",
    ),
    // Standard reaches the four direct facts, Hard the starved number and the dark square (51 and 22 of 60), Expert the what-if.
    tierFor = tierByIndex({ LampsTraining.techniques }, hard = 4, expert = 6),
    // LampsTeacher.deduce is handed the walls, numbers and marks: never the answer.
    drill = MoveDrill(
        next = { s -> (s as LampsState).let { l -> LampsTeacher.deduce(l.size, l.walls, l.marks)?.let(LampsTeacher::deduction) } },
        every = { s, t -> (s as LampsState).let { l -> LampsTeacher.steps(l.size, l.walls, l.marks, t).map(LampsTeacher::deduction) } },
        wrong = { s -> (s as LampsState).marks.indices.any { s.marks[it] == LampsLogic.CROSS && s.solution[it] == '1' } },
    ),
    badge = "L",
    // The first practice seed whose Expert board's walk needs all seven (31 of the first 60). Pinned by LampsTrainingTest.
    examIndex = 4,
)
