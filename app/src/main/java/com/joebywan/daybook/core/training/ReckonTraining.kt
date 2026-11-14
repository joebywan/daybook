package com.joebywan.daybook.core

import com.joebywan.daybook.puzzles.Reckon
import com.joebywan.daybook.puzzles.ReckonState
import com.joebywan.daybook.puzzles.ReckonTeacher

/** Reckon's course: the line singles, the cages' ways, a cage in a line, a pair and a what-if, on real positions. */
val ReckonTraining: Course = Course(
    puzzle = Reckon,
    explanations = linkedMapOf(
        ReckonTeacher.LAST to "When a row or column has one empty square left, the digit it's missing goes there.",
        ReckonTeacher.ONLY_DIGIT to "Look at one square. When its row and column between them hold every digit but " +
            "one, that one is all it can be.",
        ReckonTeacher.ONLY_PLACE to "Pick a line and a digit it still needs. When every other empty square in it " +
            "already has that digit in its own row or column, the one square left takes it.",
        ReckonTeacher.CAGE_DIGIT to "List the ways a cage can make its number with the digits its squares can still " +
            "take. 3÷ in two squares is 1 and 3 or 2 and 6; 15× is only 3 and 5. When the ways leave a square one digit, " +
            "that's it.",
        ReckonTeacher.CAGE_PLACE to "A digit's only place in a line, with the cages counted: a square whose cage can't " +
            "hold the digit is no place for it.",
        ReckonTeacher.CAGE_LINE to "When every way of a cage puts some digit in the squares it has in one line, that " +
            "line's other squares can't have it. Strike it off and look for the single it leaves.",
        ReckonTeacher.NAKED_PAIR to "Two squares in a line that can each be only the same two digits take both of them, " +
            "so no other square in that line can hold either. Strike them off and look for the single it leaves.",
        ReckonTeacher.WHAT_IF to "Take a square left two digits and suppose one. Follow it a few steps: if a square, a " +
            "line or a cage is left with nothing, the supposition was wrong and the square is the other digit.",
    ),
    // ReckonLogic.specFor: Standard singles and the cages' ways, Hard must need a cage in a line or a pair, Expert a what-if.
    tierFor = tierByIndex({ ReckonTraining.techniques }, hard = 5, expert = 7),
    stepper = { s -> val q = s as ReckonState; { cells -> ReckonTeacher.deduce(q.size, cells, q.cageOf, q.ops, q.targets) } },
    cellsOf = { (it as ReckonState).cells },
    solutionOf = { (it as ReckonState).solution },
    withGivens = { s, cells -> (s as ReckonState).copy(cells = cells, locked = cells.indices.filter { cells[it] != 0 }) },
    badge = "×",
    // The first practice seed whose Expert board needs all eight. Pinned by ReckonTutorialTest.
    examIndex = 10,
)
