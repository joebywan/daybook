package com.joebywan.daybook.core

import com.joebywan.daybook.puzzles.Thermometers
import com.joebywan.daybook.puzzles.ThermometersLogic
import com.joebywan.daybook.puzzles.ThermometersState
import com.joebywan.daybook.puzzles.ThermometersTeacher

/**
 * Thermometers' course, on [MoveDrill]: the player fills or crosses the step's squares (a tap fills a thermometer from its
 * bulb, or crosses it to its tip, in one move). A mark the board's one answer disagrees with ends the run, a wrong cross
 * included, as the hints flag it, so no `wrong` of its own is needed.
 */
val ThermometersTraining: Course = Course(
    puzzle = Thermometers,
    explanations = linkedMapOf(
        ThermometersTeacher.TUBE to "A thermometer fills from its round bulb with no gaps. Under a filled square everything " +
            "fills; above an empty square nothing can.",
        ThermometersTeacher.DONE to "When a row or column already has as many filled squares as its number, every other " +
            "square in it stays empty: cross them out.",
        ThermometersTeacher.ROOM to "When a row or column has exactly as many open squares as it still needs, all of them fill.",
        ThermometersTeacher.AT_MOST to "A thermometer fills a line bulb side first. If the line needs only k more, one " +
            "thermometer can give it at most k: its next open square there, and everything above, stay empty.",
        ThermometersTeacher.AT_LEAST to "Count the open squares the other thermometers have in a line. If they fall short of " +
            "what it needs, this one must make up the rest: it fills from its bulb that far.",
        ThermometersTeacher.WHAT_IF to "Suppose a square filled (or empty) and follow the lines it settles. If some line can " +
            "no longer hold its count, the square is the other way.",
    ),
    // ThermometersLogic.specFor: Standard needs lines and tips, every Hard board a bulb that must fill, every Expert a what-if.
    tierFor = tierByIndex({ ThermometersTraining.techniques }, hard = 4, expert = 5),
    // ThermometersTeacher.deduce reads the tubes, the counts and the marks only; the counts are printed on the board.
    // Any thermometer's or line's instance of the technique (or any what-if within the hint's reach) is right.
    drill = MoveDrill(
        view = { (it as ThermometersState).marks.toList() },
        next = { s ->
            (s as ThermometersState).let { t ->
                ThermometersTeacher.deduce(t.size, t.tubes, ThermometersLogic.targets(t), t.marks)?.let { Thermometers.deduction(t, it) }
            }
        },
        every = { s, technique ->
            (s as ThermometersState).let { t ->
                ThermometersTeacher.steps(t.size, t.tubes, ThermometersLogic.targets(t), t.marks, technique).map { Thermometers.deduction(t, it) }
            }
        },
    ),
    badge = "Th",
    // The first practice seed whose Expert board's walk needs all six. Pinned by ThermometersTrainingTest.
    examIndex = 0,
)
