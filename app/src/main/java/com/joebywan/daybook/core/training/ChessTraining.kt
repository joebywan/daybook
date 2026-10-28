package com.joebywan.daybook.core

import com.joebywan.daybook.puzzles.Chess
import com.joebywan.daybook.puzzles.ChessState
import com.joebywan.daybook.puzzles.ChessTeacher

/**
 * Chess's course, on [MoveDrill]: a step is a key move. Right is any key the prover names (a mate puzzle may have
 * several); wrong is a move after which the defence escapes, which the prover proves ("no legal answer keeps this"),
 * never "differs from a stored line". Its walkthrough teaches the rules; this teaches finding the key.
 */
val ChessTraining: Course = Course(
    puzzle = Chess,
    explanations = linkedMapOf(
        ChessTeacher.MATE_IN_ONE to "One move ends it. Look at the king's squares: a check it can't escape by moving, " +
            "by capturing the checker or by blocking is mate.",
        ChessTeacher.FORCING_KEY to "With moves to spare, look at checks first: a check leaves the defence few replies. " +
            "Find the one after which every reply still allows mate.",
        ChessTeacher.QUIET_KEY to "Sometimes no check works and the key is a quiet move: it takes the king's last " +
            "squares or sets up a threat the defence can't meet. Every reply must still allow mate.",
    ),
    // Standard (mate in two) reaches the first two on every board. A quiet key: 3 of 60 Hard boards, 18 of 60 Expert;
    // Expert finds a board in a mean of 54 ms against Hard's 20 seeds a board, so Expert.
    tierFor = tierByIndex({ ChessTraining.techniques }, hard = 2, expert = 2),
    // The position the player faces and the moves left: ChessState holds no answer, and the prover is the reasoning.
    drill = MoveDrill { s ->
        val c = s as ChessState
        ChessTeacher.replay(c)?.takeIf { !it.mated && it.mistakeIndex < 0 }
            ?.let { ChessTeacher.deduce(it.position, it.remaining) }?.let { ChessTeacher.deduction(c, it) }
    },
    badge = "#",
    // The first practice seed whose Expert walk needs all three (13 of the first 60). Pinned by ChessTrainingTest.
    examIndex = 6,
)
