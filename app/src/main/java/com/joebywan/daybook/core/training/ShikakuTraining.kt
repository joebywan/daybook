package com.joebywan.daybook.core

import com.joebywan.daybook.puzzles.Shikaku
import com.joebywan.daybook.puzzles.ShikakuState
import com.joebywan.daybook.puzzles.ShikakuTeacher

/**
 * Shikaku's course, on [MoveDrill]: every step is one rectangle to draw. The player draws it (drag corner to corner); a
 * rectangle the teacher calls a mistake ends the run, and a correct rectangle elsewhere plays on.
 */
val ShikakuTraining: Course = Course(
    puzzle = Shikaku,
    explanations = linkedMapOf(
        ShikakuTeacher.ONLY_FITS to "A number is its rectangle's area. When the edges, the other numbers and the " +
            "rectangles already drawn leave it just one way to lay that many squares out, draw it: drag from corner to corner.",
        ShikakuTeacher.ONLY_REACHES to "Every square belongs to some rectangle. When only one number's rectangles can " +
            "reach an empty square, and just one of them covers it, that rectangle is the one.",
        ShikakuTeacher.COMMON_CELLS to "When every rectangle a number can make covers the same squares, no other number " +
            "can use them. Set them aside and see which number that leaves just one rectangle.",
        ShikakuTeacher.WHAT_IF to "Suppose a number took one of its other rectangles. If that leaves another number no " +
            "room, or a square no number can reach, it can't go that way. Draw the one way left.",
    ),
    // Standard boards reach the first two; Hard is the easiest tier that reaches the other two.
    tierFor = tierByIndex({ ShikakuTraining.techniques }, hard = 2, expert = 4),
    // ShikakuTeacher.deduce has no parameter through which the answer could reach it.
    drill = MoveDrill { s ->
        (s as ShikakuState).let { k -> ShikakuTeacher.deduce(k.width, k.height, k.clues, k.blocks)?.let { Shikaku.deduction(it) } }
    },
    badge = "2x3",
    // The first practice seed whose Expert board's walk needs all four. Pinned by ShikakuTrainingTest.
    examIndex = 15,
    generate = { seed, tier -> Shikaku.generateVerified(seed, tier) },
)
