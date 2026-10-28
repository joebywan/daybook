package com.joebywan.daybook.core

import com.joebywan.daybook.puzzles.DominoLogic
import com.joebywan.daybook.puzzles.DominoState
import com.joebywan.daybook.puzzles.DominoTeacher
import com.joebywan.daybook.puzzles.Dominoes

/**
 * Dominoes' course, on [MoveDrill]: every step lays one domino from the tray. The player lays it (either way round for a
 * double); a domino the teacher calls a mistake ends the run, and a correct domino elsewhere plays on.
 */
val DominoesTraining: Course = Course(
    puzzle = Dominoes,
    explanations = linkedMapOf(
        DominoTeacher.ONLY_PARTNER to "A free square with one free neighbour must share a domino with it. When only " +
            "one domino in the tray can cover the two and keep the regions' rules, lay it there.",
        DominoTeacher.ONLY_PLACE to "Pick a domino in the tray and look at everywhere it could lie. When the regions' " +
            "rules rule out all but one place, it goes there.",
        DominoTeacher.ONLY_DOMINO to "Pick a free square and ask which dominoes could cover it. When the rules of the " +
            "regions around it rule out all but one, lay that one.",
        DominoTeacher.REGION_VALUE to "A region's empty squares can only show values the tray can still put there. " +
            "When those can't keep its rule with a domino lying somewhere, it can't lie there. See what that leaves.",
        DominoTeacher.COUNTING to "Count a value's halves left in the tray. When exactly that many squares can take " +
            "nothing else, no other square gets one, and that settles a domino.",
        DominoTeacher.WHAT_IF to "Try each domino that could cover a square and follow it a few steps. When all but " +
            "one break a rule, the one left goes there.",
    ),
    // Each tier needs exactly one level (Dominoes.neededTechnique): Standard region values, Hard counting, Expert the what-if.
    tierFor = tierByIndex({ DominoesTraining.techniques }, hard = 4, expert = 5),
    // DominoTeacher.deduce reads the layout (everything but the answer) and the placements.
    drill = MoveDrill { s -> (s as DominoState).let { d -> DominoTeacher.deduce(d.layout, d.placed)?.let { Dominoes.deduction(it) } } },
    badge = "6|6",
    // The first practice seed whose Expert board's walk needs all six. Pinned by DominoesTrainingTest.
    examIndex = 2,
    generate = { seed, tier -> DominoLogic.generateVerified(seed, tier) },
)
