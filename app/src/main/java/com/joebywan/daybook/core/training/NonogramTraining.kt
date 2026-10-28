package com.joebywan.daybook.core

import com.joebywan.daybook.puzzles.Nonogram
import com.joebywan.daybook.puzzles.NonogramLogic
import com.joebywan.daybook.puzzles.NonogramState
import com.joebywan.daybook.puzzles.NonogramTeacher

/**
 * Nonogram's course, on [MoveDrill]: the player fills and crosses the step's squares (one at a time or in a sweep), and
 * a mark no picture fitting the clues keeps ends the run, a wrong cross included, as the hints flag it.
 */
val NonogramTraining: Course = Course(
    puzzle = Nonogram,
    explanations = linkedMapOf(
        NonogramTeacher.FULL to "When a line's clues and the gaps between them take its whole width, there is nowhere " +
            "to slide: fill every run and cross the gaps.",
        NonogramTeacher.FINISHED to "When a line already shows every run its clue asks for, none of its other squares " +
            "can be filled: cross them out.",
        NonogramTeacher.OVERLAP to "Push a line's clues as far one way as they go, then as far the other. Squares a run " +
            "covers both times are filled, wherever it ends up.",
        NonogramTeacher.LINE to "With some squares of a line already marked, every way its clues can still fit may " +
            "agree on a square. Fill it or cross it, whichever they all say.",
    ),
    // Standard boards reach all four (TRAINING_SPEC.md, Measured).
    tierFor = { Difficulty.STANDARD },
    // NonogramTeacher.deduce reads the clues and the marks only; the clues are printed on the board.
    // Any line's instance of the technique is right, not only the hint's pick.
    drill = MoveDrill(
        next = { s ->
            (s as NonogramState).let { g ->
                NonogramTeacher.deduce(g.width, g.height, NonogramLogic.rowClues(g), NonogramLogic.colClues(g), g.cells)
                    ?.let { Nonogram.deduction(g, it) }
            }
        },
        every = { s, technique ->
            (s as NonogramState).let { g ->
                NonogramTeacher.steps(g.width, g.height, NonogramLogic.rowClues(g), NonogramLogic.colClues(g), g.cells)
                    .filter { it.technique == technique }.map { Nonogram.deduction(g, it) }
            }
        },
    ),
    // Not "#": a glyph starting with it names a drawn shape (AchievementBadge).
    badge = "N",
    // The first practice seed whose Expert board's walk needs all four. Pinned by NonogramTrainingTest.
    examIndex = 0,
)
