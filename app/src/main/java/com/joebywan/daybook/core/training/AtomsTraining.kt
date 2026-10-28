package com.joebywan.daybook.core

import com.joebywan.daybook.puzzles.Atoms
import com.joebywan.daybook.puzzles.AtomsState
import com.joebywan.daybook.puzzles.AtomsTeacher

/**
 * Atoms' course on the move drill: the player lays the bond (or bonds) the technique forces. A bond past what the answer
 * has is the teacher's mistake and ends the run; one bond of a step that lays two plays on.
 *
 * "A group's only way out" is left out: no walk ever reaches it (0 of 30 boards on any tier; the simpler rungs always
 * settle those lines first), so there is no board to practise it on.
 */
val AtomsTraining: Course = Course(
    puzzle = Atoms,
    explanations = linkedMapOf(
        AtomsTeacher.ONE_NEIGHBOUR to "An atom's number is how many bonds it takes. When it has only one neighbour it " +
            "can still bond with, every bond it still needs goes there. Tap between two atoms for a bond, again for a double.",
        AtomsTeacher.ALL_FORCED to "A line holds two bonds at most, and a neighbour takes no more than it still needs. " +
            "When an atom needs exactly as many bonds as its neighbours have room for, it uses all of them.",
        AtomsTeacher.AT_LEAST_ONE to "When an atom can leave only a little room unused, a neighbour with more room than " +
            "that must take the difference. A 3 with two neighbours gets at least one bond each way.",
        AtomsTeacher.CROSSING to "Bonds can't cross. Once a bond is drawn, the line across it is shut, and that can " +
            "leave an atom few enough ways for one of the rules before.",
        AtomsTeacher.ISOLATION to "Every atom joins one molecule. A bond that would finish two atoms and cut them off " +
            "from the rest (a 1 to a 1, a double between two 2s) can't be, and that can force a bond elsewhere.",
        AtomsTeacher.WHAT_IF to "Suppose a line had no bond and follow what that forces, a step or two. If an atom then " +
            "can't reach its number, or a group is cut off, the line must have a bond.",
    ),
    // Atoms.specFor: Standard needs up to "at least one" (7 of 60 boards reach it), Hard crossings and isolation, Expert
    // the what-if (15 of 60).
    tierFor = tierByIndex({ AtomsTraining.techniques }, hard = 3, expert = 5),
    // AtomsTeacher.deduce has no parameter through which the answer could reach it.
    drill = MoveDrill { s -> (s as AtomsState).let { a -> AtomsTeacher.deduce(a.atoms, a.pairs, a.counts)?.let(Atoms::deduction) } },
    badge = "=",
    examIndex = 1,
)
