package com.joebywan.daybook.core

import com.joebywan.daybook.puzzles.Pipes
import com.joebywan.daybook.puzzles.PipesState
import com.joebywan.daybook.puzzles.PipesTeacher
import com.joebywan.daybook.puzzles.PuzzleState

/**
 * Pipes' course on the move drill: the player turns the tile the technique settles until it sits its one way round.
 *
 * Judged its own way, because a tap turns a tile a quarter at a time: turning the step's tile round to its way passes
 * through turns that are wrong, and those are the player getting there, not a mistake. So the step's tile is never
 * judged wrong (it is right once it reads its turn); any other tile the player turns is wrong when it runs full and no
 * finished network has it that way, the teacher's own mistake ("no legal answer keeps this rotation", never "differs from
 * the stored answer"). A turn elsewhere that some network keeps, or a dry one, plays on.
 */
private object PipesDrill : Drill {
    // PipesTeacher.deduce reads only the tiles as they lie: no answer goes in.
    private val moves = MoveDrill { s -> (s as PipesState).let { p -> PipesTeacher.deduce(p.width, p.height, p.cells)?.let(Pipes::deduction) } }

    override fun walk(start: PuzzleState) = moves.walk(start)

    override fun judge(puzzle: PuzzleType, p: Course.Practice, after: PuzzleState): Boolean? {
        val step = p.step as Deduction
        val was = p.board as PipesState
        val now = after as PipesState
        val target = step.targets.single()
        val wet = Pipes.filled(now)
        val wrong = now.cells.indices.any { c ->
            c != target && now.cells[c] != was.cells[c] && c in wet &&
                PipesTeacher.solve(now.width, now.height, now.cells, c to now.cells[c]) == null
        }
        return when {
            wrong -> false
            step.isReached(now) -> true
            else -> null
        }
    }
}

val PipesTraining: Course = Course(
    puzzle = Pipes,
    explanations = linkedMapOf(
        PipesTeacher.BORDER to "No pipe can point off the board. Against the edge a tile often fits one way only: a " +
            "straight runs along it, a T turns its flat side to it, a bend in a corner has one way. Tap the tile to turn it.",
        PipesTeacher.SET_NEIGHBOUR to "An opening must meet an opening. A set neighbour settles the side the two tiles " +
            "share: open if it points in, shut if it doesn't. When the sides that settles leave one way round, turn the tile to it.",
        PipesTeacher.WHICHEVER_WAY to "A neighbour that isn't set yet can still settle a side: when every way it could " +
            "turn points into this tile, or none does, the shared side is settled whichever way it ends up.",
        PipesTeacher.NO_LOOP to "The pipes make no loops. When two tiles are already joined the long way round, they " +
            "can't also join directly, so the side between them is shut. That can leave a tile only one way round.",
    ),
    // The generator gates no tier by technique, only by size: Standard's small boards reach all four (no loop on 8 of 60).
    tierFor = { Difficulty.STANDARD },
    drill = PipesDrill,
    badge = "+",
    examIndex = 1,
)
