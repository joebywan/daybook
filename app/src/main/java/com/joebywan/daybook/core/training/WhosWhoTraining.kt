package com.joebywan.daybook.core

import com.joebywan.daybook.puzzles.WhosWho
import com.joebywan.daybook.puzzles.WhosWhoGraphLogic
import com.joebywan.daybook.puzzles.WhosWhoGraphState
import com.joebywan.daybook.puzzles.WhosWhoTeacher

/**
 * Who's Who's course, on [MoveDrill]: the teacher draws lines and rules pairs out on the graph. Every step is logic over
 * the clues and the marks (same, not, next to, left of, either); the words are only labels, so no board needs knowing
 * anything. "The one item left" is not drilled: the graph links a last choice by itself, so no hint ever gives it.
 */
val WhosWhoTraining: Course = Course(
    puzzle = WhosWho,
    explanations = linkedMapOf(
        WhosWhoTeacher.CLUE to "Some clues settle a pair on their own: \"Ann has the cat\" is a line from Ann to the cat, " +
            "\"Ben isn't the pilot\" rules that pair out. Make the mark the clue gives.",
        WhosWhoTeacher.LINK to "A clue that ties two things together carries what you know across. If the baker drinks " +
            "tea and Ann is the baker, Ann drinks tea; if tea is ruled out for Ben, he isn't the baker either.",
        WhosWhoTeacher.HOUSE to "Houses are numbered from the left. \"Next to\" and \"left of\" clues limit where " +
            "someone can live: whoever lives left of someone can't be in the last house. Rule out the houses the clue rules out.",
        WhosWhoTeacher.WHAT_IF to "Suppose a link and follow the clues a few steps. If someone is left with nothing, or " +
            "a clue can't hold, the link was wrong: rule that pair out.",
    ),
    // The generator's tiers: Standard takes clues, Hard links and houses, Expert a what-if.
    tierFor = tierByIndex({ WhosWhoTraining.techniques }, hard = 1, expert = 3),
    // WhosWhoTeacher reads the table the marks imply, the clues and the words; never the answer.
    drill = MoveDrill(
        // Ruling a pair out takes two taps, the first drawing a line: a line that leaves the right item out is a
        // mistake for a moment, so the verdict waits as Mambo's does.
        settleMillis = 1000L,
        view = { s -> (s as WhosWhoGraphState).let { g -> WhosWhoGraphLogic.close(g).let { c -> List(g.size) { a -> List(g.size) { c.at(a, it) } } } } },
        next = { s ->
            (s as WhosWhoGraphState).let { g ->
                val t = WhosWhoGraphLogic.table(g)
                WhosWhoTeacher.deduce(t, t.marks)?.let { WhosWho.deduction(g, it) }
            }
        },
        every = { s, technique ->
            (s as WhosWhoGraphState).let { g ->
                val t = WhosWhoGraphLogic.table(g)
                WhosWhoTeacher.steps(t, t.marks, technique).map { WhosWho.deduction(g, it) }
            }
        },
    ),
    badge = "?",
    // Every Expert board's walk needs all four; the first. Pinned by WhosWhoTrainingTest.
    examIndex = 0,
)
