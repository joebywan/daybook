package com.joebywan.daybook.core

import com.joebywan.daybook.puzzles.Lits
import com.joebywan.daybook.puzzles.LitsState
import com.joebywan.daybook.puzzles.LitsTeacher

/**
 * LITS' course, on [MoveDrill]: every step shades squares. The player shades them, or the squares of any other step of
 * the same technique (tap by tap or in a drag); a square
 * the teacher calls a mistake ends the run, which for LITS means no answer under the win check's rules keeps it (no
 * connectivity rule, PR #15). Boards come from [Lits.generateVerified] only, never [Lits.generate]'s unproved floor.
 */
val LitsTraining: Course = Course(
    puzzle = Lits,
    explanations = linkedMapOf(
        LitsTeacher.WHOLE_REGION to "Every region holds one L, I, T or S of four squares. A region of exactly four " +
            "squares is its own tetromino: shade all of it.",
        LitsTeacher.OVERLAP to "Picture every L, I, T and S that fits in a region, around anything shaded there " +
            "already. Squares every one of them covers are shaded whichever it is.",
        LitsTeacher.AVOID_BLOCK to "No 2x2 block may be all shaded. Set aside a region's shapes that would finish one " +
            "with the shading beside them, and shade what every shape left covers.",
        LitsTeacher.LETTER_CLASH to "Two tetrominoes of one letter may not touch edge to edge. Set aside a region's " +
            "shapes that would touch a finished one of their own letter, and shade what every shape left covers.",
        LitsTeacher.NEIGHBOUR to "Set aside a region's shapes that would leave a region beside it no shape at all, " +
            "and shade what every shape left covers.",
        LitsTeacher.WHAT_IF to "Suppose a square stays empty and follow the shapes that forces. If some region is " +
            "left with no shape at all, the square must be shaded.",
    ),
    // Standard reaches the first four (the whole region on 10 of 60 boards), Hard the neighbour (28 of 60). The what-if
    // is drawn from Expert: Hard reaches it on 3 boards in 60, Expert on 25.
    tierFor = tierByIndex({ LitsTraining.techniques }, hard = 4, expert = 5),
    // LitsTeacher.deduce has no parameter through which the answer could reach it.
    drill = MoveDrill(
        view = { (it as LitsState).shaded },
        next = { s -> (s as LitsState).let { l -> LitsTeacher.deduce(l.width, l.height, l.region, l.shaded)?.let { Lits.deduction(it) } } },
        every = { s, t -> (s as LitsState).let { l -> LitsTeacher.instances(l.width, l.height, l.region, l.shaded, t).map { Lits.deduction(it) } } },
    ),
    badge = "L",
    // The first practice seed whose Expert board's walk needs all six. Pinned by LitsTrainingTest.
    examIndex = 5,
    generate = { seed, tier -> Lits.generateVerified(seed, tier) },
)
