package com.joebywan.daybook.core

import com.joebywan.daybook.puzzles.WhosWho
import com.joebywan.daybook.puzzles.WhosWhoState
import com.joebywan.daybook.puzzles.WhosWhoTeacher

/**
 * Who's Who's course, on [MoveDrill]: the teacher ticks and crosses the table. Every step is logic over the clues and
 * the marks (same, not, next to, left of, either); the words are only labels, so no board needs knowing anything.
 */
val WhosWhoTraining: Course = Course(
    puzzle = WhosWho,
    explanations = linkedMapOf(
        WhosWhoTeacher.CLUE to "Some clues settle a square on their own: \"Ann has the cat\" is a tick, \"Ben isn't " +
            "the pilot\" a cross. Make the mark the clue gives.",
        WhosWhoTeacher.ONLY to "When everything else in a column is crossed off for someone, the one item left is " +
            "theirs: tick it. It works down the table too: when everyone but one person is ruled out for an item, it's theirs.",
        WhosWhoTeacher.LINK to "A clue that ties two things together carries what you know across. If the baker drinks " +
            "tea and Ann is the baker, Ann drinks tea; if tea is ruled out for Ben, he isn't the baker either.",
        WhosWhoTeacher.HOUSE to "Houses are numbered from the left. \"Next to\" and \"left of\" clues limit where " +
            "someone can live: whoever lives left of someone can't be in the last house. Cross off the houses the clue rules out.",
        WhosWhoTeacher.WHAT_IF to "Suppose a tick and follow the clues a few steps. If someone is left with nothing, or " +
            "a clue can't hold, the tick was wrong: cross it off.",
    ),
    // The generator's tiers: Standard takes clues and lone items, Hard links and houses, Expert a what-if.
    tierFor = tierByIndex({ WhosWhoTraining.techniques }, hard = 2, expert = 4),
    // WhosWhoTeacher.deduce reads the clues, the marks and the words; never the answer.
    drill = MoveDrill { s -> (s as WhosWhoState).let { w -> WhosWhoTeacher.deduce(w, w.marks)?.let { WhosWho.deduction(it) } } },
    badge = "?",
    // Every Expert board's walk needs all five (60 of the first 60); the first. Pinned by WhosWhoTrainingTest.
    examIndex = 0,
)
