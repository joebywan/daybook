package com.joebywan.daybook.core

import com.joebywan.daybook.puzzles.Links
import com.joebywan.daybook.puzzles.LinksLogic
import com.joebywan.daybook.puzzles.LinksState
import com.joebywan.daybook.puzzles.LinksTeacher

/**
 * Links' course on the move drill: the player draws the lines the step forces. A line the board's one answer lacks is the
 * teacher's mistake and ends the run (sound: every board has one answer). Any instance of the technique on the position
 * passes, the hint's own or another (`LinksTeacher.steps`).
 */
val LinksTraining: Course = Course(
    puzzle = Links,
    explanations = linkedMapOf(
        LinksTeacher.ONE_WAY to "A line can't stop short of its pair. When the end of a line (or a number with no line " +
            "yet) has only one square it can step to, the line goes there. Drag from the end to draw it.",
        LinksTeacher.TWO_WAYS to "Every square is on a path, and a path through a square uses two of its sides. When " +
            "only two neighbours could join an empty square, it joins both: drag through it from one to the other.",
        LinksTeacher.CUT to "Look at each way an end or a square could go. If taking one would cut a pair off from its " +
            "other end, or leave a square nothing can fill, it can't go that way. Draw the way that is left.",
        LinksTeacher.WHAT_IF to "An end with two ways: suppose one, and follow the moves it forces a few steps. If " +
            "something breaks (a pair cut off, a square boxed in), the end takes the other way.",
    ),
    // LinksLogic.specFor: Standard the local rules alone, Hard a cut, Expert a cut or a what-if.
    tierFor = tierByIndex({ LinksTraining.techniques }, hard = 2, expert = 3),
    // LinksTeacher.deduce is handed the ends and the lines; the answer never reaches it.
    drill = MoveDrill(
        view = { (it as LinksState).links.toList() },
        next = { s -> (s as LinksState).let { l -> LinksTeacher.deduce(l.size, l.ends, l.links)?.let(Links::deduction) } },
        every = { s, t -> (s as LinksState).let { l -> LinksTeacher.steps(l.size, l.ends, l.links, t).map(Links::deduction) } },
    ),
    badge = "1-1",
    examIndex = EXAM_INDEX,
    generate = LinksLogic::generateVerified,
)

/** The first practice seed whose Expert board's walk needs every technique. Pinned by `LinksTrainingTest`. */
private const val EXAM_INDEX = 0
